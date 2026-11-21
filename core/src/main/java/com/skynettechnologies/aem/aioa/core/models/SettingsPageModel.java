package com.skynettechnologies.aem.aioa.core.models;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skynettechnologies.aem.aioa.core.config.AioaConfiguration;
import com.skynettechnologies.aem.aioa.core.services.SiteDomainService;
import com.skynettechnologies.aem.aioa.core.services.SkynetWidgetService;
import com.skynettechnologies.aem.aioa.core.util.AdminAccess;
import com.skynettechnologies.aem.aioa.core.util.AioaValues;

/**
 * Everything the settings page HTL needs: what GET / in server.js put together for views/layout.ejs
 * (page domain, admin name and email, theme, boot data) and the option lists of app/config.js.
 *
 * Website: ?url=..., otherwise the first "Site domains" entry of the OSGi configuration.
 * Unlike the Node.js page, the page's own hostname is never used: on AEM that is the author host.
 *
 * Admin name and email come from the signed-in AEM user's profile; ?username= and ?email= still win.
 */
@Model(adaptables = SlingHttpServletRequest.class, cache = true)
public class SettingsPageModel {

    /** Folder of the widget icons inside the clientlib (resources are served through /etc.clientlibs). */
    static final String ICON_BASE = "/etc.clientlibs/aioa/clientlibs/settings/resources/images/icons/";
    static final String CSRF_TOKEN_URL = "/libs/granite/csrf/token.json";
    static final int ICON_TYPE_COUNT = 29;

    /** Accessibility figure used by the header logo and the upgrade banner (SVG path, 69 x 69 view box). */
    static final String LOGO_PATH = "M34.38,0A34.394,34.394,0,1,1,10.072,10.073,34.394,34.394,0,0,1,34.38,0Zm-.218,41.511-5,13.839a2.752,2.752,0,0,1-1.443,1.583A2.8,2.8,0,0,1,24,55.59a2.832,2.832,0,0,1-.1-2.149l3.491-9.667a14.235,14.235,0,0,0,.654-2.239,23.446,23.446,0,0,0,.3-2.361c.134-1.416.229-2.949.3-4.421s.125-2.9.163-4.08c.05-1.472-.346-1.567-1.527-1.846l-.25-.056-10.073-1.9a2.8,2.8,0,1,1,1.018-5.484L28.8,23.421c.431.039.85.09,1.293.14a32.187,32.187,0,0,0,4.017.3,45.275,45.275,0,0,0,5-.353c.5-.056.979-.118,1.455-.163l10.212-1.914a2.764,2.764,0,0,1,2.848,4.281v.034a2.8,2.8,0,0,1-1.8,1.169l-9.782,1.836c-.325.073-.615.125-.875.163-1.018.174-1.522.263-1.461,1.712.045,1.058.174,2.323.341,3.643.2,1.55.453,3.2.722,4.7a22.945,22.945,0,0,0,.559,2.546c.224.76.441,1.538.778,2.474l3.409,9.457a2.8,2.8,0,1,1-5.253,1.909l-5.02-13.814-.559-1.024-.559,1Zm.218-29.949a4.942,4.942,0,1,1-3.487,1.446,4.942,4.942,0,0,1,3.487-1.446ZM54.722,14.04a28.774,28.774,0,1,0,8.393,20.341A28.774,28.774,0,0,0,54.722,14.04Z";

    /** [value sent to the API, label, horizontal, vertical]; null is the center preview cell. */
    private static final String[][] POSITIONS = {
            {"top_left", "Top left", "0", "0"},
            {"top_center", "Top center", "0.5", "0"},
            {"top_right", "Top right", "1", "0"},
            {"middle_left", "Middle left", "0", "0.5"},
            null,
            {"middle_right", "Middle right", "1", "0.5"},
            {"bottom_left", "Bottom left", "0", "1"},
            {"bottom_center", "Bottom center", "0.5", "1"},
            {"bottom_right", "Bottom right", "1", "1"},
    };

    /** [value sent to the API, label, preview diameter in px] */
    private static final Object[][] ICON_SIZES = {
            {"aioa-big-icon", "Big", 84},
            {"aioa-medium-icon", "Medium", 74},
            {"aioa-default-icon", "Default", 64},
            {"aioa-small-icon", "Small", 54},
            {"aioa-extra-small-icon", "Extra small", 44},
    };

