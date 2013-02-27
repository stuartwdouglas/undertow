/**
 * JBoss, Home of Professional Open Source. Copyright 2012, Red Hat, Inc., and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of individual
 * contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package io.undertow.proxy.container;

import java.io.Serializable;

/**
 * {@code Server}
 *
 * Created on Jun 12, 2012 at 4:57:25 PM
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 */
public class Server implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = -7107364666635260030L;

    /**
     * Name of the server.
     */
    private String name;
    /**
     * The Root directory.
     */
    private String root;

    /**
     * Number of thread per child process.
     */
    private int threadsPerChild;

    /**
     * Max requests a child will handle before stopping
     */
    private int maxRequestsPerChild;

    /**
     * Max simultaneous requests supported
     */
    private int maxClient;

    /**
     * Max size of any request header field
     */
    private int limitReqFieldsize;

    /**
     * Max on number of request header fields
     */
    private int limitReqFields;

    /**
     * The server request scheme (http/https)
     */
    private String scheme;

    /**
     * Server keeps connections opened between request
     */
    private boolean keepAlive;

    /**
     * Amount of time the connection between httpd and the browser is kept
     * opened waiting for request
     */
    private int keepAliveTimeout;

    /**
     * Max number of request for a browser httpd will process before closing the
     * connection
     */
    private int maxKeepAliveRequests;

    /**
     * Max time in milliseconds the proxy will wait for a backend server.
     */
    private int timeout;

    /**
     * Create a new instance of {@code Server}
     */
    public Server() {
        super();
    }

    /**
     * Getter for name
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Setter for the name
     *
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Getter for root
     *
     * @return the root
     */
    public String getRoot() {
        return this.root;
    }

    /**
     * Setter for the root
     *
     * @param root
     *            the root to set
     */
    public void setRoot(String root) {
        this.root = root;
    }

    /**
     * Getter for threadsPerChild
     *
     * @return the threadsPerChild
     */
    public int getThreadsPerChild() {
        return this.threadsPerChild;
    }

    /**
     * Setter for the threadsPerChild
     *
     * @param threadsPerChild
     *            the threadsPerChild to set
     */
    public void setThreadsPerChild(int threadsPerChild) {
        this.threadsPerChild = threadsPerChild;
    }

    /**
     * Getter for maxRequestsPerChild
     *
     * @return the maxRequestsPerChild
     */
    public int getMaxRequestsPerChild() {
        return this.maxRequestsPerChild;
    }

    /**
     * Setter for the maxRequestsPerChild
     *
     * @param maxRequestsPerChild
     *            the maxRequestsPerChild to set
     */
    public void setMaxRequestsPerChild(int maxRequestsPerChild) {
        this.maxRequestsPerChild = maxRequestsPerChild;
    }

    /**
     * Getter for maxClient
     *
     * @return the maxClient
     */
    public int getMaxClient() {
        return this.maxClient;
    }

    /**
     * Setter for the maxClient
     *
     * @param maxClient
     *            the maxClient to set
     */
    public void setMaxClient(int maxClient) {
        this.maxClient = maxClient;
    }

    /**
     * Getter for limitReqFieldsize
     *
     * @return the limitReqFieldsize
     */
    public int getLimitReqFieldsize() {
        return this.limitReqFieldsize;
    }

    /**
     * Setter for the limitReqFieldsize
     *
     * @param limitReqFieldsize
     *            the limitReqFieldsize to set
     */
    public void setLimitReqFieldsize(int limitReqFieldsize) {
        this.limitReqFieldsize = limitReqFieldsize;
    }

    /**
     * Getter for limitReqFields
     *
     * @return the limitReqFields
     */
    public int getLimitReqFields() {
        return this.limitReqFields;
    }

    /**
     * Setter for the limitReqFields
     *
     * @param limitReqFields
     *            the limitReqFields to set
     */
    public void setLimitReqFields(int limitReqFields) {
        this.limitReqFields = limitReqFields;
    }

    /**
     * Getter for scheme
     *
     * @return the scheme
     */
    public String getScheme() {
        return this.scheme;
    }

    /**
     * Setter for the scheme
     *
     * @param scheme
     *            the scheme to set
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Getter for keepAlive
     *
     * @return the keepAlive
     */
    public boolean isKeepAlive() {
        return this.keepAlive;
    }

    /**
     * Setter for the keepAlive
     *
     * @param keepAlive
     *            the keepAlive to set
     */
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    /**
     * Getter for keepAliveTimeout
     *
     * @return the keepAliveTimeout
     */
    public int getKeepAliveTimeout() {
        return this.keepAliveTimeout;
    }

    /**
     * Setter for the keepAliveTimeout
     *
     * @param keepAliveTimeout
     *            the keepAliveTimeout to set
     */
    public void setKeepAliveTimeout(int keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    /**
     * Getter for maxKeepAliveRequests
     *
     * @return the maxKeepAliveRequests
     */
    public int getMaxKeepAliveRequests() {
        return this.maxKeepAliveRequests;
    }

    /**
     * Setter for the maxKeepAliveRequests
     *
     * @param maxKeepAliveRequests
     *            the maxKeepAliveRequests to set
     */
    public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
        this.maxKeepAliveRequests = maxKeepAliveRequests;
    }

    /**
     * Getter for timeout
     *
     * @return the timeout
     */
    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Setter for the timeout
     *
     * @param timeout
     *            the timeout to set
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

}
