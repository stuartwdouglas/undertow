package io.undertow.servlet.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 *
 *
 * @author Stuart Douglas
 */
public class SecurityConstraintInfo implements Cloneable {

    private final Set<String> urls = new HashSet<String>();
    private final Set<String> allowedRoles = new HashSet<String>();

    public SecurityConstraintInfo addUrl(final String url) {
        urls.add(url);
        return this;
    }

    public SecurityConstraintInfo addUrls(final String... urls) {
        this.urls.addAll(Arrays.asList(urls));
        return this;
    }

    public SecurityConstraintInfo addUrls(final Collection<String> urls) {
        this.urls.addAll(urls);
        return this;
    }

    public Set<String> getUrls() {
        return Collections.unmodifiableSet(urls);
    }

    public SecurityConstraintInfo addAllowedRole(final String allowedRole) {
        allowedRoles.add(allowedRole);
        return this;
    }

    public SecurityConstraintInfo addAllowedRoles(final String... allowedRoles) {
        this.allowedRoles.addAll(Arrays.asList(allowedRoles));
        return this;
    }

    public SecurityConstraintInfo addAllowedRoles(final Collection<String> allowedRoles) {
        this.allowedRoles.addAll(allowedRoles);
        return this;
    }

    public Set<String> getAllowedRoles() {
        return Collections.unmodifiableSet(allowedRoles);
    }

    @Override
    public SecurityConstraintInfo clone() {
        SecurityConstraintInfo clone = new SecurityConstraintInfo();
        clone.urls.addAll(urls);
        clone.allowedRoles.addAll(allowedRoles);
        return clone;
    }
}
