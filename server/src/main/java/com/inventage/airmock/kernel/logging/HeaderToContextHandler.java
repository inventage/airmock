package com.inventage.airmock.kernel.logging;

import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.UUID;

public class HeaderToContextHandler implements Handler<RoutingContext> {

    // ---- Static

    public static final String X_REQUEST_ID = "X-REQUESTID";
    public static final String X_SESSION_ID = "X-SESSIONID";


    // ---- Methods

    @Override
    public void handle(RoutingContext rc) {
        headerToContext(rc);
        headerOrRandomToContext(rc);
        rc.next();
    }

    private void headerToContext(RoutingContext rc) {
        final String headerValue = rc.request().headers().get(X_SESSION_ID);
        if (!(headerValue == null || headerValue.trim().isEmpty())) {
            rc.put(X_SESSION_ID, headerValue);
        }
    }

    private void headerOrRandomToContext(RoutingContext rc) {
        String headerValue = rc.request().headers().get(X_REQUEST_ID);
        if (headerValue == null || headerValue.trim().isEmpty()) {
            headerValue = UUID.randomUUID().toString();
            rc.request().headers().set(X_REQUEST_ID, headerValue);
        }
        rc.put(X_REQUEST_ID, headerValue);
    }
}