    /** [hex, name] */
    private static final String[][] PRESET_COLORS = {
            {"420083", "Purple (default)"},
            {"1D4ED8", "Blue"},
            {"0E7490", "Teal"},
            {"15803D", "Green"},
            {"B91C1C", "Red"},
            {"C2410C", "Orange"},
            {"BE185D", "Pink"},
            {"111827", "Charcoal"},
    };

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Self
    private SlingHttpServletRequest request;

    @SlingObject(injectionStrategy = org.apache.sling.models.annotations.injectorspecific.InjectionStrategy.OPTIONAL)
    private SlingHttpServletResponse response;

    @OSGiService
    private SkynetWidgetService service;

    @OSGiService
    private SiteDomainService siteDomainService;

    private boolean allowed;
    private boolean setup;
    private boolean detected;
    private List<String> suggestions = Collections.emptyList();
    private String domain = "";
    private String name = "";
    private String firstName = "";
    private String lastName = "";
    private String email = "";
    private String company = "";
    private String theme = "";
    private String routeUrl = "";
    private String pagePath = "";
    private String bootJson = "{}";
    private List<String> siteDomains = Collections.emptyList();
    private List<String> removableDomains = Collections.emptyList();
    private AioaConfiguration config;

    @PostConstruct
    protected void init() {
        config = service.getConfiguration();
        allowed = AdminAccess.isAllowed(request.getResourceResolver(), config.allowedGroups());
        if (!allowed) {
            if (response != null) {
                response.setStatus(403);
            }
            return;
        }

        String context = request.getContextPath() == null ? "" : request.getContextPath();
        pagePath = context + request.getResource().getPath() + ".html";
        routeUrl = context + request.getResource().getPath() + ".route.json";

        // Websites: OSGi "Site domains" (AIOA_SITE_DOMAIN) first, then the ones saved from this console
        List<String> configured = Arrays.stream(config.siteDomains() == null ? new String[0] : config.siteDomains())
                .map(AioaValues::normalizeDomain)
                .filter(AioaValues::isValidDomain)
                .distinct()
                .collect(Collectors.toList());
        List<String> saved = siteDomainService.getSavedDomains();
        siteDomains = Stream.concat(configured.stream(), saved.stream())
                .distinct()
                .collect(Collectors.toList());
        // Remove website: only saved websites that the OSGi configuration does not list as well
        removableDomains = saved.stream()
                .filter(savedDomain -> !configured.contains(savedDomain))
                .collect(Collectors.toList());

        String urlParam = param("url");
        domain = !urlParam.isEmpty() ? urlParam : (siteDomains.isEmpty() ? "" : siteDomains.get(0));

        suggestions = siteDomainService.suggestDomains(request.getResourceResolver()).stream()
                .filter(suggestion -> !siteDomains.contains(suggestion))
                .collect(Collectors.toList());

        // No website configured or saved: use the domain AEM knows (Sling mapping host, Externalizer publish domain)
        // automatically. AEM's technical *.adobeaemcloud.com domains are never used on their own: visitors rarely
        // see them, so the widget settings would end up on the wrong website.
        if (domain.isEmpty()) {
            domain = suggestions.stream().filter(suggestion -> !isTechnical(suggestion)).findFirst().orElse("");
            detected = !domain.isEmpty();
        }

        // Nothing usable found: the console asks for the domain once ("Your website" step), with the suggestions
        setup = domain.isEmpty();

        // Admin details: ?username= / ?email= / ?company= win, otherwise the signed-in user's AEM profile
        // (first / last name, email) and the OSGi "Company name" (AEM profiles have no company field)
        String username = param("username");
        if (!username.isEmpty()) {
            String[] parts = username.trim().split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        } else {
            firstName = AdminAccess.profileValue(request.getResourceResolver(), "profile/givenName");
            lastName = AdminAccess.profileValue(request.getResourceResolver(), "profile/familyName");
            if (firstName.isEmpty()) {
                // Only one name in the profile (the SDK's admin has just "Administrator"): use it as the first name
                firstName = lastName;
                lastName = "";
            }
        }
        name = (firstName + " " + lastName).trim();

        String mail = param("email");
        email = !mail.isEmpty() ? mail : AdminAccess.profileValue(request.getResourceResolver(), "profile/email");

        String companyParam = param("company");
        company = !companyParam.isEmpty() ? companyParam
                : (config.companyName() == null ? "" : config.companyName().trim());

        // AEM: the console is always light (the theme toggle is hidden); ?theme= is no longer used
        theme = "light";

        Map<String, Object> boot = new LinkedHashMap<>();
        boot.put("domain", domain);
        boot.put("name", name);
        boot.put("email", email);
        boot.put("company", company);
        boot.put("iconBase", ICON_BASE);
        boot.put("defaultColor", config.defaultColor());
        boot.put("routeUrl", routeUrl);
        boot.put("csrfTokenUrl", context + CSRF_TOKEN_URL);
        boot.put("apiUpdateUrl", config.apiBase() + "widget-setting-update-platform");
        boot.put("geoApiUrl", config.geoApi());
        boot.put("hostFallback", false);
        boot.put("setup", setup);
        boot.put("detected", detected);
        boot.put("removableDomains", removableDomains);
        try {
            bootJson = AioaValues.jsonForScript(MAPPER.writeValueAsString(boot));
        } catch (JsonProcessingException e) {
            bootJson = "{}";
        }
    }

