package com.inventage.airmock;

import com.inventage.airmock.kernel.AirmockApplication;
import com.inventage.airmock.waf.backend.ProxyVerticle;
import com.inventage.airmock.waf.saml.SamlPostBindingVerticle;
import com.inventage.airmock.waf.ui.WafUiVerticle;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Class that contains all applications that are used in the Airmock.
 */
public class Applications {

    private final JsonObject config;

    public Applications(JsonObject config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration must not be null.");
        }
        this.config = config;
    }

    /**
     * Register all applications.
     * @return list of applications
     *
     */
    public List<AirmockApplication> create() {
        final List<AirmockApplication> result = new LinkedList<>();

        addSamlPostBinding(result);
        addWafUi(result);
        result.add(new AirmockApplication("backends", "/", new ProxyVerticle()));

        return result;
    }

    private String getConfig(String key) {
        return config.getString(key);
    }

    private void addSamlPostBinding(List<AirmockApplication> applications) {
        addApplication("saml-postbinding", new SamlPostBindingVerticle(),
                SamlPostBindingVerticle.property(SamlPostBindingVerticle.POSTBINDING_PATH_PREFIX), applications);
    }

    private void addWafUi(List<AirmockApplication> applications) {
        addApplication("waf-ui", new WafUiVerticle(),
                WafUiVerticle.property(WafUiVerticle.PATH_PREFIX), applications);
    }

    private void addApplication(String name, Verticle mainVerticle, String configPropertyForPath, List<AirmockApplication> applications) {
        final String pathPrefix = getConfig(configPropertyForPath);
        if (StringUtils.isNotEmpty(pathPrefix)) {
            applications.add(new AirmockApplication(name, pathPrefix, mainVerticle));
        }

    }
}
