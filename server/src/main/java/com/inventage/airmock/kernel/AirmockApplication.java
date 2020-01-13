package com.inventage.airmock.kernel;

import io.vertx.core.Verticle;
import io.vertx.reactivex.ext.web.Router;

import java.util.Optional;

/**
 * Generic Airmock Application.
 */
public class AirmockApplication {
    private final String name;
    private final String applicationRootPath;
    private final Verticle applicationVerticle;

    public AirmockApplication(String name, String applicationRootPath, Verticle applicationVerticle) {
        this.name = name;
        this.applicationRootPath = applicationRootPath;
        this.applicationVerticle = applicationVerticle;
    }

    public String getApplicationName() {
        return this.name;
    }

    public String getApplicationRootPath() {
        return this.applicationRootPath;
    }

    public Verticle getApplicationVerticle() {
        return applicationVerticle;
    }

    /**
     * Create routes for a specific application.
     *
     * @return empty if no routes present
     */
    public Optional<Router> createRoutes() {
        if (applicationVerticle instanceof RouteProvider) {
            return Optional.of(((RouteProvider) applicationVerticle).createRoutes());
        }
        return Optional.empty();
    }
}
