package com.inventage.airmock.kernel.logging;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import java.util.Arrays;
import java.util.function.Predicate;

import static org.slf4j.spi.LocationAwareLogger.*;
import static org.slf4j.spi.LocationAwareLogger.ERROR_INT;

/**
 * Delegates logging.
 */
public class AirmockLogDelegate implements LogDelegate {
    private static final String FQCN = io.vertx.core.logging.Logger.class.getCanonicalName();

    private final Logger logger;

    public AirmockLogDelegate(String name) {
        logger = LoggerFactory.getLogger(name);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void fatal(final Object message) {
        log(ERROR_INT, message);
    }

    @Override
    public void fatal(final Object message, final Throwable t) {
        log(ERROR_INT, message, t);
    }

    @Override
    public void error(final Object message) {
        log(ERROR_INT, message);
    }

    @Override
    public void error(Object message, Object... params) {
        log(ERROR_INT, message, null, params);
    }

    @Override
    public void error(final Object message, final Throwable t) {
        log(ERROR_INT, message, t);
    }

    @Override
    public void error(Object message, Throwable t, Object... params) {
        log(ERROR_INT, message, t, params);
    }

    @Override
    public void warn(final Object message) {
        log(WARN_INT, message);
    }

    @Override
    public void warn(Object message, Object... params) {
        log(WARN_INT, message, null, params);
    }

    @Override
    public void warn(final Object message, final Throwable t) {
        log(WARN_INT, message, t);
    }

    @Override
    public void warn(Object message, Throwable t, Object... params) {
        log(WARN_INT, message, t, params);
    }

    @Override
    public void info(final Object message) {
        log(INFO_INT, message);
    }

    @Override
    public void info(Object message, Object... params) {
        log(INFO_INT, message, null, params);
    }

    @Override
    public void info(final Object message, final Throwable t) {
        log(INFO_INT, message, t);
    }

    @Override
    public void info(Object message, Throwable t, Object... params) {
        log(INFO_INT, message, t, params);
    }

    @Override
    public void debug(final Object message) {
        log(DEBUG_INT, message);
    }

    @Override
    public void debug(final Object message, final Object... params) {
        log(DEBUG_INT, message, null, params);
    }

    @Override
    public void debug(final Object message, final Throwable t) {
        log(DEBUG_INT, message, t);
    }

    @Override
    public void debug(final Object message, final Throwable t, final Object... params) {
        log(DEBUG_INT, message, t, params);
    }

    @Override
    public void trace(final Object message) {
        log(TRACE_INT, message);
    }

    @Override
    public void trace(Object message, Object... params) {
        log(TRACE_INT, message, null, params);
    }

    @Override
    public void trace(final Object message, final Throwable t) {
        log(TRACE_INT, message, t);
    }

    @Override
    public void trace(Object message, Throwable t, Object... params) {
        log(TRACE_INT, message, t, params);
    }

    private void log(int level, Object message) {
        log(level, message, null);
    }

    private void log(int level, Object message, Throwable t) {
        log(level, message, t, (Object[]) null);
    }

    private void log(int level, Object message, Throwable t, Object... params) {
        final String msg = (message == null) ? "NULL" : message.toString();

        // We need to compute the right parameters.
        // If we have both parameters and an error, we need to build a new array [params, t]
        // If we don't have parameters, we need to build a new array [t]
        // If we don't have error, it's just params.
        Object[] parameters = params;
        if (params != null && t != null) {
            parameters = new Object[params.length + 1];
            System.arraycopy(params, 0, parameters, 0, params.length);
            parameters[params.length] = t;
        }
        else if (params == null && t != null) {
            parameters = new Object[]{t};
        }

        final RoutingContext[] routingContext = new RoutingContext[1];
        if (parameters != null) {
            Arrays.stream(parameters).filter(contextTypes()).forEach(context -> {
                if (context instanceof RoutingContext) {
                    routingContext[0] = (RoutingContext) context;
                }
                else if (context instanceof io.vertx.reactivex.ext.web.RoutingContext) {
                    routingContext[0] = ((io.vertx.reactivex.ext.web.RoutingContext) context).getDelegate();
                }
            });
        }

        try (CompoundCloseable closeable = CompoundCloseable.create(HeaderToContextHandler.X_REQUEST_ID, getRequestIdFromContext(routingContext[0]))
                .add(HeaderToContextHandler.X_SESSION_ID, getSessionIdFromContext(routingContext[0]))) {
            doLogging(level, msg, parameters, t);
        }
    }

    private Predicate<Object> contextTypes() {
        return parameter -> parameter instanceof RoutingContext || parameter instanceof io.vertx.reactivex.ext.web.RoutingContext;
    }

    private void doLogging(int level, String msg, Object[] parameters, Throwable t) {
        if (logger instanceof LocationAwareLogger) {
            final LocationAwareLogger l = (LocationAwareLogger) logger;
            l.log(null, FQCN, level, msg, parameters, t);
        }
        else {
            switch (level) {
                case TRACE_INT:
                    logger.trace(msg, parameters);
                    break;
                case DEBUG_INT:
                    logger.debug(msg, parameters);
                    break;
                case INFO_INT:
                    logger.info(msg, parameters);
                    break;
                case WARN_INT:
                    logger.warn(msg, parameters);
                    break;
                case ERROR_INT:
                    logger.error(msg, parameters);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown log level " + level);
            }
        }
    }

    @Override
    public Object unwrap() {
        return logger;
    }

    private String getRequestIdFromContext(RoutingContext routingContext) {
        return routingContext == null ? null : routingContext.get(HeaderToContextHandler.X_REQUEST_ID);
    }

    private String getSessionIdFromContext(RoutingContext routingContext) {
        return routingContext == null ? null : routingContext.get(HeaderToContextHandler.X_SESSION_ID);
    }


}
