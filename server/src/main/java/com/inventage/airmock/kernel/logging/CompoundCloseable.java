package com.inventage.airmock.kernel.logging;

import org.slf4j.MDC;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public final class CompoundCloseable implements Closeable {

    private List<MDC.MDCCloseable> closeables = new ArrayList<>();

    private CompoundCloseable() {
    }

    @Override
    public void close() {
        closeables.forEach(mdcCloseable -> mdcCloseable.close());
    }

    /**
     *
     * @param key MDC key
     * @param value MDC value
     * @return a new instance
     */
    public static CompoundCloseable create(String key, String value) {
        final CompoundCloseable instance = new CompoundCloseable();
        if (value != null) {
            instance.add(key, value);
        }
        return instance;
    }

    /**
     *
     * @param key MDC key
     * @param value MDC key
     * @return this for chaining
     */
    public CompoundCloseable add(String key, String value) {
        if (value != null) {
            closeables.add(MDC.putCloseable(key, value));
        }
        return this;
    }

}
