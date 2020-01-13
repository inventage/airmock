package com.inventage.airmock.kernel.logging;

import io.vertx.core.Handler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.reactivex.ext.web.RoutingContext;

public final class LoggerHandler implements Handler<RoutingContext> {

    // ---- Fields

    private final io.vertx.reactivex.ext.web.handler.LoggerHandler loggerHandler;

    // ---- Constructor

    private LoggerHandler(LoggerFormat loggerFormat) {
        loggerHandler = io.vertx.reactivex.ext.web.handler.LoggerHandler.newInstance(new AccessLogger(loggerFormat));
    }


    /**
     *
     * @param loggerFormat the format
     * @return a new instance
     */
    public static LoggerHandler create(LoggerFormat loggerFormat) {
        return new LoggerHandler(loggerFormat);
    }


    // ---- Methods

    @Override
    public void handle(RoutingContext rc) {
        loggerHandler.handle(rc);
    }
}
