package com.inventage.airmock.waf.backend;

import com.inventage.airmock.kernel.RouteProvider;
import com.inventage.airmock.kernel.backend.HtmlBackendVerticle;
import com.inventage.airmock.kernel.proxy.HttpProxy;
import com.inventage.airmock.kernel.util.AirmockConfigRetriever;
import com.inventage.airmock.waf.mapping.Mapping;
import com.inventage.airmock.waf.proxy.AirmockHttpProxy;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.inventage.airmock.kernel.util.ConfigUtils.getInteger;

/**
 * Delegates the the backend of the different mappings.
 */
public class ProxyVerticle extends AbstractVerticle implements RouteProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyVerticle.class);

    private JsonObject config;
    private String xForwardedHost;
    private Integer xForwardedPort;
    private Map<Mapping, HttpProxy> proxies = new HashMap<>();

    @Override
    public void start(Future<Void> startFuture) {
        LOGGER.info("start: ...");

        final ConfigRetriever retriever = AirmockConfigRetriever.create(vertx);
        retriever.getConfig(asyncResult -> init(asyncResult, startFuture));
    }

    /**
     * Initialize this verticle.
     *
     * @param asyncResult async config load
     * @param startFuture future for complete reporting
     */
    protected void init(AsyncResult<JsonObject> asyncResult, Future<Void> startFuture) {
        if (asyncResult.succeeded()) {
            config = asyncResult.result();

            this.xForwardedHost = config.getString(HtmlBackendVerticle.class.getName() + ".x-forwarded-host");
            this.xForwardedPort = getInteger(config, HtmlBackendVerticle.class.getName() + ".x-forwarded-port");

            startFuture.complete();
        }
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
        final Mapping mapping = (Mapping) rc.data().get(Mapping.class.getName()); // added to context by AirmockHandler.handle
        LOGGER.debug("callBackend: for mapping '{}' for frontend request '{}'", mapping.getName(), rc.request().absoluteURI(), rc);

        HttpProxy httpProxy = proxies.get(mapping);
        if (httpProxy == null) {
            httpProxy = createHttpProxy(mapping);
            proxies.put(mapping, httpProxy);
        }
        httpProxy.delegate(rc, this::onFallback);
    }

    /**
     * Handler if something went wrong.
     *
     * @param rc rc
     */
    protected void onFallback(RoutingContext rc) {
        final Mapping mapping = (Mapping) rc.data().get(Mapping.class.getName());
        LOGGER.debug("onFallback: error for mapping '{}'. Status code was {}", mapping.getName(), rc.request().response().getStatusCode(), rc);
        if (!rc.request().response().closed()) {
            rc.request().response().end();
        }
    }

    /**
     * Factory method for the HttpProxy.
     * @param mapping the mapping for which a HttpProxy should be created
     * @return new instance of HttpProxy
     */
    protected HttpProxy createHttpProxy(Mapping mapping) {
        final HttpProxy httpProxy = new AirmockHttpProxy(xForwardedHost, xForwardedPort == null ? null : xForwardedPort.toString());
        httpProxy.backend(mapping.backendProtocol(), mapping.backendHost(), mapping.backendPort());
        httpProxy.setClient(vertx.createHttpClient(withOptions(mapping)).getDelegate());
        httpProxy.circuitBreaker(CircuitBreaker.create("backend", vertx, getHttpProxyCircuitBreakerOptions()));
        httpProxy.backendUrlMapper(Function.identity());
        return httpProxy;
    }

    /**
     *
     * @param mapping the mapping for which a HttpProxy should be created
     * @return HttpClientOptions to be used
     */
    protected HttpClientOptions withOptions(Mapping mapping) {
        return new HttpClientOptions().setMaxInitialLineLength(10000)
                .setMaxPoolSize(10 /* = HttpClientOptions.DEFAULT_MAX_POOL_SIZE*/)
                .setSsl(mapping.backendProtocol() != null && "https".equalsIgnoreCase(mapping.backendProtocol()))
                .setTrustAll(true);
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
