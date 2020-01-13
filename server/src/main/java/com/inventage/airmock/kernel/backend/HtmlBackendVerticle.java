package com.inventage.airmock.kernel.backend;

import com.inventage.airmock.kernel.RouteProvider;
import com.inventage.airmock.kernel.ServerVerticle;
import com.inventage.airmock.kernel.proxy.HttpProxy;
import com.inventage.airmock.kernel.util.AirmockConfigRetriever;
import com.inventage.airmock.waf.proxy.AirmockHttpProxy;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.function.Function;

import static com.inventage.airmock.kernel.util.ConfigUtils.getInteger;

public class HtmlBackendVerticle extends AbstractVerticle implements RouteProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlBackendVerticle.class);

    private JsonObject config;
    private String backendName;
    private HttpProxy proxy;

    private Function<String, String> backendUrlMapper = Function.identity();

    private String backendHost;
    private Integer backendPort;
    private String xForwardedHost;
    private Integer xForwardedPort;

    public HtmlBackendVerticle(String backendName) {
        this.backendName = backendName;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        LOGGER.info("start: ...");

        final ConfigRetriever retriever = AirmockConfigRetriever.create(vertx);
        retriever.getConfig(asyncResult -> init(asyncResult, startFuture));
    }

    /**
     * Callback when the configuration is available.
     *
     * @param asyncResult asyncResult
     * @param startFuture startFuture
     */
    protected void init(AsyncResult<JsonObject> asyncResult, Future<Void> startFuture) {
        if (asyncResult.succeeded()) {
            config = asyncResult.result();

            this.backendPort = getInteger(config, getConfigPrefix() + "backend-port");
            this.backendHost = config.getString(getConfigPrefix() + "backend-host");

            this.xForwardedHost = config.getString(HtmlBackendVerticle.class.getName() + ".x-forwarded-host");
            if (this.xForwardedHost == null) {
                this.xForwardedHost = "localhost";
            }
            this.xForwardedPort = getInteger(config, HtmlBackendVerticle.class.getName() + ".x-forwarded-port");
            if (this.xForwardedPort == null) {
                this.xForwardedPort = getInteger(config, ServerVerticle.CONFIG_PREFIX + ServerVerticle.HTTP_PORT);
            }

            final HttpClient client = vertx.createHttpClient();

            this.proxy = new AirmockHttpProxy(xForwardedHost, xForwardedPort == null ? null : xForwardedPort.toString());
            this.proxy.setClient(client.getDelegate());
            this.proxy.circuitBreaker(CircuitBreaker.create("backend", vertx, getHttpProxyCircuitBreakerOptions()));
            this.proxy.backend(backendHost, backendPort);
            proxy.backendUrlMapper(this.backendUrlMapper);

            startFuture.complete();
        }
    }

    /**
     * Get config prefix for this instance.
     *
     * @return config prefix
     */
    public String getConfigPrefix() {
        return String.format("%s.%s.", HtmlBackendVerticle.class.getName(), backendName);
    }

    @Override
    public Router createRoutes() {
        final Router router = Router.router(vertx);

        router.route("/*").handler(this::callBackend);

        return router;
    }

    /**
     * Send the RoutingContext and Fallback handler to proxy.
     *
     * @param rc rc
     */
    protected void callBackend(RoutingContext rc) {
        LOGGER.debug("callBackend: backend '{}' for frontend request '{}'", backendName, rc.request().absoluteURI(), rc);
        proxy.delegate(rc, this::onFallback);
    }

    /**
     * Handler if something went wrong.
     *
     * @param rc rc
     */
    protected void onFallback(RoutingContext rc) {
        LOGGER.debug("onFallback: error from backend '{}'. Status code was {}", backendName, rc.request().response().getStatusCode(), rc);
        if (!rc.request().response().closed()) {
            rc.request().response().end();
        }
    }

    /**
     * Get circuitBreakerOptions for HtmlBackendVerticle.
     *
     * @return CircuitBreakerOptions
     */
    protected CircuitBreakerOptions getHttpProxyCircuitBreakerOptions() {
        return new CircuitBreakerOptions()
            .setMaxFailures(10) // number of failure before opening the circuit
            .setTimeout(240 * 1000) // consider a failure if the operation does not succeed in milliseconds
            .setFallbackOnFailure(true) // do we call the fallback on failure
            .setResetTimeout(10 * 1000) // milliseconds spent in open state before attempting to re-try
            ;
    }
}
