package com.inventage.airmock.kernel.logging;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;

/**
 * Own implementation which adds the RoutingContext as additional parameter to the LogDelegate.
 */
public class AccessLogger implements LoggerHandler {

    private final io.vertx.core.logging.Logger logger = LoggerFactory.getLogger(this.getClass());

    /** log before request or after
     */
    private final boolean immediate;

    /** the current choosen format
     */
    private final LoggerFormat format;

    public AccessLogger(boolean immediate, LoggerFormat format) {
        this.immediate = immediate;
        this.format = format;
    }

    public AccessLogger(LoggerFormat format) {
        this(false, format);
    }

    private String getClientAddress(SocketAddress inetSocketAddress) {
        if (inetSocketAddress == null) {
            return null;
        }
        return inetSocketAddress.host();
    }

    private void log(RoutingContext context, long timestamp, String remoteClient, HttpVersion version, HttpMethod method, String uri) {
        final HttpServerRequest request = context.request();
        long contentLength = 0;
        if (immediate) {
            final Object obj = request.headers().get("content-length");
            if (obj != null) {
                try {
                    contentLength = Long.parseLong(obj.toString());
                }
                catch (NumberFormatException e) {
                    // ignore it and continue
                    contentLength = 0;
                }
            }
        }
        else {
            contentLength  = request.response().bytesWritten();
        }
        String versionFormatted = "-";
        switch (version){
            case HTTP_1_0:
                versionFormatted = "HTTP/1.0";
                break;
            case HTTP_1_1:
                versionFormatted = "HTTP/1.1";
                break;
            default:
                versionFormatted = "HTTP/1.1";
        }

        final int status = request.response().getStatusCode();
        String message = null;

        switch (format) {
            case DEFAULT:
                message = defaultMessage(request, timestamp, remoteClient, method, uri, versionFormatted, status, contentLength);
                break;
            case SHORT:
                message = String.format("out: %s - %s %s %s %d %d - %d ms",
                        remoteClient,
                        method,
                        uri,
                        versionFormatted,
                        status,
                        contentLength,
                        System.currentTimeMillis() - timestamp);
                break;
            case TINY:
                message = String.format("out: %s %s %d %d - %d ms",
                        method,
                        uri,
                        status,
                        contentLength,
                        System.currentTimeMillis() - timestamp);
                break;
            default:
                message = defaultMessage(request, timestamp, remoteClient, method, uri, versionFormatted, status, contentLength);
        }
        doLog(status, message, context);
    }

    private String defaultMessage(HttpServerRequest request, long timestamp, String remoteClient, HttpMethod method, String uri,
                                  String versionFormatted, int status, long contentLength) {
        String referrer = request.headers().get("referrer");
        String userAgent = request.headers().get("user-agent");
        referrer = referrer == null ? "-" : referrer;
        userAgent = userAgent == null ? "-" : userAgent;

        return String.format("out: %s - - \"%s %s %s\" %d %d \"%s\" \"%s\" - %d ms",
                remoteClient,
                method,
                uri,
                versionFormatted,
                status,
                contentLength,
                referrer,
                userAgent,
                System.currentTimeMillis() - timestamp);
    }

    /**
     *
     * @param status status code of the response
     * @param message message to be written
     * @param routingContext context
     */
    protected void doLog(int status, String message, RoutingContext routingContext) {
        logger.info(message, routingContext);
    }

    @Override
    public void handle(RoutingContext context) {
        // common logging data
        final long timestamp = System.currentTimeMillis();
        final String remoteClient = getClientAddress(context.request().remoteAddress());
        final HttpMethod method = context.request().method();
        final String uri = context.request().uri();
        final HttpVersion version = context.request().version();
        logger.debug("in: {} - - \"{} {} {}\"", remoteClient, method, uri, version, context);

        if (immediate) {
            log(context, timestamp, remoteClient, version, method, uri);
        }
        else {
            context.addBodyEndHandler(v -> log(context, timestamp, remoteClient, version, method, uri));
        }

        context.next();

    }
}
