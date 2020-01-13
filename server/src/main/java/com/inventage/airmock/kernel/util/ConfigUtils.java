package com.inventage.airmock.kernel.util;

import io.vertx.core.json.JsonObject;
import org.apache.commons.text.StringSubstitutor;

public final class ConfigUtils {

    private JsonObject config;

    public ConfigUtils(JsonObject config) {
        this.config = config;
    }

    /**
     * Gets the value of the given key of the config as an Integer.
     * @param config The config to get the value from
     * @param key the key to get the value from
     * @return the value as an integer or null if it doesn't exist.
     */
    public static Integer getInteger(JsonObject config, String key) {
        final String stringValue = config.getString(key);
        if (stringValue == null) {
            return null;
        }
        return new Double(stringValue).intValue();
    }

    /**
     * Gets the value of the given key of the config as an Integer.
     * @param config The config to get the value from
     * @param key the key to get the value from
     * @param defaultValue value to be returned if no value is found by the given key
     * @return the value as an integer or null if it doesn't exist.
     */
    public static Integer getInteger(JsonObject config, String key, int defaultValue) {
        final Integer value = getInteger(config, key);
        if (value == null) {
            return defaultValue;
        }
        return new Double(value).intValue();
    }

    /**
     * Replace any environment variables found in the input with the value of the corresponding environment variable.
     * @param config The config to get the replacement values from.
     * @param input The input string
     * @return The input string with replaced variables
     */
    public static String replaceEnvVariables(JsonObject config, String input) {
        return StringSubstitutor.replace(input, config.getMap());
    }

    /**
     * Gets the value of the given key of the config as an Integer.
     * @param key the key to get the value from
     * @return the value as an integer or null if it doesn't exist.
     */
    public Integer getInteger(String key) {
        return getInteger(this.config, key);
    }


    /**
     * Replace any environment variables found in the input with the value of the corresponding environment variable.
     * @param input The input string
     * @return The input string with replaced variables
     */
    public String replaceEnvVariables(String input) {
        return replaceEnvVariables(this.config, input);
    }

}
