package com.inventage.airmock.kernel.proxy.internal;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.reactivex.core.Promise;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * BackendResponse.
 */
public interface BackendResponse {
    /**
     * Return the Headers from the frontendResponse.
     *
     * @return headers
     */
    MultiMap headers();

    /**
     * Prepare BackendResponse.
     *
     * @param routingContext         rc
     */
    void prepare(RoutingContext routingContext);

    /**
     * Send backendResponse to frontend.
     *
     * @param backendResponseHandler handler
     * @param routingContext         rc
     */
    void replyToFrontend(Handler<AsyncResult<Void>> backendResponseHandler, RoutingContext routingContext);

    /**
     * Stop Pump.
     */
    void stopPump();

    /**
     * A server error has occurred.
     *
     * @return boolean
     */
    boolean isServerError();

    /**
     * Stop.
     */
    void stop();

    /**
     * Call fail on the given Future, if the response from the backend was an server error.
     *
     * @param future         future
     * @param routingContext rc
     */
    void failIfServerError(Promise<Object> future, RoutingContext routingContext);
}
