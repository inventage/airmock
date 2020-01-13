package com.inventage.airmock.waf.mapping;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class RedirectMapping extends DefaultMapping {
    @Override
    public void endRequest(RoutingContext routingContext) {
        final String accessDeniedUrl = getAccessDeniedUrlWithPostfix(routingContext);
        routingContext.response()
                .setStatusCode(HttpResponseStatus.FOUND.code())
                .putHeader(HttpHeaders.LOCATION.toString(), accessDeniedUrl)
                .end();
    }

    private String getAccessDeniedUrlWithPostfix(RoutingContext routingContext) {
        String accessDeniedUrl = getAccessDeniedUrl();

        try {
            if (accessDeniedUrl.endsWith("redirect_uri=")) {
                final String requestPath = URLEncoder.encode(routingContext.request().path(), "ISO-8859-1");
                accessDeniedUrl = accessDeniedUrl + requestPath; // only for OIDC the original url is appended
            }
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return accessDeniedUrl;
    }
}
