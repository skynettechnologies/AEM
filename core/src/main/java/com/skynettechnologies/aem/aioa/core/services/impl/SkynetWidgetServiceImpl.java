package com.skynettechnologies.aem.aioa.core.services.impl;

import static com.skynettechnologies.aem.aioa.core.util.AioaValues.asList;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.asMap;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.base64;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.isEmpty;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.isObject;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.isValidDomain;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.isValidEmail;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.jsonNumber;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.normalizeDomain;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.phpRound;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.toFloat;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.toInt;
import static com.skynettechnologies.aem.aioa.core.util.AioaValues.toStr;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skynettechnologies.aem.aioa.core.config.AioaConfiguration;
import com.skynettechnologies.aem.aioa.core.services.SkynetWidgetService;

/**
 * AEM port of app/skynet-widget.js. Same rules, same answers:
 *
 *  - loading never registers: fetchWidgetSettings answers {"not_registered": true} for a new website;
 *  - registerDomain checks widget-settings again first, so a website is never registered twice,
 *    and nothing is registered when the API can't be reached;
 *  - only the dashboard link leaves the server (api_key and the rest of check-website stay here).
 *
 * Never log check-website responses: they hold the sign-in link and api_key.
 */
@Component(service = SkynetWidgetService.class, immediate = true)
@Designate(ocd = AioaConfiguration.class)
public class SkynetWidgetServiceImpl implements SkynetWidgetService {

    private static final Logger LOG = LoggerFactory.getLogger(SkynetWidgetServiceImpl.class);

    /** Widget position for mobile (Paid Features): missing from widget-settings, see withMobileFields(). */
    private static final List<String> MOBILE_FIELDS = Arrays.asList(
            "is_mobile_position",
            "is_widget_custom_position_mobile",
            "widget_position_mobile",
            "widget_position_top_mobile",
            "widget_position_bottom_mobile",
            "widget_position_left_mobile",
            "widget_position_right_mobile",
            "widget_position_vertical_center_offset_mobile",
            "widget_position_horizontal_center_offset_mobile");

    /** Score label by minimum score, checked from the top: [min, level, label]. */
    private static final Object[][] SCORE_LEVELS = {
            {85, "compliant", "Compliant"},
            {50, "semi", "Semi Compliant"},
            {0, "not", "Not Compliant"},
    };

    private static final Pattern HTTPS = Pattern.compile("^https://.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter START_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+0000'");
    private static final String INVALID_DOMAIN = "A valid domain is required, for example example.com or localhost.";

    private volatile AioaConfiguration config;
    private volatile SkynetHttpClient http;

    @Activate
    @Modified
    protected void activate(AioaConfiguration configuration) {
        this.config = configuration;
        this.http = new SkynetHttpClient(configuration.userAgent(), configuration.httpTimeout());
    }

    @Override
    public AioaConfiguration getConfiguration() {
        return config;
    }

    /* =======================================================================
     * Actions (called by AioaRouteServlet)
     * ===================================================================== */

    @Override
    public Map<String, Object> fetchWidgetSettings(Map<String, String> req) {
        String url = normalizeDomain(req.get("url"));
        if (!isValidDomain(url)) {
            return error(INVALID_DOMAIN);
        }

        Object data = getSettingsData(url);
        if (data == Boolean.FALSE) {
            return error("Unable to load widget settings. Please try again.");
        }
        if (data == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("not_registered", true);
            return result;
        }
        return asMap(data);
    }

    @Override
    public Map<String, Object> registerDomain(Map<String, String> req) {
        String url = normalizeDomain(req.get("url"));
        if (!isValidDomain(url)) {
            return error(INVALID_DOMAIN);
        }

        Object data = getSettingsData(url);

        // API not reachable: never register a website that may already be registered
        if (data == Boolean.FALSE) {
            return error("Unable to load widget settings. Please try again.");
        }

        if (data == null) {
            String noRequiredEu = "0".equals(req.get("no_required_eu")) ? "0" : "1";
            register(url, valueOf(req.get("username")), valueOf(req.get("email")), valueOf(req.get("company")), noRequiredEu);
            data = waitForSettings(url, 3);
        }

        // Registered, but the settings did not appear yet: the page waits instead of registering again
        if (data == null) {
            Map<String, Object> result = error("Your website was registered, but its settings are not ready yet. Please try again in a moment.");
            result.put("pending", true);
            return result;
        }
        return asMap(data);
    }

