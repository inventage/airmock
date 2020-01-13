package com.inventage.airmock.waf.headerbag;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import java.util.Arrays;
import java.util.List;

import static com.inventage.airmock.kernel.route.RoutingContextUtils.session;
import static com.inventage.airmock.waf.headerbag.HeaderBag.NAME_FIELD;
import static com.inventage.airmock.waf.headerbag.HeaderBag.VALUE_FIELD;

/**
 * Header Manager.
 */
public final class Headermanager {
    public static final String HEADER_BAG = "HEADER_BAG";

    private static final String HEADERS_FOR_REQUEST = "HeadersForRequest";

    private static final Logger LOGGER = LoggerFactory.getLogger(Headermanager.class);

    private Headermanager() {
    }

    /**
     * Add headers to the backend request.
     *
     * @param routingContext rc
     * @param backendRequest backendRequest
     */
    public static void setHeadersToRequest(RoutingContext routingContext, HttpClientRequest backendRequest) {
        final JsonArray headers = routingContext.get(HEADERS_FOR_REQUEST);
        if (headers != null && !headers.isEmpty()) {
            LOGGER.debug("setHeadersToRequest: header '{}' for path '{}'", headers, routingContext.request().path());
            headers.stream()
                .map(entry -> (JsonObject) entry)
                .forEach(headerEntry -> backendRequest.putHeader(headerEntry.getString(NAME_FIELD), headerEntry.getString(VALUE_FIELD))
                );
        }
    }


    /**
     * Put the headers into the routing context map at the key HEADERS_FOR_REQUEST.
     *
     * @param routingContext where the headers are put
     */
    public static void prepareHeaderForRequest(RoutingContext routingContext) {
        prepareHeadersForRequest(routingContext);
    }

    /**
     * Put the headers into the routing context map at the key HEADERS_FOR_REQUEST.
     *
     * @param routingContext where the headers are put
     */
    private static void prepareHeadersForRequest(RoutingContext routingContext) {
        final JsonArray headerBag = session(routingContext).get(HEADER_BAG);
        if (headerBag != null) {
            routingContext.put(HEADERS_FOR_REQUEST, headerBag.copy());
        }
    }


    /**
     * Stores header in the session.
     * @param routingContext rc
     * @param setHeader header
     */
    public static void storeHeader(RoutingContext routingContext, HeaderBag.HttpHeader setHeader) {
        storeHeaders(routingContext, Arrays.asList(setHeader));
    }
    /**
     * Stores headers in the session.
     *
     * @param routingContext  rc
     * @param setHeaders headers
     */
    public static void storeHeaders(RoutingContext routingContext, List<HeaderBag.HttpHeader> setHeaders) {
        setHeaders.stream()
            .forEach(httpHeader -> addHeader(httpHeader, routingContext));
    }

    private static void addHeader(HeaderBag.HttpHeader header, RoutingContext routingContext) {
        LOGGER.info("addHeader: header '{}'", header.getName());
        final Session session = session(routingContext);
        JsonArray headerBag = session.get(HEADER_BAG);
        if (headerBag == null) {
            headerBag = new JsonArray();
        }

        headerBag = HeaderBag.addOrReplace(header, headerBag);
        session.remove(HEADER_BAG);
        session.put(HEADER_BAG, headerBag);
    }
}
