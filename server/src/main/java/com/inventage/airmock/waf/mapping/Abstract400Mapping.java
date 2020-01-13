package com.inventage.airmock.waf.mapping;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.reactivex.ext.web.RoutingContext;

public abstract class Abstract400Mapping extends DefaultMapping {
    @Override
    public void endRequest(RoutingContext routingContext) {
        routingContext.response().setStatusCode(getResponseStatus().code()).end();
    }

    abstract HttpResponseStatus getResponseStatus();
}
