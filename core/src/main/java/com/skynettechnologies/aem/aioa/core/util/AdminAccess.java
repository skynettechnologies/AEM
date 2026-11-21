package com.skynettechnologies.aem.aioa.core.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "requireAdmin" integration point of server.js, for AEM: the console and its route are
 * for members of the configured groups only (OSGi "Allowed groups"; "admin" is always allowed).
 * Group membership is checked transitively (memberOf), so nested groups work.
 */
public final class AdminAccess {

    private static final Logger LOG = LoggerFactory.getLogger(AdminAccess.class);

    private AdminAccess() {
    }

    public static boolean isAllowed(ResourceResolver resolver, String[] allowedGroups) {
        String userId = resolver.getUserID();
        if (userId == null || "anonymous".equals(userId)) {
            return false;
        }
        if ("admin".equals(userId)) {
            return true;
        }
        Set<String> allowed = Arrays.stream(allowedGroups == null ? new String[0] : allowedGroups)
                .filter(g -> g != null && !g.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return false;
        }
        try {
            UserManager userManager = resolver.adaptTo(UserManager.class);
            Authorizable user = userManager != null ? userManager.getAuthorizable(userId) : null;
            if (user == null) {
                return false;
            }
            Iterator<Group> groups = user.memberOf();
            while (groups.hasNext()) {
                if (allowed.contains(groups.next().getID())) {
                    return true;
                }
            }
        } catch (RepositoryException e) {
            LOG.warn("All in One Accessibility: could not read the groups of {}", userId, e);
        }
        return false;
    }

    /** A profile property of the current user ("profile/email", "profile/givenName", ...), '' when missing. */
    public static String profileValue(ResourceResolver resolver, String property) {
        try {
            UserManager userManager = resolver.adaptTo(UserManager.class);
            Authorizable user = userManager != null && resolver.getUserID() != null
                    ? userManager.getAuthorizable(resolver.getUserID()) : null;
            if (user == null || !user.hasProperty(property)) {
                return "";
            }
            javax.jcr.Value[] values = user.getProperty(property);
            return values != null && values.length > 0 ? values[0].getString().trim() : "";
        } catch (RepositoryException e) {
            return "";
        }
    }
}
