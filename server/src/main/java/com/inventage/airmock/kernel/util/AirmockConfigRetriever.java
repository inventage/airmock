package com.inventage.airmock.kernel.util;

import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ConfigRetriever utility class.
 */
public final class AirmockConfigRetriever {
    private static final Logger LOGGER = LoggerFactory.getLogger(AirmockConfigRetriever.class);
    private static final List<String> ADDITIONAL_CONFIG_FILES = new ArrayList<>();

    private AirmockConfigRetriever() {
    }

    /**
     * Registers an additional property file to be used as source of properties.
     * This feature is mainly used, when the application is started from within the IDE. If the given
     * file path points to a non existing file it is ignored.
     *
     * @param relativeFilePath a file system path to the property file, it starts in the working directory of the java process
     */
    public static void addAdditionalConfigFile(String relativeFilePath) {
        ADDITIONAL_CONFIG_FILES.add(relativeFilePath);
    }

    /**
     * returns a ConfigRetriever.
     * @param vertx vertx instance
     * @return ConfigRetriever
     */
    public static ConfigRetriever create(Vertx vertx) {
        return ConfigRetriever.create(vertx, getOrCreateOptions());
    }

    /**
     * Stores added later to the options, will override properties from prior stores.
     *
     * @return ConfigRetrieverOptions
     */
    private static ConfigRetrieverOptions getOrCreateOptions() {
        final ConfigRetrieverOptions options = new ConfigRetrieverOptions();

        for (String additionalConfigFile : ADDITIONAL_CONFIG_FILES) {
            final File configFile = new File(new File("."), additionalConfigFile);

            if (configFile.exists()) {
                LOGGER.info("getOrCreateOptions: reading config file '{}'", configFile.getAbsolutePath());
                final ConfigStoreOptions file = new ConfigStoreOptions()
                        .setType("file")
                        .setFormat("properties")
                        .setConfig(new JsonObject()
                                    .put("path", configFile.getAbsolutePath())
                                    .put("raw-data", true)
                        );
                options.addStore(file);
            }
        }

        final ConfigStoreOptions sys = new ConfigStoreOptions()
                .setType("sys")
                .setConfig(new JsonObject().put("raw-data", true));
        options.addStore(sys);

        final ConfigStoreOptions env = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject().put("raw-data", true));
        options.addStore(env);

        return options;

    }
}
