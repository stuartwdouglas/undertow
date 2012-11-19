package io.undertow.servlet.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class SecurityConstraint {

    private final Set<String> urlPatterns;
    private final Set<String> roleNames;
    private final TransportGuaranteeType transportGuaranteeType;

    public SecurityConstraint(Set<String> urlPatterns, Set<String> roleNames, TransportGuaranteeType transportGuaranteeType) {
        this.urlPatterns = Collections.unmodifiableSet(new HashSet<String>(urlPatterns));
        this.roleNames = Collections.unmodifiableSet(new HashSet<String>(roleNames));
        this.transportGuaranteeType = transportGuaranteeType;
    }

    public Set<String> getUrlPatterns() {
        return urlPatterns;
    }

    public Set<String> getRoleNames() {
        return roleNames;
    }

    public TransportGuaranteeType getTransportGuaranteeType() {
        return transportGuaranteeType;
    }
}
