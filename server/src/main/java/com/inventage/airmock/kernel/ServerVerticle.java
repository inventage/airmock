package com.inventage.airmock.kernel;


import com.inventage.airmock.Applications;
import com.inventage.airmock.SimpleRouterConfigurator;
import com.inventage.airmock.kernel.util.AirmockConfigRetriever;
import com.inventage.airmock.kernel.util.ConfigUtils;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.dropwizard.MetricsService;
import io.vertx.reactivex.ext.web.Router;

import java.util.List;

/**
 * Verticle to handle all applications within airmock.
 */
public class ServerVerticle extends AbstractVerticle {

    public static final String CONFIG_PREFIX = ServerVerticle.class.getName() + ".";
    public static final String HTTP_PORT = "http-port";
    public static final String HTTPS_PORT = "https-port";
    public static final String HTTPS_KEY_STORE_PATH = "https-key-store-path";
    public static final String HTTPS_KEY_STORE_PASSWORD = "https-key-store-password";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerVerticle.class);

    @Override
    public void start() throws Exception {
        LOGGER.debug("starting.....");
        super.start();

        final ConfigRetriever retriever = AirmockConfigRetriever.create(vertx);
        retriever.getConfig(asyncResult -> {
            try {
                final JsonObject config = asyncResult.result();

                final List<AirmockApplication> applications = new Applications(config).create();
                applications.forEach(this::deploy);

                final Router router = Router.router(vertx);
                new SimpleRouterConfigurator().configure(router, vertx, config, applications);

                listenForHttp(router, config);
                listenForHttps(router, config);
                LOGGER.debug("started");
            }
            catch (Exception e) {
                shutdownOnStartupFailure(e);
            }
        });
    }

    private void listenForHttp(Router router, JsonObject config) {
        final int serverPort = ConfigUtils.getInteger(config, CONFIG_PREFIX + HTTP_PORT, 10000);
        if (serverPort > 0) {
            final HttpServerOptions options = new HttpServerOptions()
                    .setMaxHeaderSize(1024 * 20);
            vertx.createHttpServer(options).requestHandler(router).listen(serverPort);
        }
    }

    private void listenForHttps(Router router, JsonObject config) {
        final int serverPort = ConfigUtils.getInteger(config, CONFIG_PREFIX + HTTPS_PORT, -1);
        if (serverPort > 0) {
            final HttpServerOptions options = new HttpServerOptions()
                    .setMaxHeaderSize(1024 * 20)
                    .setSsl(true)
                    .setKeyStoreOptions(getJksOptions(config));
            vertx.createHttpServer(options).requestHandler(router).listen(serverPort);
        }
    }

    private JksOptions getJksOptions(JsonObject config) {
        final String keyStorePath = config.getString(CONFIG_PREFIX + HTTPS_KEY_STORE_PATH);
        if (keyStorePath == null || keyStorePath.isEmpty()) {
            throw new IllegalStateException("When using https the path to the key store must be configured by variable: '" +
                    CONFIG_PREFIX + "https-key-store-path'. To disable the https port configuration use '-1' as port.");
        }
        return new JksOptions()
                .setPath(keyStorePath)
                .setPassword(config.getString(CONFIG_PREFIX + HTTPS_KEY_STORE_PASSWORD));
    }

    private void deploy(AirmockApplication application) {
        vertx.deployVerticle(application.getApplicationVerticle());
    }

    private void shutdownOnStartupFailure(Throwable throwable) {
        if (throwable instanceof IllegalArgumentException) {
            LOGGER.error("shutdownOnStartupFailure: airmock will shut down because '{}'", throwable.getMessage());
        }
        else {
            LOGGER.error("shutdownOnStartupFailure: airmock will shut down because '{}'", throwable.getMessage(), throwable);
        }
        vertx.close();
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        if (vertx.isMetricsEnabled()) {
            final MetricsService metricsService = MetricsService.create(vertx);
            final JsonObject metricsSnapshot = metricsService.getMetricsSnapshot(vertx);
            LOGGER.info("metrics: {}", metricsSnapshot);
        }
        super.stop();

        super.stop(stopFuture);
    }
}
