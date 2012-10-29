package io.undertow.servlet.api;

/**
 * @author Stuart Douglas
 */
public class LoginConfigInfo implements Cloneable {

    private volatile String method;
    private volatile String realm;

    /**
     * The login page for FORM auth
     */
    private volatile String loginPage;

    /**
     * The error page used for FORM auth
     */
    private volatile String errorPage;

    public String getMethod() {
        return method;
    }

    public LoginConfigInfo setMethod(final String method) {
        this.method = method;
        return this;
    }

    public String getRealm() {
        return realm;
    }

    public LoginConfigInfo setRealm(final String realm) {
        this.realm = realm;
        return this;
    }

    public String getLoginPage() {
        return loginPage;
    }

    public LoginConfigInfo setLoginPage(final String loginPage) {
        this.loginPage = loginPage;
        return this;
    }

    public String getErrorPage() {
        return errorPage;
    }

    public LoginConfigInfo setErrorPage(final String errorPage) {
        this.errorPage = errorPage;
        return this;
    }

    @Override
    public LoginConfigInfo clone() {
        LoginConfigInfo clone = new LoginConfigInfo();
        clone.errorPage = errorPage;
        clone.loginPage = loginPage;
        clone.realm = realm;
        clone.method = method;
        return clone;
    }
}
