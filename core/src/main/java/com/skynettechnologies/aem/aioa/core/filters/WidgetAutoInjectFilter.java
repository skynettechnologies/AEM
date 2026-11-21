package com.skynettechnologies.aem.aioa.core.filters;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.servlets.annotations.SlingServletFilter;
import org.apache.sling.servlets.annotations.SlingServletFilterScope;
import org.apache.sling.xss.XSSAPI;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.granite.ui.clientlibs.HtmlLibraryManager;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.WCMMode;
import com.skynettechnologies.aem.aioa.core.config.AioaConfiguration;
import com.skynettechnologies.aem.aioa.core.models.WidgetModel;
import com.skynettechnologies.aem.aioa.core.services.SkynetWidgetService;

/**
 * Adds the accessibility widget to site pages automatically, so installing the package is enough:
 * no change to page components, templates or content.
 *
 * For a GET of a page (.html) below the OSGi "Automatic: include paths" and not below the excluded paths,
 * the rendered HTML is buffered and the same markup as the widget component (aioa/components/widget) is
 * inserted before the closing body tag: on publish, and on author for pages opened directly, in preview and
 * "view as published". Inside the page editor the markup is present but the loader (widget-loader.js) does not
 * load the widget, so it never covers the authoring UI. Nothing is inserted:
 *  - in design mode;
 *  - when the page already contains the widget (component placed by an author or developer);
 *  - for error pages, redirects, non-HTML answers, or when the widget / automatic mode is switched off.
 *
 * Runs after AEM's WCM request filter (low ranking), so the WCM mode of the request is known.
 */
@Component(service = Filter.class, property = Constants.SERVICE_RANKING + ":Integer=-3000")
@SlingServletFilter(scope = SlingServletFilterScope.REQUEST, extensions = "html", methods = "GET")
public class WidgetAutoInjectFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(WidgetAutoInjectFilter.class);

    /** Attribute of the marker element; present when the widget is already on the page. */
    static final String MARKER = "data-aioa-widget";
    static final String CLIENTLIB_CATEGORY = "aioa.widget";
    private static final String BODY_END = "</body>";

    @Reference
    private SkynetWidgetService service;

    @Reference
    private HtmlLibraryManager htmlLibraryManager;

    @Reference
    private XSSAPI xssApi;

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to initialize
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof SlingHttpServletRequest) || !(servletResponse instanceof SlingHttpServletResponse)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        SlingHttpServletRequest request = (SlingHttpServletRequest) servletRequest;
        SlingHttpServletResponse response = (SlingHttpServletResponse) servletResponse;

        if (!appliesTo(request, service.getConfiguration())) {
            chain.doFilter(request, response);
            return;
        }

        BufferedHtmlResponse buffered = new BufferedHtmlResponse(response);
        chain.doFilter(request, buffered);
        buffered.writeTo(response, html -> withWidget(request, response, html));
    }

    @Override
    public void destroy() {
        // nothing to release
    }

    /** Cheap checks before rendering: switched on, a page, below an include path and not excluded. */
    static boolean appliesTo(SlingHttpServletRequest request, AioaConfiguration config) {
        if (config == null || !config.widgetEnabled() || !config.autoInjectEnabled()) {
            return false;
        }
        Resource resource = request.getResource();
        if (resource == null || resource.adaptTo(Page.class) == null) {
            return false;
        }
        String path = resource.getPath();
        return matchesAny(path, config.autoInjectPaths()) && !matchesAny(path, config.autoInjectExcludedPaths());
    }

    /** True when path is one of the given paths or below one of them. */
    static boolean matchesAny(String path, String[] roots) {
        if (roots == null) {
            return false;
        }
        for (String root : roots) {
            if (root == null) {
                continue;
            }
            String trimmed = root.trim();
            while (trimmed.length() > 1 && trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty() && (path.equals(trimmed) || path.startsWith(trimmed + "/") || "/".equals(trimmed))) {
                return true;
            }
        }
        return false;
    }

    /** The page HTML with the widget before the closing body tag, or null to leave it unchanged. */
    private String withWidget(SlingHttpServletRequest request, SlingHttpServletResponse response, String html) {
        // Also in edit mode: on author, opening a page directly (/content/...html) is edit mode too, and the widget
        // must show there. Inside the page editor the loader (widget-loader.js) does not load the widget.
        WCMMode mode = WCMMode.fromRequest(request);
        if (mode == WCMMode.DESIGN) {
            return null;
        }
        String contentType = response.getContentType();
        if (response.getStatus() != HttpServletResponse.SC_OK
                || contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("text/html")
                || html.contains(MARKER)) {
            return null;
        }
        int bodyEnd = html.toLowerCase(Locale.ROOT).lastIndexOf(BODY_END);
        if (bodyEnd < 0) {
            return null;
        }
        String snippet = snippet(request);
        return snippet == null ? null : html.substring(0, bodyEnd) + snippet + html.substring(bodyEnd);
    }

    /** Same markup as aioa/components/widget: marker element plus the async loader clientlib. */
    private String snippet(SlingHttpServletRequest request) {
        WidgetModel widget = request.adaptTo(WidgetModel.class);
        if (widget == null || !widget.isEnabled()) {
            return null;
        }
        try {
            StringWriter script = new StringWriter();
            htmlLibraryManager.writeJsInclude(request, script, CLIENTLIB_CATEGORY);
            String scriptTag = script.toString().trim().replaceFirst("<script\\b", "<script async");
            if (!scriptTag.contains("<script")) {
                return null;
            }
            return "\n<div " + MARKER + " data-aioa-src=\"" + xssApi.encodeForHTMLAttr(widget.getScriptUrl())
                    + "\" data-aioa-delay=\"" + widget.getDelay() + "\" hidden></div>\n" + scriptTag + "\n";
        } catch (IOException | RuntimeException e) {
            LOG.warn("All in One Accessibility: could not add the widget to {}: {}", request.getRequestURI(), e.toString());
            return null;
        }
    }
}
