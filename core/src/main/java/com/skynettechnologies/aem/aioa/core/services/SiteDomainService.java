package com.skynettechnologies.aem.aioa.core.services;

import java.util.List;

import org.apache.sling.api.resource.ResourceResolver;

/**
 * The websites (public domains) the settings console manages, so nobody has to configure them by hand.
 *
 *  - Saved domains: confirmed once in the console ("Your website" step / "Add website") and kept in the
 *    repository on author (/conf/aioa/settings), written by the service user aioa-domain-store.
 *  - Suggestions: domains AEM already knows, offered in that step: Sling mapping hosts (/etc/map)
 *    and the Externalizer "publish" domain.
 *
 * The OSGi "Site domains" (AIOA_SITE_DOMAIN) still work and come first.
 */
public interface SiteDomainService {

    /** Domains saved from the console, normalized and valid, in the order they were added. */
    List<String> getSavedDomains();

    /**
     * Saves a domain (normalized, for example "https://WWW.Example.com/" becomes "www.example.com").
     *
     * @return the saved domain, or null when it is not a valid domain or could not be stored
     */
    String saveDomain(String domain);

    /**
     * Removes a saved domain from the console. Only the console's list changes: the widget and its settings
     * stay with Skynet Technologies, and the website can be added again.
     *
     * @return true when the domain was saved and is now removed
     */
    boolean removeDomain(String domain);

    /** Domains found in AEM that are likely the website, best first: Sling mapping hosts, then the Externalizer publish domain. */
    List<String> suggestDomains(ResourceResolver resolver);
}
