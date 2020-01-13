package com.inventage.airmock.waf.saml;

import com.inventage.airmock.kernel.RouteProvider;
import com.inventage.airmock.kernel.util.AirmockConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.BodyHandler;

import static io.vertx.core.http.HttpMethod.POST;

/**
 * Verticle for handling the SAML post binding requests.
 */
public class SamlPostBindingVerticle extends AbstractVerticle implements RouteProvider {

    public static final String POSTBINDING_PATH_PREFIX = "post-binding-path-prefix";

    private JsonObject config;

    /**
     * Get the fully qualified property for a given key.
     *
     * @param key the configuration key within this class
     * @return the fully qualified property (as used for env variables)
     */
    public static String property(String key) {
        return SamlPostBindingVerticle.class.getName() + "." + key;
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        // Get the config
        final ConfigRetriever configRetriever = AirmockConfigRetriever.create(vertx);
        configRetriever.getConfig(result -> {
            if (result.succeeded()) {
                config = result.result();
                startFuture.succeeded();
            }
            else {
                startFuture.fail(result.cause());
            }
        });
    }

    @Override
    public Router createRoutes() {
        final Router router = Router.router(vertx);

        router.route(POST, "/*").handler(BodyHandler.create());
        router.route(POST, "/*").handler(new SAMLPostbindingHandler(config.getString(property(POSTBINDING_PATH_PREFIX))));

        return router;
    }
}
