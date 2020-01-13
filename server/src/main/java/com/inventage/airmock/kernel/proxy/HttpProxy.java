package com.inventage.airmock.kernel.proxy;

import com.inventage.airmock.kernel.proxy.internal.BackendRequestImpl;
import com.inventage.airmock.kernel.proxy.internal.BackendResponse;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.function.Function;

/**
 * Reverse proxy for web requests, which must be delegated to a
 * backend system. The web requests are named frontend requests for clarity.
 * <p>
 * Initial code is taken from https://github.com/vietj/vertx-http-proxy.
 */
public interface HttpProxy {
    /**
     * Set the address of the backend system.
     *
     * @param address the address
     * @return HttpProxy
     */
    HttpProxy backend(SocketAddress address);

    /**
     * Set the address of the backend system.
     *
     * @param host the host name of the backend system
     * @param port the port of the backend system
     * @return HttpProxy
     */
    HttpProxy backend(String host, int port);

    /**
     * Set the backend selector.
     *
     * @param selector backend selector
     * @return HttpProxy
     */
    HttpProxy backendSelector(Function<HttpServerRequest, Future<SocketAddress>> selector);

    /**
     * Set the circuitBreaker.
     *
     * @param circuitBreaker circuitBreaker
     * @return HttpProxy
     */
    HttpProxy circuitBreaker(CircuitBreaker circuitBreaker);

    /**
     * Set the applicationJwtCookieName.
     *
     * @param applicationJwtCookieName applicationJwtCookieName
     * @return HttpProxy
     */
    HttpProxy applicationJwtCookieName(String applicationJwtCookieName);

    /**
     * Delegate request.
     *
     * @param routingContext rc
     */
    void delegate(RoutingContext routingContext);

    /**
     * Delegate request.
     *
     * @param routingContext  rc
     * @param fallbackHandler fallback handler
     */
    void delegate(RoutingContext routingContext, Handler<RoutingContext> fallbackHandler);

    /**
     * Set the mapping function for URLs.
     *
     * @param backendUrlMapper Function<String, String>
     * @return HttpProxy
     */
    HttpProxy backendUrlMapper(Function<String, String> backendUrlMapper);

    /**
     * Set the Http client in the proxy.
     *
     * @param client client
     * @return HttpProxy
     */
    HttpProxy setClient(HttpClient client);

    /**
     * Providing the backend Response to be used in the BackendRequest.
     *
     * @param backendRequest   The backendRequest that calls to get a response
     * @param backendResponse  The response from the backend Server
     * @param frontendResponse The response to send to the frontend, once finished
     * @return BackendResponse
     */
    BackendResponse getBackendResponse(BackendRequestImpl backendRequest, HttpClientResponse backendResponse, HttpServerResponse frontendResponse);

    /**
     * Providing a BackendRequest.
     *
     * @param proxyHostName            proxyHostName
     * @param proxyHostPort            proxyHostPort
     * @param applicationJwtCookieName applicationJwtCookieName
     * @param client                   client
     * @param routingContext           routingContext
     * @param proxy                    proxy
     * @return BackendRequestImpl
     */
    BackendRequestImpl getBackendRequestImpl(String proxyHostName,
                                             String proxyHostPort,
                                             String applicationJwtCookieName,
                                             HttpClient client,
                                             RoutingContext routingContext,
                                             HttpProxy proxy);
}
