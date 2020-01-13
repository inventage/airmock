package com.inventage.airmock.kernel.logging;

import io.vertx.core.spi.logging.LogDelegate;
import io.vertx.core.spi.logging.LogDelegateFactory;

/**
 * Factory for AirmockLogDelegates.
 */
public class AirmockLogDelegateFactory implements LogDelegateFactory {
    @Override
    public LogDelegate createDelegate(final String clazz) {
        return new AirmockLogDelegate(clazz);
    }
}
