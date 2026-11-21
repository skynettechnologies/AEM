package com.skynettechnologies.aem.aioa.core.models;

import javax.annotation.PostConstruct;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;

import com.skynettechnologies.aem.aioa.core.config.AioaConfiguration;
import com.skynettechnologies.aem.aioa.core.services.SkynetWidgetService;

/**
 * Front-end widget component (aioa/components/widget): the script tag the public pages load.
 *
 * The widget reads its settings (color, icon, position, plan) from Skynet by the page's domain,
 * so the query string only carries the default fallbacks of Skynet's own install snippet:
 *   ...all-in-one-accessibility-js-widget-minify.js?colorcode=#420083&token=&position=bottom_right
 */
@Model(adaptables = SlingHttpServletRequest.class)
public class WidgetModel {

    @OSGiService
    private SkynetWidgetService service;

    private boolean enabled;
    private String scriptUrl = "";
    private int delay = 3000;

    @PostConstruct
    protected void init() {
        AioaConfiguration config = service.getConfiguration();
        String base = config.widgetScriptUrl() == null ? "" : config.widgetScriptUrl().trim();
        enabled = config.widgetEnabled() && base.startsWith("https://");
        if (enabled) {
            scriptUrl = base + (base.contains("?") ? "&" : "?")
                    + "colorcode=#" + config.defaultColor() + "&token=&position=bottom_right";
        }
        delay = Math.max(0, config.widgetLoadDelay());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getScriptUrl() {
        return scriptUrl;
    }

    public int getDelay() {
        return delay;
    }
}