    private String param(String key) {
        return AioaValues.cleanRequestValue(AioaValues.lastValue(request.getParameterValues(key)));
    }

    /* ---------- Page values ---------- */

    public boolean isAllowed() {
        return allowed;
    }

    public String getDomain() {
        return domain;
    }

    public String getTheme() {
        return theme;
    }

    public String getBootJson() {
        return bootJson;
    }

    public String getPagePath() {
        return pagePath;
    }

    public String getIconBase() {
        return ICON_BASE;
    }

    public String getLogoPath() {
        return LOGO_PATH;
    }

    /**
     * "Upgrade to Paid Plan" link with the details the trial page pre-fills, for example
     * https://ada.skynettechnologies.us/trial-subscription?website=example.com&email=dev@example.com
     * &company=demo%20company&first_name=hello&last_name=dev (empty values are left out).
     * Without an email in the profile, no-reply@&lt;website&gt; is sent, as for the free widget registration.
     */
    public String getUpgradeUrl() {
        String website = AioaValues.normalizeDomain(domain);
        String mail = email.trim();
        if (!AioaValues.isValidEmail(mail)) {
            mail = AioaValues.isValidDomain(website) ? "no-reply@" + website.replaceFirst("^www\\.", "") : "";
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("website", website);
        query.put("email", mail);
        query.put("company", company);
        query.put("first_name", firstName);
        query.put("last_name", lastName);

        StringBuilder url = new StringBuilder(config.upgradeUrl() == null ? "" : config.upgradeUrl().trim());
        char separator = url.indexOf("?") >= 0 ? '&' : '?';
        for (Map.Entry<String, String> entry : query.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!value.isEmpty()) {
                url.append(separator).append(entry.getKey()).append('=').append(urlEncode(value));
                separator = '&';
            }
        }
        return url.toString();
    }

