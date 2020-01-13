package com.inventage.airmock.kernel.proxy.internal;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.inventage.airmock.kernel.Constants;
import com.inventage.airmock.kernel.proxy.HttpProxy;
import com.inventage.airmock.kernel.route.RoutingContextUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.reactivex.ext.web.Cookie;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Generic BackendRequest.
 */
public class BackendRequestImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackendRequestImpl.class);

    protected final String proxyHostName;
    protected final String proxyHostPort;
    protected final HttpClient httpClient;
    protected final String applicationJwtCookieName;
    protected HttpProxy proxy;

    protected RoutingContext routingContext;
    protected HttpServerRequest frontendRequest;
    protected HttpClientRequest backendRequest;
    protected BackendResponse backendResponse;

    protected Function<HttpServerRequest, HttpClientRequest> requestProvider;
    protected Function<String, String> backendUrlMapper = Function.identity();
    protected Function<ReadStream<Buffer>, ReadStream<Buffer>> bodyFilter = Function.identity();
    protected CookieFilter2 cookieFilter = new CookieFilter2("_access_token");

    protected MultiMap headers;

    protected Pump requestPump;

    public BackendRequestImpl(String proxyHostName,
                              String proxyHostPort,
                              String applicationJwtCookieName,
                              HttpClient client,
                              RoutingContext routingContext,
                              HttpProxy proxy) {
        if (routingContext == null) {
            throw new NullPointerException();
        }
        this.proxyHostName = proxyHostName;
        this.proxyHostPort = proxyHostPort;
        this.httpClient = client;
        this.applicationJwtCookieName = applicationJwtCookieName;
        this.routingContext = routingContext;
        this.frontendRequest = routingContext.request().getDelegate();
        this.proxy = proxy;
    }

    /**
     * Set backendUrlMapper.
     *
     * @param mapper mapper
     * @return this
     */
    public BackendRequestImpl backendUrlMapper(Function<String, String> mapper) {
        backendUrlMapper = mapper;
        return this;
    }

    /**
     * Returns true if http version is 1.1.
     *
     * @return boolean
     */
    public boolean isHttp11() {
        return frontendRequest.version() == HttpVersion.HTTP_1_1;
    }

    /**
     * Send a request to the backend specified.
     *
     * @param backend                backend
     * @param backendResponseHandler responseHandler
     */
    public void send(SocketAddress backend, Handler<AsyncResult<BackendResponse>> backendResponseHandler) {
        LOGGER.debug("send: forwarding '{}' to '{}'", frontendRequest.uri(), backend, routingContext);

        // Sanity check 1
        try {
            frontendRequest.version();
        }
        catch (IllegalStateException e) {
            // Sends 501
            frontendRequest.resume();
            backendResponseHandler.handle(Future.failedFuture(e));
            return;
        }

        // Create backend request
        if (requestProvider != null) {
            backendRequest = requestProvider.apply(frontendRequest);
        }
        else {
            final HttpMethod method = frontendRequest.method();
            backendRequest = httpClient.request(method, backend.port(), backend.host(), backendUrlMapper.apply(frontendRequest.uri()));
            if (method == HttpMethod.OTHER) {
                backendRequest.setRawMethod(frontendRequest.rawMethod());
            }
        }

        // Encoding check
        final List<String> te = frontendRequest.headers().getAll("transfer-encoding");
        if (te != null) {
            for (String val : te) {
                if ("chunked".equals(val)) {
                    backendRequest.setChunked(true);
                }
                else {
                    frontendRequest.resume().response().setStatusCode(400).end();
                    // I think we should make a call to completion handler at
                    // this point - it does not seem to be tested
                    return;
                }
            }
        }

        // Set headers
        propagateHeaders(routingContext, frontendRequest, backendRequest);

        // Apply body filter
        final ReadStream<Buffer> bodyStream = bodyFilter.apply(frontendRequest);

        bodyStream.endHandler(v -> {
            requestPump = null;
            backendRequest.end();
        });
        requestPump = Pump.pump(bodyStream, backendRequest); // frontend -> backend
        backendRequest.handler(resp -> onDataAvailableFromBackend(resp, backendResponseHandler));
        backendRequest.exceptionHandler(err -> onExceptionReceivedFromBackend(err, backendResponseHandler));
        this.frontendRequest.response().endHandler(v -> {
            if (stop() != null) {
                backendRequest.reset();
            }
        });
        bodyStream.resume(); // request is sent
        LOGGER.debug("send: start request pumping", routingContext);
        requestPump.start();
    }

    /**
     * Set header to the backend request.
     *
     * @param routingContext the current routing context
     * @param frontendRequest the incoming request
     * @param backendRequest the outgoing request
     */
    protected void propagateHeaders(RoutingContext routingContext, HttpServerRequest frontendRequest, HttpClientRequest backendRequest) {
        if (headers != null) {
            // Handler specially the host header
            final String host = headers.get("host");
            if (host != null) {
                headers.remove("host");
                backendRequest.setHost(host);
            }
            backendRequest.headers().setAll(headers);
        }
        else {
            copyHeaders(backendRequest.headers());
        }
        // inform backend about reverse proxy (https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Host)
        if (proxyHostName != null) {
            addHeaderIfNotAlready(backendRequest, "X-Forwarded-Host", proxyHostName);
        }
        if (proxyHostPort != null) {
            replaceHeader(backendRequest, "X-Forwarded-Port", proxyHostPort);
            //replaceHeader(backendRequest,"Host",proxyHostName + ":" + proxyHostPort);
        }
        propagateAccessToken(routingContext, backendRequest);
        propagateFilteredCookies(routingContext.cookies(), backendRequest);
    }

    /**
     * Handler if an exception was received from the backend.
     *
     * @param throwable              error
     * @param backendResponseHandler handler
     */
    protected void onExceptionReceivedFromBackend(Throwable throwable, Handler<AsyncResult<BackendResponse>> backendResponseHandler) {
        LOGGER.error("onExceptionReceivedFromBackend: '{}' for '{}'",
            throwable, throwable.getMessage(), routingContext.request().absoluteURI(), routingContext);
        backendResponseHandler.handle(Future.failedFuture(throwable));
    }

    /**
     * Stop pumping.
     *
     * @return HttpServerRequest
     */
    public HttpServerRequest stop() {
        LOGGER.debug("stop: pumps", routingContext);
        final HttpServerRequest request = frontendRequest;
        if (request != null) {
            // Abrupt close
            frontendRequest = null;
            if (requestPump != null) {
                requestPump.stop();
                requestPump = null;
            }
            if (this.backendResponse != null) {
                this.backendResponse.stopPump();
            }
            return request;
        }
        return null;
    }

    /**
     * Handler to be called when response is available.
     *
     * @param backendResponse        backendResponse
     * @param backendResponseHandler handler
     */
    protected void onDataAvailableFromBackend(HttpClientResponse backendResponse, Handler<AsyncResult<BackendResponse>> backendResponseHandler) {
        LOGGER.debug("onDataAvailableFromBackend: backend '{}' responded with status code '{}'",
            backendRequest.absoluteURI(), backendResponse.statusCode(), routingContext);

        if (frontendRequest == null) {
            return;
        }
        backendResponse.pause();
        final HttpServerResponse frontendResponse = frontendRequest.response();
        if (!frontendResponse.ended()) {
            final BackendResponse response = proxy.getBackendResponse(this, backendResponse, frontendResponse);
            response.prepare(routingContext);
            backendResponseHandler.handle(Future.succeededFuture(response));
        }
        else {
            LOGGER.debug("onDataAvailableFromBackend: frontend response already ended, backend response is not sent deliverd", routingContext);
        }
    }

    private void propagateFilteredCookies(Set<Cookie> cookies, HttpClientRequest backendRequest) {
        final StringBuffer cookiesHeader = new StringBuffer();
        cookies.stream().filter(cookieFilter::test).map(cookie -> toHeader(cookie)).forEach(cookieHeader -> {
            cookiesHeader.append(cookieHeader);
        });
        if (cookiesHeader.length() > 0) {
            backendRequest.headers().add("Cookie", cookiesHeader.toString());
        }
    }

    private Object toHeader(Cookie cookie) {
        final StringBuffer cookieHeader = new StringBuffer();
        cookieHeader.append(cookie.getName());
        cookieHeader.append("=");
        cookieHeader.append(cookie.getValue());
        cookieHeader.append("; ");
        return cookieHeader.toString();
    }

    /**
     * Copy host and cookie header to destination.
     *
     * @param to destination
     */
    protected void copyHeaders(MultiMap to) {
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

    private void propagateAccessToken(RoutingContext routingContext, HttpClientRequest backendRequest) {
        final String accessTokenEncoded = RoutingContextUtils.getAccessTokenEncoded(routingContext, applicationJwtCookieName);
        if (accessTokenEncoded != null) {
            LOGGER.debug("propagateAccessToken: adding Authorization header 'Bearer {}...'", accessTokenEncoded.substring(0, 10), routingContext);
            backendRequest.headers().add("Authorization", "Bearer " + accessTokenEncoded);
        }
        final DecodedJWT portalJWT = RoutingContextUtils.getJWT(routingContext);
        if (portalJWT != null || hasCookieAuthenticationLevel(routingContext)) {
            addHeaderIfNotAlready(backendRequest, "X-PORTAL-AUTHENTICATED", "true");
        }
    }

    private boolean hasCookieAuthenticationLevel(RoutingContext routingContext) {
        return routingContext.getCookie(Constants.COOKIE_NAME_AUTHENTICATION_LEVEL) != null;
    }

    /**
     * Add header to backendRequest if it was not present already.
     *
     * @param backendRequest backendRequest
     * @param header         headerName
     * @param value          headerValue
     */
    protected void addHeaderIfNotAlready(HttpClientRequest backendRequest, String header, String value) {
        if (!backendRequest.headers().contains(header)) {
            backendRequest.putHeader(header, value);
            LOGGER.debug("addHeaderIfNotAlready: header added to backend request '{}': '{}'", header, backendRequest.headers().get(header), routingContext);
        }
    }

    /**
     * Replace a headerValue in the backendRequest.
     *
     * @param backendRequest backendRequest
     * @param header         headerName
     * @param value          headerValue
     */
    protected void replaceHeader(HttpClientRequest backendRequest, String header, String value) {
        backendRequest.headers().remove(header);
        backendRequest.putHeader(header, value);
        LOGGER.debug("replaceHeader: header added to backend request '{}': '{}'", header, backendRequest.headers().get(header), routingContext);

    }

    /**
     * Reset backendRequest.
     */
    public void reset() {
        if (backendRequest != null) {
            backendRequest.reset();
        }
    }
}