    @Override
    public Map<String, Object> getDashboardLink(Map<String, String> req) {
        String url = normalizeDomain(req.get("url"));
        if (!isValidDomain(url)) {
            return error(INVALID_DOMAIN);
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("domain", url);
        Map<String, Object> res = http.post(config.checkWebsiteApi(), fields);
        if (res == null) {
            return error("Unable to load the dashboard link right now.");
        }

        String link = toStr(res.get("manage_domain"));
        if (!HTTPS.matcher(link).matches()) {
            return error("No dashboard link was found for " + url + ".");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("link", link);
        return result;
    }

    @Override
    public Map<String, Object> getScanDetails(Map<String, String> req) {
        String url = normalizeDomain(req.get("url"));
        if (!isValidDomain(url)) {
            return error("Enter a valid domain, for example example.com or localhost");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("lang_code", config.scanLang());
        fields.put("website", base64(url));
        Map<String, Object> res = http.post(config.scanApi(), fields);
        if (res == null) {
            return error("Unable to load the accessibility score right now. Please try again.");
        }

        Object data = res.get("Data");
        boolean inProgress = toInt(res.get("status")) == 201;
        if (inProgress || isEmpty(data) || !(data instanceof Map || data instanceof List)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("no_scan", true);
            result.put("in_progress", inProgress);
            result.put("message", toStr(res.get("msg")));
            return result;
        }

        // Alt Text Remediated comes from check-website (alt_count)
        Map<String, String> siteFields = new LinkedHashMap<>();
        siteFields.put("domain", url);
        Map<String, Object> site = http.post(config.checkWebsiteApi(), siteFields);

        return scoreSummary(asMap(data), site != null ? site.get("alt_count") : null);
    }

    /* =======================================================================
     * Internal
     * ===================================================================== */

    /** add-user-domain: name = admin name or the website, email = admin email or no-reply@website */
    private void register(String url, String name, String email, String company, String noRequiredEu) {
        name = name.trim();
        email = email.trim();

        Map<String, String> postFields = new LinkedHashMap<>();
        postFields.put("name", !name.isEmpty() ? name : url);
        postFields.put("email", isValidEmail(email) ? email : "no-reply@" + url.replaceFirst("^www\\.", ""));
        // Company: from the console (?company=), otherwise the OSGi "Company name"
        String companyName = !company.trim().isEmpty() ? company.trim() : (config.companyName() == null ? "" : config.companyName().trim());
        postFields.put("company_name", companyName);
        postFields.put("website", base64(url));
        postFields.put("package_type", "free-widget");
        postFields.put("start_date", ZonedDateTime.now(ZoneOffset.UTC).format(START_DATE));
        postFields.put("end_date", "");
        postFields.put("price", "");
        postFields.put("discount_price", "0");
        postFields.put("platform", config.platform());
        postFields.put("api_key", "");
        postFields.put("is_trial_period", "");
        postFields.put("is_free_widget", "1");
        postFields.put("no_required_eu", noRequiredEu);
        postFields.put("bill_address", "");
        postFields.put("country", "");
        postFields.put("state", "");
        postFields.put("city", "");
        postFields.put("post_code", "");
        postFields.put("transaction_id", "");
        postFields.put("subscr_id", "");
        postFields.put("payment_source", "");

        LOG.info("All in One Accessibility: registering the free widget for {}", url);
        http.post(config.apiBase() + "add-user-domain", postFields);
    }

    /**
     * Settings of a registered domain (a Map), null when the domain is not registered,
     * or Boolean.FALSE when the API could not be reached or did not answer with JSON.
     */
    private Object getSettingsData(String url) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("website_url", url);
        Map<String, Object> res = http.post(config.apiBase() + "widget-settings", fields);
        if (res == null) {
            return Boolean.FALSE;
        }
        Object data = res.get("Data");
        if (isEmpty(data) || !isObject(data)) {
            return null;
        }
        return withMobileFields(url, new LinkedHashMap<>(asMap(data)));
    }

    /** Adds the widget position for mobile to a paid plan's settings (fields widget-settings sent win). */
    private Map<String, Object> withMobileFields(String url, Map<String, Object> data) {
        if (toInt(data.get("is_free_widget")) == 1) {
            return data;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("website_url", url);
        Map<String, Object> res = http.post(config.widgetSettingsApi(), fields);
        Object widget = res != null ? res.get("Data") : null;
        if (!isObject(widget)) {
            return data;
        }

        Map<String, Object> widgetData = asMap(widget);
        for (String field : MOBILE_FIELDS) {
            if (!data.containsKey(field) && widgetData.containsKey(field)) {
                data.put(field, widgetData.get(field));
            }
        }
        return data;
    }

    /** Poll for the settings of a domain that was just registered (the API needs a moment to sync). */
    private Object waitForSettings(String url, int attempts) {
        for (int i = 0; i < attempts; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            Object data = getSettingsData(url);
            if (data instanceof Map) {
                return data;
            }
        }
        return null;
    }

    /** Score card values from the scanner's "Data" (see skynet-widget.js scoreSummary). */
    private Map<String, Object> scoreSummary(Map<String, Object> data, Object altCount) {
        // Up to 99, never 100: a scanner score of 100 is shown as 99
        int score = (int) Math.max(0, Math.min(99, phpRound(toFloat(data.get("success_percentage")), 0)));

        Object[] level = SCORE_LEVELS[SCORE_LEVELS.length - 1];
        for (Object[] candidate : SCORE_LEVELS) {
            if (score >= (Integer) candidate[0]) {
                level = candidate;
                break;
            }
        }

        // Every check: fixed by the widget, or still waiting for a manual fix (API spelling)
        Map<String, Object> groups = asMap(data.get("acessibility_voilation"));
        List<Object> checks = new ArrayList<>(asList(groups.get("voilation_remediated")));
        checks.addAll(asList(groups.get("require_manual_action")));

        long fixed = 0;
        long total = 0;
        for (Object item : checks) {
            if (!isObject(item)) {
                continue;
            }
            Map<String, Object> check = asMap(item);
            long checkFixed = toInt(check.get("total_fixed"));
            fixed += checkFixed;
            total += checkFixed + toInt(check.get("total_error"));
        }

        // The summary wins when the API sends it
        Map<String, Object> summary = asMap(data.get("summary"));
        if (summary.get("total_violations") != null && summary.get("total_fixed") != null) {
            fixed = toInt(summary.get("total_fixed"));
            total = toInt(summary.get("total_violations"));
        }

        Object totalPages = data.get("total_selected_pages") != null ? data.get("total_selected_pages")
                : (data.get("total_pages") != null ? data.get("total_pages") : 0);
        Map<String, Object> pages = new LinkedHashMap<>();
        pages.put("scanned", toInt(data.get("total_scan_pages")));
        pages.put("total", toInt(totalPages));

        String reportUrl = toStr(data.get("pdf_report_url"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", toStr(data.get("domain")));
        result.put("score", score);
        result.put("level", level[1]);
        result.put("status", level[2]);
        result.put("violations", ratio(fixed, total));
        result.put("alt_text", altText(altCount));
        result.put("pages", pages);
        result.put("last_scan", toStr(data.get("last_scan")));
        result.put("report_url", HTTPS.matcher(reportUrl).matches() ? reportUrl : "");
        return result;
    }

    /** Alt Text Remediated: fixed = decorative + updated + other images, total = fixed + missing alt tag. */
    private Map<String, Object> altText(Object altCount) {
        Map<String, Object> count = asMap(altCount);
        long fixed = images(count, "total_decorative_images") + images(count, "total_update_alt_text") + images(count, "total_other_images");
        return ratio(fixed, fixed + images(count, "total_missing_alt_tag"));
    }

    private static long images(Map<String, Object> count, String key) {
        return Math.max(0, toInt(count.get(key)));
    }

    /** {fixed: 38, total: 44, percentage: 86.36} */
    private static Map<String, Object> ratio(long fixed, long total) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fixed", fixed);
        result.put("total", total);
        result.put("percentage", total > 0 ? jsonNumber(phpRound((double) fixed / total * 100, 2)) : 0);
        return result;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        return result;
    }

    private static String valueOf(String value) {
        return value == null ? "" : value;
    }
}
