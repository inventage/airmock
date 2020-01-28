package com.inventage.airmock.kernel.proxy.internal;

import com.inventage.airmock.kernel.proxy.HttpProxy;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.Promise;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.function.Function;

public class HttpProxyImpl implements HttpProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyImpl.class);

    protected final String proxyHostName;
    protected final String proxyHostPort;
    protected HttpClient client;
    protected String applicationJwtCookieName;
    protected CircuitBreaker circuitBreaker;
    protected Function<HttpServerRequest, Future<SocketAddress>> backendSelector = req -> Future.failedFuture("No backend configured");
    protected String backendProtocol;
    protected Function<String, String> backendUrlMapper = Function.identity();

    public HttpProxyImpl() {
        this(null, null);
    }

    public HttpProxyImpl(String proxyHostName, String proxyHostPort) {
        this.proxyHostName = proxyHostName;
        this.proxyHostPort = proxyHostPort;
    }

    @Override
    public HttpProxy backend(String protocol, String host, int port) {
        backendProtocol = protocol;
        final SocketAddressImpl address = new SocketAddressImpl(port, host);
        backendSelector = req -> Future.succeededFuture(address);
        return this;
    }

    @Override
    public HttpProxy backendSelector(Function<HttpServerRequest, Future<SocketAddress>> selector) {
        this.backendSelector = selector;
        return this;
    }

    @Override
    public HttpProxy circuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        return this;
    }

    @Override
    public HttpProxy applicationJwtCookieName(String applicationJwtCookieName) {
        this.applicationJwtCookieName = applicationJwtCookieName;
        return this;

    }

    @Override
    public void delegate(RoutingContext routingContext) {
        delegateToBackend(routingContext, null);
    }

    @Override
    public void delegate(RoutingContext routingContext, Handler<RoutingContext> fallbackHandler) {
        delegateToBackend(routingContext, fallbackHandler);
    }

    /**
     * Delegate request to the backend.
     *
     * @param routingContext  request
     * @param fallbackHandler fallbackHandler
     */
    protected void delegateToBackend(RoutingContext routingContext, Handler<RoutingContext> fallbackHandler) {
        routingContext.request().pause();
        final Future<SocketAddress> fut = backendSelector.apply(routingContext.request().getDelegate());
        fut.setHandler(ar -> {
            if (ar.succeeded()) {
                final SocketAddress backend = ar.result();
                LOGGER.debug("delegateToBackend: forwarding to backend '{}://{}'", backendProtocol, backend, routingContext);
                final BackendRequestImpl backendRequest = getBackendRequestImpl(proxyHostName,
                    proxyHostPort,
                    applicationJwtCookieName,
                    client,
                    routingContext,
                    this);
                backendRequest.backendUrlMapper(this.backendUrlMapper);
                if (circuitBreaker != null) {
                    circuitBreaker.execute(future -> {
                        backendRequest.send(backend,
                            asyncResult -> onResponseReceivedFromBackend(asyncResult, future, routingContext));
                    }).setHandler(cc -> {
                        if (cc.succeeded()) {
                            LOGGER.debug("onResult: circuitBreaker executed with success", routingContext);
                        }
                        else {
                            LOGGER.error("onResult: backend request '{}' returned with failure '{}', current state is '{}' (failures={})",
                                backendRequest.backendRequest != null ? backendRequest.backendRequest.absoluteURI() : "",
                                cc.cause().getMessage(), circuitBreaker.state(), circuitBreaker.failureCount(), routingContext);
                            backendRequest.reset();
                            if (fallbackHandler != null) {
                                fallbackHandler.handle(routingContext);
                            }
                            else {
                                routingContext.request().response().end();
                            }
                        }

                    });
                }
                else {
                    backendRequest.send(backend, asyncResult -> onBackendResponseReceived(asyncResult, routingContext));
                }
            }
            else {
                LOGGER.error("delegateToBackend: backend address not resolveable by selector '{}'", ar.cause().getMessage(), routingContext);
                routingContext.request().resume();
                routingContext.request().response().setStatusCode(500).end();
            }
        });
    }

    /**
     * Callback when backend request was sent.
     *
     * @param asyncResult    asyncResult
     * @param routingContext routingContext
     */
    protected void onBackendResponseReceived(AsyncResult<BackendResponse> asyncResult, RoutingContext routingContext) {
        if (asyncResult.succeeded()) {
            LOGGER.debug("onBackendResponseReceived: request to backend successful", routingContext);
            final BackendResponse backendResponse = asyncResult.result();
            backendResponse.replyToFrontend(asyncResult2 -> onResponseSentToFrontend(asyncResult2, routingContext), routingContext);
        }
        else {
            LOGGER.error("onBackendResponseReceived: request to backend failed '{}'",
                asyncResult.cause(), asyncResult.cause() != null ? asyncResult.cause().getMessage() : "Unkown error", routingContext);
        }
    }

    /**
     * Callback when backend request was sent.
     *
     * @param asyncResult    asyncResult
     * @param future         future
     * @param routingContext routingContext
     */
    protected void onResponseReceivedFromBackend(AsyncResult<BackendResponse> asyncResult, Promise<Object> future, RoutingContext routingContext) {
        if (asyncResult.succeeded()) {
            final BackendResponse backendResponse = (BackendResponse) asyncResult.result();
            LOGGER.debug("onResponseReceivedFromBackend: response from backend successful", routingContext);
            if (backendResponse.isServerError()) {
                backendResponse.failIfServerError(future, routingContext);
            }
            else {
                // proxyResp.bodyFilter(createFilter(proxyResp.backendResponse()));
                backendResponse.replyToFrontend(asyncResult2 -> onResponseSentToFrontend(asyncResult2, routingContext), routingContext);
                future.complete(backendResponse);
            }
        }
        else {
            if (asyncResult.cause() != null) {
                LOGGER.error("onResponseReceivedFromBackend: response from backend failed with exception '{}'",
                    asyncResult.cause() != null ? asyncResult.cause().getMessage() : "Unkown error", routingContext);
                future.fail(asyncResult.cause());
            }
            else {
                LOGGER.error("onResponseReceivedFromBackend: response from backend failed '{}'",
                    asyncResult.cause() != null ? asyncResult.cause().getMessage() : "Unkown error", routingContext);
                future.fail("Request to backend failed with unkown error");
            }
        }
    }

    /**
     * Callback when frontend response was sent.
     *
     * @param asyncResult    the result from the sending the result back to the client
     * @param routingContext routingContext
     */
    protected void onResponseSentToFrontend(AsyncResult<Void> asyncResult, RoutingContext routingContext) {
        if (asyncResult.succeeded()) {
            LOGGER.debug("handleFrontendResponse: response to frontend has been sent successfuly", routingContext);
        }
        else {
            LOGGER.error("handleFrontendResponse: response to frontend has failed '{}'",
                asyncResult.cause() != null ? asyncResult.cause().getMessage() : "Unkown error", routingContext);
        }
    }

    @Override
    public HttpProxy backendUrlMapper(Function<String, String> backendUrlMapper) {
        this.backendUrlMapper = backendUrlMapper;
        return this;
    }

    @Override
    public HttpProxy setClient(HttpClient client) {
        this.client = client;
        return this;
    }

    @Override
    public BackendResponse getBackendResponse(BackendRequestImpl backendRequest, HttpClientResponse backendResponse, HttpServerResponse frontendResponse) {
        return new BackendResponseImpl(backendRequest, backendResponse, frontendResponse);
    }

    @Override
    public BackendRequestImpl getBackendRequestImpl(String proxyHostName,
                                                    String proxyHostPort,
                                                    String applicationJwtCookieName,
                                                    HttpClient client,
                                                    RoutingContext routingContext,
                                                    HttpProxy proxy) {
        return new BackendRequestImpl(proxyHostName, proxyHostPort, applicationJwtCookieName, client, routingContext, this);
    }
}
