package com.skynettechnologies.aem.aioa.core.services.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.Externalizer;
import com.skynettechnologies.aem.aioa.core.services.SiteDomainService;
import com.skynettechnologies.aem.aioa.core.util.AioaValues;

/**
 * Saved website domains (/conf/aioa/settings, property siteDomains) and domain suggestions.
 * The store is written with the service user aioa-domain-store (subservice "domain-store"),
 * created by the repoinit configuration in ui.config.
 */
@Component(service = SiteDomainService.class)
public class SiteDomainServiceImpl implements SiteDomainService {

    private static final Logger LOG = LoggerFactory.getLogger(SiteDomainServiceImpl.class);

    static final String SUBSERVICE = "domain-store";
    static final String STORE_PARENT = "/conf/aioa";
    static final String STORE_PATH = STORE_PARENT + "/settings";
    static final String PROPERTY = "siteDomains";
    private static final String[] MAP_ROOTS = {"/etc/map/http", "/etc/map/https"};
    private static final String TECHNICAL_SUFFIX = ".adobeaemcloud.com";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY)
    private volatile Externalizer externalizer;

    @Override
    public List<String> getSavedDomains() {
        try (ResourceResolver resolver = serviceResolver()) {
            Resource store = resolver.getResource(STORE_PATH);
            return store == null ? Collections.emptyList() : clean(store.getValueMap().get(PROPERTY, new String[0]));
        } catch (LoginException e) {
            LOG.warn("All in One Accessibility: cannot read the saved websites (service user for '{}' missing?): {}", SUBSERVICE, e.toString());
            return Collections.emptyList();
        }
    }

    @Override
    public String saveDomain(String value) {
        String domain = AioaValues.normalizeDomain(value);
        if (!AioaValues.isValidDomain(domain)) {
            return null;
        }
        try (ResourceResolver resolver = serviceResolver()) {
            Resource store = storeResource(resolver);
            ModifiableValueMap properties = store.adaptTo(ModifiableValueMap.class);
            if (properties == null) {
                LOG.warn("All in One Accessibility: {} is not writable", STORE_PATH);
                return null;
            }
            List<String> domains = new ArrayList<>(clean(properties.get(PROPERTY, new String[0])));
            if (!domains.contains(domain)) {
                domains.add(domain);
                properties.put(PROPERTY, domains.toArray(new String[0]));
                resolver.commit();
                LOG.info("All in One Accessibility: website {} saved", domain);
            }
            return domain;
        } catch (LoginException | PersistenceException | RuntimeException e) {
            LOG.warn("All in One Accessibility: could not save the website {}: {}", domain, e.toString());
            return null;
        }
    }

    @Override
    public boolean removeDomain(String value) {
        String domain = AioaValues.normalizeDomain(value);
        if (!AioaValues.isValidDomain(domain)) {
            return false;
        }
        try (ResourceResolver resolver = serviceResolver()) {
            Resource store = resolver.getResource(STORE_PATH);
            ModifiableValueMap properties = store == null ? null : store.adaptTo(ModifiableValueMap.class);
            if (properties == null) {
                return false;
            }
            List<String> domains = new ArrayList<>(clean(properties.get(PROPERTY, new String[0])));
            if (!domains.remove(domain)) {
                return false;
            }
            properties.put(PROPERTY, domains.toArray(new String[0]));
            resolver.commit();
            LOG.info("All in One Accessibility: website {} removed from the console", domain);
            return true;
        } catch (LoginException | PersistenceException | RuntimeException e) {
            LOG.warn("All in One Accessibility: could not remove the website {}: {}", domain, e.toString());
            return false;
        }
    }

    @Override
    public List<String> suggestDomains(ResourceResolver resolver) {
        Set<String> found = new LinkedHashSet<>();
        if (resolver != null) {
            for (String root : MAP_ROOTS) {
                Resource mappings = resolver.getResource(root);
                if (mappings != null) {
                    for (Resource mapping : mappings.getChildren()) {
                        addHost(found, mapping.getValueMap().get("sling:match", mapping.getName()));
                    }
                }
            }
            Externalizer current = externalizer;
            if (current != null) {
                try {
                    addHost(found, current.publishLink(resolver, "/"));
                } catch (RuntimeException e) {
                    LOG.debug("All in One Accessibility: no Externalizer publish domain: {}", e.toString());
                }
            }
        }
        // Public domains first, AEM's technical *.adobeaemcloud.com domains last
        List<String> result = new ArrayList<>(found);
        result.sort((a, b) -> Boolean.compare(isTechnical(a), isTechnical(b)));
        return result;
    }

    /** True for AEM as a Cloud Service default domains (publish-p123-e456.adobeaemcloud.com): rarely the website visitors use. */
    public static boolean isTechnical(String domain) {
        return domain != null && domain.endsWith(TECHNICAL_SUFFIX);
    }

    /* ---------- internal ---------- */

    private ResourceResolver serviceResolver() throws LoginException {
        Map<String, Object> auth = new HashMap<>();
        auth.put(ResourceResolverFactory.SUBSERVICE, SUBSERVICE);
        return resolverFactory.getServiceResourceResolver(auth);
    }

    /** /conf/aioa/settings, created when repoinit has not created it (for example on an older install). */
    private static Resource storeResource(ResourceResolver resolver) throws PersistenceException {
        Resource store = resolver.getResource(STORE_PATH);
        if (store != null) {
            return store;
        }
        Resource parent = resolver.getResource(STORE_PARENT);
        if (parent == null) {
            Map<String, Object> folder = new HashMap<>();
            folder.put("jcr:primaryType", "sling:Folder");
            Resource conf = resolver.getResource("/conf");
            if (conf == null) {
                throw new PersistenceException("/conf is not readable");
            }
            parent = resolver.create(conf, "aioa", folder);
        }
        Map<String, Object> settings = new HashMap<>();
        settings.put("jcr:primaryType", "nt:unstructured");
        return resolver.create(parent, "settings", settings);
    }

    /** Sling mapping name / sling:match ("www.example.com.80", "www.example.com/") or a URL -> valid domain, else ignored. */
    static void addHost(Set<String> found, String value) {
        if (value == null) {
            return;
        }
        String host = value.trim();
        if (host.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            host = AioaValues.normalizeDomain(host);
        } else {
            host = host.replaceFirst("/.*$", "")      // path part of a mapping
                    .replaceFirst("\\.\\d+$", "")      // port suffix: www.example.com.80
                    .replace("\\.", ".");               // escaped dots of a regular expression
            host = AioaValues.normalizeDomain(host);
        }
        if (AioaValues.isValidDomain(host)) {
            found.add(host);
        }
    }

    private static List<String> clean(String[] values) {
        Set<String> domains = new LinkedHashSet<>();
        for (String value : values) {
            String domain = AioaValues.normalizeDomain(value);
            if (AioaValues.isValidDomain(domain)) {
                domains.add(domain);
            }
        }
        return new ArrayList<>(domains);
    }
}
