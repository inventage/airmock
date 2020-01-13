package com.inventage.airmock.waf.mapping;

import com.inventage.airmock.waf.AirmockHandler;
import io.reactivex.Single;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * Class that represents a WAF Mapping.
 */
public interface Mapping {

    /**
     * Name of this mapping.
     *
     * @return String
     */
    String getName();

    /**
     * Context root of this mapping.
     *
     * @return String
     */
    String getContextRoot();

    /**
     * Is the path matching.
     *
     * @param urlPath path
     * @return boolean
     */
    boolean isMatching(String urlPath);

    /**
     * Ends a request.
     *
     * @param routingContext rc
     */
    void endRequest(RoutingContext routingContext);

    /**
     * Has the request the required permissions.
     *
     * @param routingContext rc
     * @param airmockHandler handler
     * @return Single<Boolean></Boolean>
     */
    Single<Boolean> canProceed(RoutingContext routingContext, AirmockHandler airmockHandler);

    /**
     * Logout.
     *
     * @param routingContext rc
     */
    void logout(RoutingContext routingContext);

    /**
     * Host to redirect requests to.
     *
     * @return host name
     */
    String backendHost();

    /**
     * Port on the host to redirect requests to.
     *
     * @return host port
     */
    int backendPort();

}
