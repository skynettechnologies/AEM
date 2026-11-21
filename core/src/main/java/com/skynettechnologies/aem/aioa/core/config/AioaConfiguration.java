package com.skynettechnologies.aem.aioa.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * All in One Accessibility - configuration (AEM port of app/config.js).
 *
 * Every value that was a constant in the Node.js app is an OSGi property here, so it can be
 * set per environment from ui.config (or Cloud Manager environment variables) without a rebuild.
 * The fixed UI options (positions, icon sizes, preset colors) stay in
 * {@link com.skynettechnologies.aem.aioa.core.models.SettingsPageModel}.
 */
@ObjectClassDefinition(
        name = "All in One Accessibility - Settings",
        description = "Skynet Technologies API endpoints, the websites managed from AEM, who may open the settings console, and the front-end widget script.")
public @interface AioaConfiguration {

    /* ---------- Websites and access ---------- */

    @AttributeDefinition(
            name = "Site domains",
            description = "Public domains of the websites served by this AEM (for example www.example.com). "
                    + "The first one opens by default; with more than one, the console shows a website switcher. "
                    + "The AEM author host is never used as a fallback.")
    String[] siteDomains() default {};

    @AttributeDefinition(
            name = "Allowed groups",
            description = "Only members of these groups may open the settings console and call its route "
                    + "(the user 'admin' is always allowed).")
    String[] allowedGroups() default {"administrators"};

    /* ---------- Skynet Technologies API ---------- */

    @AttributeDefinition(name = "API base URL", description = "Base URL of the API that stores registrations and settings (with trailing slash).")
    String apiBase() default "https://ada.skynettechnologies.us/api/";

    @AttributeDefinition(name = "Platform name", description = "Sent as 'platform' when a free widget is registered.")
    String platform() default "AEM";

    @AttributeDefinition(name = "User-Agent", description = "User-Agent of the server's API calls. The API firewall answers HTTP 406 without one.")
    String userAgent() default "AIOA-Settings/1.0";

    @AttributeDefinition(name = "Widget settings API", description = "Endpoint the widget reads; supplies the mobile position fields for paid plans.")
    String widgetSettingsApi() default "https://freeada.skynettechnologies.com/api/widget-settings";

    @AttributeDefinition(name = "Check website API", description = "Plan details: dashboard sign-in link (manage_domain) and alt_count.")
    String checkWebsiteApi() default "https://ada.skynettechnologies.us/check-website";

    @AttributeDefinition(name = "Geo API (EU check)", description = "Called by the browser once, only before a new website is registered.")
    String geoApi() default "https://ipapi.co/json";

    @AttributeDefinition(name = "Upgrade URL", description = "'Upgrade to Paid Plan' destination. The console adds website, email, "
            + "company, first_name and last_name (from the website, the signed-in AEM user's profile and 'Company name').")
    String upgradeUrl() default "https://ada.skynettechnologies.us/trial-subscription";

    @AttributeDefinition(name = "Company name", description = "Optional. Sent as the company when a free widget is registered and "
            + "pre-filled in the 'Upgrade to Paid Plan' link (AEM user profiles have no company field). "
            + "On Cloud Service: the AIOA_COMPANY_NAME environment variable.")
    String companyName() default "";

    @AttributeDefinition(name = "HTTP timeout (seconds)", description = "Seconds before a Skynet API call gives up.", type = AttributeType.INTEGER)
    int httpTimeout() default 30;

    @AttributeDefinition(name = "Scan API", description = "Accessibility Score tab. No trailing slash (a trailing slash answers 301).")
    String scanApi() default "https://skynetaccessibilityscan.com/api/ada-scanner/domain/details";

    @AttributeDefinition(name = "Scan language", description = "Language of the scan texts.")
    String scanLang() default "en";

    @AttributeDefinition(name = "Default widget color", description = "Hex without '#'. Used as default and by Reset.")
    String defaultColor() default "420083";

    /* ---------- Front-end widget (publish) ---------- */

    @AttributeDefinition(name = "Widget enabled", description = "Master switch: load the accessibility widget on site pages "
            + "(added automatically, see below, or through the widget component).")
    boolean widgetEnabled() default true;

    @AttributeDefinition(name = "Add widget to pages automatically",
            description = "Adds the widget to every site page below the include paths (before </body>), without changing templates "
                    + "or content. Not in the page editor; pages that already contain the widget component are left as they are. "
                    + "Flush the Dispatcher cache after changing this.")
    boolean autoInjectEnabled() default true;

    @AttributeDefinition(name = "Automatic: include paths",
            description = "Pages below these paths get the widget automatically, for example /content/mysite.")
    String[] autoInjectPaths() default {"/content"};

    @AttributeDefinition(name = "Automatic: excluded paths",
            description = "Pages below these paths never get the widget automatically.")
    String[] autoInjectExcludedPaths() default {"/content/dam", "/content/experience-fragments", "/content/cq:tags"};

    @AttributeDefinition(name = "Widget script URL", description = "Script added to the public pages (id aioa-adawidget).")
    String widgetScriptUrl() default "https://www.skynettechnologies.com/accessibility/js/all-in-one-accessibility-js-widget-minify.js";

    @AttributeDefinition(name = "Widget load delay (ms)", description = "The widget loads after this delay so it never blocks page rendering.", type = AttributeType.INTEGER)
    int widgetLoadDelay() default 3000;
}
