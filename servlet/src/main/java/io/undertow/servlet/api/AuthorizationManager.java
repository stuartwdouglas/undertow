package io.undertow.servlet.api;

import io.undertow.security.idm.Account;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Authorization manager. The servlet implementation delegates all authorization checks to this interface.
 *
 * @author Stuart Douglas
 */
public interface AuthorizationManager {

    boolean isUserInRole(String roleName, final Account account, final ServletInfo servletInfo, final HttpServletRequest request, Deployment deployment);

    boolean canAccessResource(List<SingleConstraintMatch> mappedConstraints, final Account account, final ServletInfo servletInfo, final HttpServletRequest request, Deployment deployment);

    boolean isAuthenticationRequired(List<SingleConstraintMatch> mappedConstraints, final ServletInfo servletInfo, final HttpServletRequest request, Deployment deployment);

    TransportGuaranteeType transportGuarantee(TransportGuaranteeType currentConnectionGuarantee, TransportGuaranteeType configuredRequiredGuarantee, final HttpServletRequest request);

}
