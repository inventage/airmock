package com.inventage.airmock;


import com.inventage.airmock.kernel.AirmockApplication;
import com.inventage.airmock.kernel.logging.HeaderToContextHandler;
import com.inventage.airmock.kernel.logging.LoggerHandler;
import com.inventage.airmock.waf.AirmockHandler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.Validate.notNull;

/**
 * The simple router configurator used in the Airmock.
 */
public class SimpleRouterConfigurator implements RouterConfigurator {
    public static final String CONFIG_PREFIX = SimpleRouterConfigurator.class.getName() + ".";
    public static final String RUN_IN_STATELESS_MODE = "run-in-stateless-mode";

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleRouterConfigurator.class);

    @Override
    public void configure(Router router, Vertx vertx, JsonObject config, List<AirmockApplication> applications) {
        notNull(router);
        notNull(config);

        router.route().handler(new HeaderToContextHandler());
        router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
        addSessionHandler(vertx, router, config);

        router.route().handler(new AirmockHandler(config, router.getDelegate()));

        applications.forEach(application -> addHandlerForApplication(application, router));
    }

    private void addSessionHandler(Vertx vertx, Router router, JsonObject config) {
        final String runInStatelessMode = config.getString(CONFIG_PREFIX + RUN_IN_STATELESS_MODE);

        if (Boolean.valueOf(runInStatelessMode)) {
            LOGGER.info("addSessionHandler: Running in STATELESS mode.");
        }
        else {
            router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx, "airmock-session-handler"))
                    .setSessionCookieName("airmock"));
        }
    }

    private void addHandlerForApplication(AirmockApplication application, Router router) {
        final Optional<Router> applicationRouter = application.createRoutes();
        if (applicationRouter.isPresent()) {
            router.mountSubRouter(application.getApplicationRootPath(), applicationRouter.get());
            LOGGER.info("addHandlerForApplication: {} by application '{}'",
                routesDescription(application.getApplicationRootPath(), applicationRouter.get()), application.getApplicationName());
        }
    }

    private String routesDescription(String basePath, Router router) {
        return routesDescription(basePath, router.getDelegate());
    }

    private String routesDescription(String basePath, io.vertx.ext.web.Router router) {
        final String paths = router.getRoutes().stream()
            .map(Route::getPath)
            .filter(Objects::nonNull)
            .map(p -> p.equals("/") ? "/*" : p)
            .map(p -> basePath + p)
            .reduce("", (t, u) -> t + " " + u);

        final String patterns = router.getRoutes().stream()
            .map(route -> {
                try {
                    final Field patternField = route.getClass().getDeclaredField("pattern");
                    patternField.setAccessible(true);
                    final Pattern pattern = (Pattern) patternField.get(route);
                    if (pattern != null) {
                        return pattern.pattern();
                    }
                }
                catch (NoSuchFieldException e) {
                    LOGGER.debug("Implementation of vertx Router class changed. Private field 'pattern' not there any more.");
                }
                catch (IllegalAccessException ignored) {
                    LOGGER.debug("Something went wrong, retrieving the Pattern from vertx Router.");
                }
                return null;
            })
            .filter(Objects::nonNull)
            .map(p -> p.replace("\\Q", "").replace("\\E", ""))
            .map(p -> basePath + p)
            .reduce("", (t, u) -> t + " " + u);

        return "Pathes: " + paths + " Patterns: " + patterns;
    }
}
