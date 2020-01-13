package com.inventage.airmock.kernel.proxy.internal;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.reactivex.core.Promise;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class BackendResponseImpl implements BackendResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackendResponseImpl.class);

    protected final BackendRequestImpl backendRequest;
    protected final HttpClientResponse backendResponse;
    protected final HttpServerResponse frontendResponse;
    protected Function<ReadStream<Buffer>, ReadStream<Buffer>> bodyFilter = Function.identity();

    protected Pump responsePump;

    public BackendResponseImpl(BackendRequestImpl backendRequest, HttpClientResponse backendResponse, HttpServerResponse frontendResponse) {
        this.backendRequest = backendRequest;
        this.backendResponse = backendResponse;
        this.frontendResponse = frontendResponse;
    }

    public BackendResponseImpl bodyFilter(Function<ReadStream<Buffer>, ReadStream<Buffer>> filter) {
        LOGGER.debug("bodyFilter: response filter registered '{}'", filter);
        this.bodyFilter = filter;
        return this;
    }

    public MultiMap headers() {
        return frontendResponse.headers();
    }

    public void prepare(RoutingContext routingContext) {
        LOGGER.debug("prepare:", routingContext);

        frontendResponse.setStatusCode(backendResponse.statusCode());
        frontendResponse.setStatusMessage(backendResponse.statusMessage());

        // Date header
        setResponseDateHeader();

        // Suppress incorrect warning header
        List<String> warningHeaders = backendResponse.headers().getAll("warning");
        final String dateHeader = backendResponse.headers().get("date");
        if (warningHeaders.size() > 0) {
            warningHeaders = new ArrayList<>(warningHeaders);
            final Date dateInstant = ParseUtils.parseDateHeaderDate(dateHeader);
            final Iterator<String> i = warningHeaders.iterator();
            while (i.hasNext()) {
                final String warningHeader = i.next();
                final Date warningInstant = ParseUtils.parseWarningHeaderDate(warningHeader);
                if (warningInstant != null && dateInstant != null && !warningInstant.equals(dateInstant)) {
                    i.remove();
                }
            }
        }
        frontendResponse.putHeader("warning", warningHeaders);

        // Handle other headers
        backendResponse.headers().forEach(header -> {
            if (header.getKey().equalsIgnoreCase("date") || header.getKey().equalsIgnoreCase("warning")
                || header.getKey().equalsIgnoreCase("transfer-encoding")) {
                // Skip
            }
            else {
                frontendResponse.headers().add(header.getKey(), header.getValue());
            }
        });
    }

    public void replyToFrontend(Handler<AsyncResult<Void>> backendResponseHandler, RoutingContext routingContext) {
        LOGGER.debug("replyToFrontend: handler '{}'", backendResponseHandler, routingContext);

        // Determine chunked
        boolean chunked = false;
        for (String value : backendResponse.headers().getAll("transfer-encoding")) {
            if ("chunked".equals(value)) {
                chunked = true;
            }
            else {
                // frontendRequest = null;
                backendResponseHandler.handle(Future.succeededFuture());
                frontendResponse.setStatusCode(501).end(); // 501 = Not Implemented
                return;
            }
        }

        final boolean handleEntireBody = false;
        //
        if (handleEntireBody) {
            backendResponse.bodyHandler(buf -> {
                LOGGER.debug("handle entire body with but '{}'", buf, routingContext);
            });
        }
        else {
            backendResponse.exceptionHandler(err -> {
                final HttpServerRequest request = backendRequest.stop();
                if (request != null) {
                    request.response().close();
                    backendResponseHandler.handle(Future.failedFuture(err));
                }
                stopPump();
            });

            // Apply body filter
            final ReadStream<Buffer> bodyStream = bodyFilter.apply(backendResponse);

            if (chunked && backendRequest.isHttp11()) {
                frontendResponse.setChunked(true);
                responsePump = Pump.pump(bodyStream, frontendResponse);
                responsePump.start();
                bodyStream.endHandler(v -> {
                    backendRequest.stop();
                    backendResponseHandler.handle(Future.succeededFuture());
                    frontendResponse.end();
                });
            }
            else {
                final String contentLength = backendResponse.getHeader("content-length");
                if (contentLength != null) {
                    responsePump = Pump.pump(bodyStream, frontendResponse);
                    responsePump.start();
                    bodyStream.endHandler(v -> {
                        backendRequest.stop();
                        backendResponseHandler.handle(Future.succeededFuture());
                        frontendResponse.end();
                    });
                }
                else {
                    final Buffer body = Buffer.buffer();
                    bodyStream.handler(body::appendBuffer);
                    bodyStream.endHandler(v -> {
                        backendRequest.stop();
                        backendResponseHandler.handle(Future.succeededFuture());
                        frontendResponse.end(body);
                    });
                }
            }


        }

        backendResponse.resume();
    }

    public void stopPump() {
        if (responsePump != null) {
            responsePump.stop();
            responsePump = null;
        }
    }

    private void setResponseDateHeader() {
        final String dateHeader = backendResponse.headers().get("date");
        Date date = null;
        if (dateHeader == null) {
            final List<String> warningHeaders = backendResponse.headers().getAll("warning");
            if (warningHeaders.size() > 0) {
                for (String warningHeader : warningHeaders) {
                    date = ParseUtils.parseWarningHeaderDate(warningHeader);
                    if (date != null) {
                        break;
                    }
                }
            }
        }
        else {
            date = ParseUtils.parseWarningHeaderDate(dateHeader);
        }
        if (date == null) {
            date = new Date();
        }

        try {
            frontendResponse.putHeader("date", ParseUtils.formatHttpDate(date));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isServerError() {
        return backendResponse != null ? (backendResponse.statusCode() >= 500 && backendResponse.statusCode() < 600) : false;
    }

    public void stop() {
        backendRequest.reset();
    }

    @Override
    public void failIfServerError(Promise<Object> future, RoutingContext routingContext) {
        if (isServerError()) {
            LOGGER.warn("failIfServerError: backend returned status code '{}' with message '{}'.",
                backendResponse.statusCode(), backendResponse.statusMessage(), routingContext);
            future.fail("backend server error, status code=" + backendResponse.statusCode());
        }
    }
}
