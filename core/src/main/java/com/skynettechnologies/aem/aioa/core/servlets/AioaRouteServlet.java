package com.skynettechnologies.aem.aioa.core.servlets;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skynettechnologies.aem.aioa.core.services.SiteDomainService;
import com.skynettechnologies.aem.aioa.core.services.SkynetWidgetService;
import com.skynettechnologies.aem.aioa.core.util.AdminAccess;
import com.skynettechnologies.aem.aioa.core.util.AioaValues;

/**
 * The AJAX router of the settings page (POST /route?action=<name> in server.js).
 *
 *   POST /apps/aioa/content/settings.route.json?action=<name>
 *
 * Bound to the settings page's resource type, so it answers only below that page. POST only:
 * AEM's CSRF filter checks the CSRF-Token header the page sends, and the user must be a member
 * of an allowed group (OSGi "Allowed groups"). Every answer is JSON, like the Node.js route.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "aioa/components/settings-page",
        selectors = "route",
        extensions = "json",
        methods = {"GET", "POST"})
public class AioaRouteServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Reference
    private transient SkynetWidgetService service;

    @Reference
    private transient SiteDomainService siteDomains;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        sendJson(response, Collections.singletonMap("error", "Method Not Allowed."), 405);
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        if (!AdminAccess.isAllowed(request.getResourceResolver(), service.getConfiguration().allowedGroups())) {
            sendJson(response, Collections.singletonMap("error", "You are not allowed to change the accessibility settings."), 403);
            return;
        }

        Function<Map<String, String>, Map<String, Object>> action = action(AioaValues.lastValue(request.getParameterValues("action")));
        if (action == null) {
            sendJson(response, Collections.singletonMap("error", "Unknown action."), 200);
            return;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!"action".equals(name) && !"_charset_".equals(name)) {
                fields.put(name, AioaValues.lastValue(request.getParameterValues(name)));
            }
        }
        sendJson(response, action.apply(fields), 200);
    }

    private Function<Map<String, String>, Map<String, Object>> action(String name) {
        switch (name) {
            case "fetchWidgetSettings":
                return service::fetchWidgetSettings;
            case "registerDomain":
                return service::registerDomain;
            case "getDashboardLink":
                return service::getDashboardLink;
            case "fetchScanDetails":
                return service::getScanDetails;
            case "saveDomain":
                return this::saveDomain;
            case "removeDomain":
                return this::removeDomain;
            default:
                return null;
        }
    }

    /** Remove website: only websites saved in the console (not the OSGi "Site domains"); Skynet keeps the widget. */
    private Map<String, Object> removeDomain(Map<String, String> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        String domain = AioaValues.normalizeDomain(fields.get("url"));
        if (!AioaValues.isValidDomain(domain)) {
            result.put("error", "Enter a valid domain, for example www.example.com.");
        } else if (isConfigured(domain)) {
            // Same rule as the console, which hides "Remove website" for these
            result.put("error", domain + " comes from the OSGi configuration (Site domains / AIOA_SITE_DOMAIN) "
                    + "and can only be removed there.");
        } else if (!siteDomains.getSavedDomains().contains(domain)) {
            result.put("error", domain + " is not a website saved in this console.");
        } else if (!siteDomains.removeDomain(domain)) {
            result.put("error", "The website could not be removed. Please try again, or ask an AEM administrator to check the log.");
        } else {
            result.put("removed", domain);
        }
        return result;
    }

    /** True when the OSGi "Site domains" (AIOA_SITE_DOMAIN) list the domain. */
    private boolean isConfigured(String domain) {
        String[] configured = service.getConfiguration().siteDomains();
        if (configured != null) {
            for (String value : configured) {
                if (domain.equals(AioaValues.normalizeDomain(value))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "Your website" step / Add website: keep the domain in AEM, so the console opens it next time. */
    private Map<String, Object> saveDomain(Map<String, String> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        String domain = siteDomains.saveDomain(fields.get("url"));
        if (domain == null) {
            result.put("error", AioaValues.isValidDomain(AioaValues.normalizeDomain(fields.get("url")))
                    ? "The website could not be saved in AEM. Please try again, or ask an AEM administrator to check the log."
                    : "Enter a valid domain, for example www.example.com.");
        } else {
            result.put("domain", domain);
        }
        return result;
    }

    private static void sendJson(SlingHttpServletResponse response, Object data, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Sign-in links must never be cached by the dispatcher or the browser
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Dispatcher", "no-cache");
        response.getWriter().write(MAPPER.writeValueAsString(data));
    }
}
