package com.skynettechnologies.aem.aioa.core.services.impl;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * POSTs form fields to a Skynet endpoint and decodes the JSON answer.
 *
 * Same behaviour as post() in app/skynet-widget.js: a User-Agent is always sent (the API firewall
 * answers 406 without one), redirects are not followed, TLS certificates are verified, and the
 * decoded JSON object is returned also for 4xx answers with a JSON body. Network errors, timeouts
 * and non-JSON answers give null.
 *
 * Uses the JVM's default ProxySelector, so AEM as a Cloud Service advanced networking
 * (egress proxy system properties) applies when it is configured.
 */
final class SkynetHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(SkynetHttpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;
    private final String userAgent;
    private final Duration timeout;

    SkynetHttpClient(String userAgent, int timeoutSeconds) {
        this.userAgent = userAgent == null || userAgent.isBlank() ? "AIOA-Settings/1.0" : userAgent;
        this.timeout = Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : 30);
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(this.timeout)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    /** @return the decoded JSON object, or null on a network / JSON error */
    @SuppressWarnings("unchecked")
    Map<String, Object> post(String url, Map<String, String> fields) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(fields)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Object decoded = MAPPER.readValue(response.body(), Object.class);
            return decoded instanceof Map ? (Map<String, Object>) decoded : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            // network error, timeout or not JSON. The URL only: never log the bodies (sign-in links, api_key)
            LOG.warn("All in One Accessibility: call to {} failed: {}", url, e.toString());
            return null;
        }
    }

    /** application/x-www-form-urlencoded, like URLSearchParams (space as '+') */
    private static String formBody(Map<String, String> fields) {
        StringJoiner body = new StringJoiner("&");
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.add(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(field.getValue() == null ? "" : field.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
    }
}
