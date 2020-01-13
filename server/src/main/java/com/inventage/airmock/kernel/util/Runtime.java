package com.inventage.airmock.kernel.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * helper class.
 */
public final class Runtime {
    public static final String DEVELOPMENT_MODE_KEY = "development";
    public static final String VERTICLE_INSTANCES_KEY = "verticle.instances";

    private static final Logger LOGGER = LoggerFactory.getLogger(Runtime.class);

    private Runtime() {
    }

    /**
     * Development mode can be activated by setting the system property 'development' (-Ddevelopment).
     *
     * @return true if development mode is activated
     */
    public static boolean isDevelopment() {
        final boolean envValue = toBoolean(System.getenv(DEVELOPMENT_MODE_KEY));
        final boolean propValue = toBoolean(System.getProperty(DEVELOPMENT_MODE_KEY));
        if (envValue || propValue) {
            LOGGER.warn("Development mode is active");
            return true;
        }
        return false;
    }

    /**
     * returns the number of verticles.
     *
     * @return number
     */
    public static String numberOfVerticleInstances() {
        try {
            final int envValue = toInt(System.getenv(VERTICLE_INSTANCES_KEY));
            LOGGER.warn("numberOfVerticleInstances: from environment is '{}'", envValue);
            return String.valueOf(envValue);
        }
        catch (Exception e) {
            //
        }
        try {
            final int propvalue = toInt(System.getProperty(VERTICLE_INSTANCES_KEY));
            LOGGER.warn("numberOfVerticleInstances: from system property is '{}'", propvalue);
            return String.valueOf(propvalue);
        }
        catch (Exception e) {
            //
        }
        final int defaultNumber = 1;
        LOGGER.warn("numberOfVerticleInstances: from default is '{}'", defaultNumber);
        return String.valueOf(defaultNumber);
    }

    private static int toInt(String property) throws NumberFormatException {
        return Integer.parseInt(property);
    }

    private static boolean toBoolean(String property) {
        return Boolean.parseBoolean(property);
    }

}
