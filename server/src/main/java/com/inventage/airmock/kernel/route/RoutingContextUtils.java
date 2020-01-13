package com.inventage.airmock.kernel.route;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.inventage.airmock.kernel.Constants;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.auth.oauth2.AccessToken;
import io.vertx.reactivex.ext.web.Cookie;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;

/**
 * Utils for handling routingContext.
 */
public final class RoutingContextUtils {
    private RoutingContextUtils() {
    }

    /**
     * Get the Base64 encoded Access Token. The token is taken either from
     * <p>
     * 1. the user as set by the OIDC Adapter
     * 2. the jwt-<APPLICATION-NAME> cookie as set by the WAF (e.g. "jwt-marketdata")
     *
     * @param rc         routing context
     * @param cookieName the name of the cookie
     * @return the OAuth2 Access Token as a Base64 encoded String or null
     */
    public static String getAccessTokenEncoded(RoutingContext rc, String cookieName) {
        final AccessToken tokens = getTokens(rc);
        if (tokens != null) {
            return tokens.opaqueAccessToken();
        }
        else {
            // get access token from JWT-APPLICATION cookie
            return getApplicationAccessToken(rc, cookieName);
        }
    }

    /**
     * Gets the session from the routing context, if available.
     * @param routingContext The routing context to get the session from.
     * @return The session stored in the routing context or a {@link NullSession} if no session is available.
     */
    public static Session session(RoutingContext routingContext) {
        if (routingContext.session() != null) {
            return routingContext.session();
        }
        else {
            return Session.newInstance(NullSession.INSTANCE);
        }
    }


    /**
     * @param rc routingContext
     * @return the OIDC ID Token as a JsonObject or null
     */
    public static DecodedJWT getJWT(RoutingContext rc) {
        if (rc != null) {
            return rc.<DecodedJWT>get(Constants.PORTAL_ACCESS_TOKEN);
        }
        return null;
    }

    private static AccessToken getTokens(RoutingContext rc) {
        AccessToken accessToken = null;
        final User user = rc.user();
        if (user != null) {
            if (AccessToken.class.isAssignableFrom(user.getDelegate().getClass())) {
                accessToken = (AccessToken) user.getDelegate();
            }
        }
        return accessToken;
    }

    private static String getApplicationAccessToken(RoutingContext rc, String cookieName) {
        String encodedAccessToken = null;
        final Cookie cookie = rc.getCookie(cookieName);
        if (cookie != null) {
            encodedAccessToken = cookie.getValue();
        }
        return encodedAccessToken;
    }

}
