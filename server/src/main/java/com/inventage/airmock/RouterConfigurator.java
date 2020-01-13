package com.inventage.airmock;


import com.inventage.airmock.kernel.AirmockApplication;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;

import java.util.List;

/**
 * Interface that defines a RouterConfigurator.
 */
public interface RouterConfigurator {
    /**
     * Configure a given router.
     * @param router router
     * @param vertx current verx instanced.
     * @param config current configuration.
     * @param applications applications.
     */
    void configure(Router router, Vertx vertx, JsonObject config, List<AirmockApplication> applications);
}