    /** Query value encoding as in the trial page's links: spaces as %20 (not "+"), "@" kept as is. */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%40", "@");
    }

    public String getDefaultColor() {
        return config.defaultColor().toUpperCase(java.util.Locale.ROOT);
    }

    public String getDefaultColorLower() {
        return "#" + config.defaultColor().toLowerCase(java.util.Locale.ROOT);
    }

    /** Website switcher: shown when more than one site domain is configured. */
    public List<SiteOption> getSites() {
        List<SiteOption> sites = new ArrayList<>();
        for (String site : siteDomains) {
            sites.add(new SiteOption(site, site.equals(AioaValues.normalizeDomain(domain))));
        }
        return sites;
    }

    public boolean isShowSiteSwitcher() {
        return siteDomains.size() > 1;
    }

    /* ---------- "Your website" step / Add website ---------- */

    /** True when no website is known yet: the console starts with the "Your website" step. */
    public boolean isSetup() {
        return setup;
    }

    /** Domains AEM knows (Sling mappings, Externalizer publish domain) that are not saved yet, best first. */
    public List<String> getSuggestions() {
        return suggestions;
    }

    public boolean isHasSuggestions() {
        return !suggestions.isEmpty();
    }

    /** Pre-filled in the domain field: the first suggestion, unless it is an AEM technical domain. */
    public String getPrefillDomain() {
        return suggestions.isEmpty() || isTechnical(suggestions.get(0)) ? "" : suggestions.get(0);
    }

    /** True when a suggestion is an AEM technical domain (*.adobeaemcloud.com): the step then explains it. */
    public boolean isTechnicalSuggestion() {
        return suggestions.stream().anyMatch(SettingsPageModel::isTechnical);
    }

    private static boolean isTechnical(String domain) {
        return domain.endsWith(".adobeaemcloud.com");
    }

    /* ---------- Option lists (app/config.js) ---------- */

    public List<Position> getPositions() {
        return positions("edit-position-");
    }

    public List<Position> getMobilePositions() {
        return positions("mobile-position-");
    }

    private List<Position> positions(String idPrefix) {
        List<Position> list = new ArrayList<>();
        for (String[] pos : POSITIONS) {
            list.add(pos == null ? Position.center() : new Position(pos[0], pos[1], pos[2], pos[3], idPrefix + AioaValues.slug(pos[0]), "bottom_right".equals(pos[0])));
        }
        return list;
    }

    public List<IconSize> getIconSizes() {
        List<IconSize> list = new ArrayList<>();
        for (Object[] size : ICON_SIZES) {
            String value = (String) size[0];
            list.add(new IconSize(value, (String) size[1], (Integer) size[2], "edit-size-" + AioaValues.slug(value), "aioa-medium-icon".equals(value)));
        }
        return list;
    }

    public List<IconType> getIconTypes() {
        List<IconType> list = new ArrayList<>();
        for (int i = 1; i <= ICON_TYPE_COUNT; i++) {
            list.add(new IconType(i, ICON_BASE));
        }
        return list;
    }

    public List<PresetColor> getPresetColors() {
        List<PresetColor> list = new ArrayList<>();
        for (String[] color : PRESET_COLORS) {
            list.add(new PresetColor(color[0], color[1]));
        }
        return list;
    }

    /* ---------- Items ---------- */

    public static final class SiteOption {
        private final String domain;
        private final boolean selected;

        SiteOption(String domain, boolean selected) {
            this.domain = domain;
            this.selected = selected;
        }

        public String getDomain() {
            return domain;
        }

        public boolean isSelected() {
            return selected;
        }
    }

    public static final class Position {
        private final String value;
        private final String label;
        private final String gx;
        private final String gy;
        private final String id;
        private final boolean checked;
        private final boolean center;

        Position(String value, String label, String gx, String gy, String id, boolean checked) {
            this(value, label, gx, gy, id, checked, false);
        }

        private Position(String value, String label, String gx, String gy, String id, boolean checked, boolean center) {
            this.value = value;
            this.label = label;
            this.gx = gx;
            this.gy = gy;
            this.id = id;
            this.checked = checked;
            this.center = center;
        }

        static Position center() {
            return new Position("", "", "1", "1", "", false, true);
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }

        public String getGx() {
            return gx;
        }

        public String getGy() {
            return gy;
        }

        public String getId() {
            return id;
        }

        public boolean isChecked() {
            return checked;
        }

        public boolean isCenter() {
            return center;
        }
    }

    public static final class IconSize {
        private final String value;
        private final String label;
        private final int diameter;
        private final String id;
        private final boolean checked;

        IconSize(String value, String label, int diameter, String id, boolean checked) {
            this.value = value;
            this.label = label;
            this.diameter = diameter;
            this.id = id;
            this.checked = checked;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }

        public String getId() {
            return id;
        }

        public boolean isChecked() {
            return checked;
        }

        /** Preview diameter in px (HTL: style="--d:${size.diameter @ context='styleToken'}px") */
        public int getDiameter() {
            return diameter;
        }
    }

    public static final class IconType {
        private final int number;
        private final String iconBase;

        IconType(int number, String iconBase) {
            this.number = number;
            this.iconBase = iconBase;
        }

        public int getNumber() {
            return number;
        }

        public String getId() {
            return "edit-type-" + number;
        }

        public String getValue() {
            return "aioa-icon-type-" + number;
        }

        public String getSrc() {
            return iconBase + "aioa-icon-type-" + number + ".svg";
        }

        public boolean isChecked() {
            return number == 1;
        }
    }

    public static final class PresetColor {
        private final String hex;
        private final String name;

        PresetColor(String hex, String name) {
            this.hex = hex;
            this.name = name;
        }

        public String getHex() {
            return hex;
        }

        public String getName() {
            return name;
        }

        public String getAriaLabel() {
            return name + " #" + hex;
        }

        /** CSS color "#420083" (HTL: style="--c:${color.color @ context='styleToken'}") */
        public String getColor() {
            return "#" + hex;
        }
    }
}
