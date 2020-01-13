package com.inventage.airmock;

import com.inventage.airmock.kernel.ServerVerticle;
import com.inventage.airmock.kernel.logging.AirmockLogDelegateFactory;
import com.inventage.airmock.kernel.util.Runtime;
import io.vertx.core.Launcher;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;

/**
 * Class used to launch the Airmock Server.
 */
public final class AirmockLauncher extends Launcher {

    private static Logger LOGGER;

    private AirmockLauncher() {
    }

    /**
     * main method to start the server.
     *
     * @param args startup arguments
     */
    public static void main(String[] args) {
        // using SLF4J instead of JUL (=vert.x default)
        System.setProperty("vertx.logger-delegate-factory-class-name", AirmockLogDelegateFactory.class.getName());
        LOGGER = LoggerFactory.getLogger(AirmockLauncher.class);
        LOGGER.info("main: Server starting....");

        System.setProperty("vertx.metrics.options.enabled", "true"); // enable metrics
        System.setProperty("vertx.metrics.options.registryName", "AirmockMetrics"); // name of the metrics registry
        System.setProperty("vertx.options.maxWorkerExecuteTime", "240000000000"); // increase timeout for worker execution to 2 min

        if (Runtime.isDevelopment()) {
            // increase the max event loop time to 10 min (default is 2000000000 ns = 2s) to omit thread blocking warnings
            System.setProperty("vertx.options.maxEventLoopExecuteTime", "600000000000");
        }
        final String[] arguments = new String[]{"run", ServerVerticle.class.getName(), "--instances", Runtime.numberOfVerticleInstances()};
        new AirmockLauncher().dispatch(arguments);
        LOGGER.info("main: AirmockLauncher started.");
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        LOGGER.info("beforeStartingVertx...");
        super.beforeStartingVertx(options);
        setMetricsOptions(options);

        setWorkerOptions(options);
    }

    private void setWorkerOptions(VertxOptions options) {
        options.setWorkerPoolSize(1);
        options.setMaxWorkerExecuteTime(3000);

        // Log warnings with stack trace if blocking time > 5 min
        options.setWarningExceptionTime(1000 * 60 * 5);
        // check if thread is blocked every 30 secs
        options.setBlockedThreadCheckInterval(1000 * 30);
    }

    private void setMetricsOptions(VertxOptions options) {
        options.setMetricsOptions(new DropwizardMetricsOptions()
                .setEnabled(true)
                .addMonitoredHttpClientEndpoint(
                        new Match().setType(MatchType.REGEX).setValue(".*")));
    }

}
