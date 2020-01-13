package com.inventage.airmock.waf.mapping;

import com.inventage.airmock.waf.AirmockHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.reactivex.ext.web.RoutingContext;

public class DenyAllMapping implements Mapping {
    @Override
    public String getName() {
        return "DENY_ALL_MAPPING";
    }

    /**
     * Should never be called.
     * @return Never returns because it throws an exception.
     */
    public String getContextRoot() {
        throw new IllegalStateException("Deny all mapping must be used.");
    }

    @Override
    public boolean isMatching(String urlPath) {
        return true;
    }

    @Override
    public void endRequest(RoutingContext routingContext) {
        routingContext.response().setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end();
    }

    @Override
    public Single<Boolean> canProceed(RoutingContext routingContext, AirmockHandler airmockHandler) {
        return Single.just(false);
    }

    @Override
    public void logout(RoutingContext routingContext) {
    }

    @Override
    public String backendHost() {
        return null;
    }

    @Override
    public int backendPort() {
        return 0;
    }

}
