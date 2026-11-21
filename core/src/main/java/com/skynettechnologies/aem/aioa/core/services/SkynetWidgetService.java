package com.skynettechnologies.aem.aioa.core.services;

import java.util.Map;

import com.skynettechnologies.aem.aioa.core.config.AioaConfiguration;

/**
 * Server-side calls to the Skynet Technologies API (AEM port of app/skynet-widget.js).
 *
 * Every action takes the posted form fields and returns a JSON-ready map, exactly like the
 * Node.js actions: the settings page reads the same answers ({"not_registered": true},
 * {"error": ..., "pending": true}, {"link": ...}, the score summary, ...).
 *
 * Saving the widget settings is still done by the browser directly
 * (clientlib js/save.js -> widget-setting-update-platform).
 */
public interface SkynetWidgetService {

    /** Settings of a domain; {"not_registered": true} for a new website. Never registers. */
    Map<String, Object> fetchWidgetSettings(Map<String, String> fields);

    /** Registers the free widget for a new website (once), then waits for its settings. */
    Map<String, Object> registerDomain(Map<String, String> fields);

    /** Dashboard sign-in link of a paid plan: {"link": "https://..."}. */
    Map<String, Object> getDashboardLink(Map<String, String> fields);

    /** Accessibility Score tab: the scan results trimmed to what the score card shows. */
    Map<String, Object> getScanDetails(Map<String, String> fields);

    /** The active configuration. */
    AioaConfiguration getConfiguration();
}
