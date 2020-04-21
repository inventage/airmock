package com.inventage.airmock.waf.mapping;

import com.inventage.airmock.kernel.util.ConfigUtils;
import com.inventage.airmock.waf.AirmockHandler;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.inventage.airmock.kernel.route.RoutingContextUtils.session;
import static com.inventage.airmock.waf.AirmockHandler.WAF_ROLES;

/**
 * The default mapping used in the airmock WAF.
 */
public class DefaultMapping implements Mapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMapping.class);

    private ConfigUtils configUtils;
    private String name;
    private String contextRoot;
    private String backendProtocol;
    private String backendHost;
    private int backendPort;
    private List<String> restrictedToRoles = new ArrayList<>();
    private String accessDeniedUrl;
    private List<String> headers;
    private Map<String, String> config;

    /**
     * Initializes the Mapping with its configuration.
     * @param configUtils The configUtils to use.
     * @param name The name of the mapping.
     * @param contextRoot The context root.
     * @param restrictedToRoles The list of restricted roles.
     * @param accessDeniedUrl The url to use if the access is denied.
     * @param headers The headers to pass. Only necessary for ONESHOT
     * @param backendProtocol The protocol to the backend.
     * @param backendHost The host of the backend.
     * @param backendPort The port of the backend.
     * @param config The additional configurations.
     */
    public void init(ConfigUtils configUtils,
                     String name,
                     String contextRoot,
                     String[] restrictedToRoles,
                     String accessDeniedUrl,
                     List<String> headers,
                     String backendProtocol,
                     String backendHost,
                     int backendPort,
                     Map<String, String> config) {
        this.configUtils = configUtils;
        this.name = name;
        this.contextRoot = contextRoot;
        this.backendProtocol = backendProtocol;
        this.backendHost = backendHost;
        this.backendPort = backendPort;
        this.restrictedToRoles.addAll(Arrays.asList(restrictedToRoles));
        this.accessDeniedUrl = accessDeniedUrl;
        this.headers = headers;
        this.config = config;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getContextRoot() {
        return contextRoot;
    }

    /**
     * Is the path matching.
     *
     * @param urlPath path
     * @return boolean
     */
    public boolean isMatching(String urlPath) {
        return urlPath != null && urlPath.startsWith(contextRoot);
    }

    /**
     * ends the current request.
     *
     * @param routingContext rc
     */
    public void endRequest(RoutingContext routingContext) {
        rerouteIfNotSameDestination(routingContext.getDelegate(), accessDeniedUrl);
    }

    @Override
    public Single<Boolean> canProceed(RoutingContext routingContext, AirmockHandler airmockHandler) {
        return Single.just(sessionContainsOneRestrictedRole(routingContext));
    }


    @Override
    public void logout(RoutingContext routingContext) {

    }

    @Override
    public String backendProtocol() {
        return backendProtocol;
    }

    @Override
    public String backendHost() {
        return backendHost;
    }

    @Override
    public int backendPort() {
        return backendPort;
    }

    private void rerouteIfNotSameDestination(io.vertx.ext.web.RoutingContext rc, String newDestination) {
        if (newDestination != null && !newDestination.equals(rc.request().path())) {
            rc.reroute(HttpMethod.GET, newDestination);
        }
        else {
            if (!rc.request().response().closed()) {
                LOGGER.warn("rerouteIfNotSameDestination: reroute loop detected for '{}' request '{}'", rc.request().method(), rc.request().path(), rc);
                rc.request().response().setStatusCode(404).exceptionHandler(e -> {
                    LOGGER.warn("rerouteIfNotSameDestination: exceptionHandler for '{}' request '{}'", rc.request().method(), rc.request().path(), rc);
                }).end();
            }
            else {
                LOGGER.warn("rerouteIfNotSameDestination: reroute loop detected on closed response for '{}' request '{}'",
                    rc.request().method(), rc.request().path(), rc);
            }
        }
    }


    private boolean lastAccess(Session session) {
        session.put(getContextRoot(), Instant.now());
        return true;
    }

    /**
     * Checks if the current routingContext contains at least one of the necessary roles.
     * The necessary roles are stored in field restrictedRoles.
     * @param routingContext The routingContext to check.
     * @return Whether or not the routing context has at least one restricted role.
     * If restrictedRoles is empty, true is returned.
     */
    protected boolean sessionContainsOneRestrictedRole(RoutingContext routingContext) {
        if (restrictedToRoles == null || restrictedToRoles.size() == 0) {
            return true;
        }
        else {
            final Session session = session(routingContext);
            session.id();
            boolean hasOneRequiredRole = false;
            final JsonArray roles = session.get(WAF_ROLES);
            if (roles != null) {
                hasOneRequiredRole = roles.stream()
                    .map(role -> (JsonObject) role)
                    .map(role -> role.getString("name"))
                    .filter(name -> restrictedToRoles.contains(name))
                    .map(name -> lastAccess(session))
                    .findFirst().orElse(false);
            }

            return hasOneRequiredRole;
        }
    }

    public String getAccessDeniedUrl() {
        return accessDeniedUrl;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public ConfigUtils getConfigUtils() {
        return configUtils;
    }
}
