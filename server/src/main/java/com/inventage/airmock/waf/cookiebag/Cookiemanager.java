package com.inventage.airmock.waf.cookiebag;

import com.inventage.airmock.waf.mapping.Mapping;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

import java.net.HttpCookie;
import java.util.List;
import java.util.Map;

import static com.inventage.airmock.kernel.route.RoutingContextUtils.session;

/**
 * Cookie Manager.
 */
public final class Cookiemanager {
    public static final String WAF_ROLES = "WAF_ROLES";
    public static final String AUDIT_TOKEN = "AUDIT_TOKEN";
    public static final String COOKIE_BAG = "COOKIE_BAG";

    private static final Logger LOGGER = LoggerFactory.getLogger(Cookiemanager.class);

    private static final String COOKIES_FOR_MAPPING = "CookieForMapping";

    private Cookiemanager() {
    }

    /**
     * Add cookies to the backend request.
     *
     * @param routingContext rc
     * @param backendRequest backendRequest
     */
    public static void setCookiesToRequest(RoutingContext routingContext, HttpClientRequest backendRequest) {
        final String cookieHeader = routingContext.get(COOKIES_FOR_MAPPING);
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            LOGGER.debug("setCookiesToRequest: header '{}' for path '{}'", cookieHeader, routingContext.request().path(), routingContext);
            backendRequest.putHeader(HttpHeaders.COOKIE, cookieHeader);
        }
    }


    /**
     * Put the cookie header for the request path into the routing context map at the key COOKIES_FOR_MAPPING.
     *
     * @param routingContext from which the path is taken and where the header is put
     */
    public static void prepareCookieHeaderForRequest(RoutingContext routingContext) {
        prepareCookieHeaderForRequest(routingContext.request().path(), routingContext);
    }

    /**
     * Put the cookie header for the request path into the routing context map at the key COOKIES_FOR_MAPPING.
     *
     * @param requestPath    path for the request
     * @param routingContext from which the path is taken and where the header is put
     */
    private static void prepareCookieHeaderForRequest(String requestPath, RoutingContext routingContext) {
        LOGGER.info("prepareCookieHeaderForRequest: for path '{}'", requestPath, routingContext);
        final JsonObject cookieBag = (JsonObject) session(routingContext).get(COOKIE_BAG);
        if (cookieBag != null) {
            final StringBuffer cookieHeader = new StringBuffer();
            cookieBag.stream()
                .filter(entry -> isPathMatching(entry, requestPath))
                .map(entry -> (JsonArray) entry.getValue())
                .forEach(cookies -> appendToCookieHeader(cookies, cookieHeader, requestPath, routingContext));
            routingContext.put(COOKIES_FOR_MAPPING, cookieHeader.toString());
        }
    }

    private static boolean isPathMatching(Map.Entry<String, Object> pathToCookies, String requestPath) {
        return requestPath.startsWith(pathToCookies.getKey());
    }

    private static void appendToCookieHeader(JsonArray cookies, StringBuffer cookieHeader, String requestPath, RoutingContext routingContext) {
        cookies.stream().map(cookie -> (JsonObject) cookie).forEach(cookie -> {
            if (cookieHeader.length() > 0) {
                cookieHeader.append("; ");
            }
            cookieHeader.append(asCookieString(cookie));
            LOGGER.info("appendToCookieHeader: '{}' for path '{}'", cookie.getString(CookieBag.NAME_FIELD), requestPath, routingContext);
        });
    }

    private static String asCookieString(JsonObject cookie) {
        final StringBuffer cookieHeader = new StringBuffer();
        cookieHeader.append(cookie.getString(CookieBag.NAME_FIELD));
        cookieHeader.append("=");
        cookieHeader.append(cookie.getString(CookieBag.VALUE_FIELD));
        return cookieHeader.toString();
    }

    /**
     * Stores cookies in the session.
     *
     * @param routingContext  rc
     * @param setCookiesValue cookies
     * @param currentMapping  current mapping
     */
    public static void storeCookies(RoutingContext routingContext, List<String> setCookiesValue, Mapping currentMapping) {
        setCookiesValue.stream()
            .map(setCookie -> HttpCookie.parse(setCookie))
            .flatMap(cookies -> cookies.stream())
            .forEach(cookie -> addCookieForPath(cookie, routingContext, currentMapping));
    }

    private static void addCookieForPath(HttpCookie cookie, RoutingContext routingContext, Mapping currentMapping) {
        String cookiePath = cookie.getPath();
        if (cookiePath == null) {
            cookiePath = currentMapping.getContextRoot();
        }
        LOGGER.info("addCookieForPath: cookie '{}' for path '{}'", cookie.getName(), cookiePath, routingContext);
        final Session session = session(routingContext);
        JsonObject cookieBag = (JsonObject) session.get(COOKIE_BAG);
        if (cookieBag == null) {
            cookieBag = new JsonObject();
        }
        JsonArray cookiesForPath = cookieBag.getJsonArray(cookiePath);
        if (cookiesForPath == null) {
            cookiesForPath = new JsonArray();
        }
        cookiesForPath = CookieBag.addOrReplace(cookie, cookiesForPath);
        cookieBag.put(cookiePath, cookiesForPath);
        session.remove(COOKIE_BAG);
        session.put(COOKIE_BAG, cookieBag);
    }
}
