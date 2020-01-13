package com.inventage.airmock.kernel;


import io.vertx.reactivex.ext.web.Router;

/**
 * Interface that must be implemented by applications that need routing.
 */
public interface RouteProvider {
    /**
     * Method that must be implemented by each application that offers its own routing.
     * CreateRoutes returns a router with all routes that are needed.
     *
     * @return router with routes
     */
    Router createRoutes();
}
