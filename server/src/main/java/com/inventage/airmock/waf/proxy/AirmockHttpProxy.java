package com.inventage.airmock.waf.proxy;

import com.inventage.airmock.kernel.proxy.HttpProxy;
import com.inventage.airmock.kernel.proxy.internal.BackendRequestImpl;
import com.inventage.airmock.kernel.proxy.internal.HttpProxyImpl;
import io.vertx.core.http.HttpClient;
import io.vertx.reactivex.ext.web.RoutingContext;

public class AirmockHttpProxy extends HttpProxyImpl {
    public AirmockHttpProxy(String hostName, String hostPort) {
        super(hostName, hostPort);
    }

    @Override
    public BackendRequestImpl getBackendRequestImpl(String proxyHostName,
                                                    String proxyHostPort,
                                                    String applicationJwtCookieName,
                                                    HttpClient client,
                                                    RoutingContext routingContext,
                                                    HttpProxy proxy) {
        return new AirmockBackendRequest(proxyHost(routingContext), proxyPort(routingContext), applicationJwtCookieName, client, routingContext, this);
    }

    /**
     *
     * @param routingContext the current routing context
     * @return the host used for X-Forwarded-Host
     */
    protected String proxyHost(RoutingContext routingContext) {
        return proxyHostName != null ? proxyHostName : filterPort(routingContext.request().host());
    }

    /**
     *
     * @param routingContext the current routing context
     * @return the port used for X-Forwarded-Port
     */
    protected String proxyPort(RoutingContext routingContext) {
        return proxyHostPort != null ? proxyHostPort : String.valueOf(routingContext.request().localAddress().port());
    }

    /**
     * Filter out if the port is given, e.g. localhost:8080.
     *
     * @param host the host from the request (header)
     * @return the host name
     */
    protected String filterPort(String host) {
        if (host == null) {
            return host;
        }
        return host.split(":")[0];
    }
}
