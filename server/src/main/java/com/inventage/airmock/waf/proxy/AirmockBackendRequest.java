package com.inventage.airmock.waf.proxy;

import com.inventage.airmock.kernel.proxy.HttpProxy;
import com.inventage.airmock.kernel.proxy.internal.BackendRequestImpl;
import com.inventage.airmock.waf.cookiebag.Cookiemanager;
import com.inventage.airmock.waf.headerbag.Headermanager;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Map;

/**
 * BackendRequest used by airmock.
 */
public class AirmockBackendRequest extends BackendRequestImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(AirmockBackendRequest.class);

    public AirmockBackendRequest(String proxyHostName,
                                 String proxyHostPort,
                                 String applicationJwtCookieName,
                                 HttpClient client,
                                 RoutingContext routingContext,
                                 HttpProxy proxy) {
        super(proxyHostName, proxyHostPort, applicationJwtCookieName, client, routingContext, proxy);
    }

    @Override
    protected void propagateHeaders(RoutingContext routingContext, HttpServerRequest frontendRequest, HttpClientRequest backendRequest) {
        copyFilteredHeaders(backendRequest.headers());
        // inform backend about reverse proxy (https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Host)
        if (proxyHostName != null) {
            addHeaderIfNotAlready(backendRequest, "X-Forwarded-Host", proxyHostName);
        }
        if (proxyHostPort != null) {
            addHeaderIfNotAlready(backendRequest, "X-Forwarded-Port", proxyHostPort);
        }

        // set cookies from cookie bag
        Cookiemanager.setCookiesToRequest(routingContext, backendRequest);
        Headermanager.setHeadersToRequest(routingContext, backendRequest);
    }

    /**
     * copy some headers to destionation.
     *
     * @param to destination
     */
    protected void copyFilteredHeaders(MultiMap to) {
        // Set headers
        for (Map.Entry<String, String> header : frontendRequest.headers()) {
            if (header.getKey().equalsIgnoreCase("host")) {
                // don't copy host, as HttpClient will set it
            }
            else if (header.getKey().equalsIgnoreCase("cookie")) {
                // don't copy cookie, because they are handled specially later
            }
            else {
                to.add(header.getKey(), header.getValue());
            }
        }
    }
}
