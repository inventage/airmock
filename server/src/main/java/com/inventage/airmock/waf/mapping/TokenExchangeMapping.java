package com.inventage.airmock.waf.mapping;

import com.inventage.airmock.waf.AirmockHandler;
import com.inventage.airmock.waf.cookiebag.Cookiemanager;
import com.inventage.airmock.waf.jwt.JWTValidator;
import io.reactivex.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.Cookie;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import static com.inventage.airmock.kernel.Constants.*;
import static com.inventage.airmock.kernel.Constants.BEARER_PREFIX;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;

public class TokenExchangeMapping extends DefaultMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenExchangeMapping.class);

    private URL tokenExchangeUrl;

    @Override
    public Single<Boolean> canProceed(RoutingContext routingContext, AirmockHandler airmockHandler) {
        // if there is a JWT in the incoming request, then we can proceed by setting the cookie value as the Authorization: Bearer value
        final Cookie jwtCookie = routingContext.getCookie(JWT_COOKE_NAME);
        final JWTValidator jwtValidator = getJwtValidator();
        if (jwtValidator.isValidJWT(jwtCookie, getClientId())) {
            replaceAuthorizationHeader(routingContext, jwtCookie.getValue());
            return Single.just(true);
        }
        routingContext.request().pause(); // to prevent 'Request has already been read' errors when the request body pumped
        return tokenexchange(routingContext, airmockHandler);
    }

    private Single<Boolean> tokenexchange(RoutingContext routingContext, AirmockHandler airmockHandler) {
        LOGGER.info("tokenexchange: on path '{}'", routingContext.request().path(), routingContext);
        final String samlHeader = getSAMLHeader(routingContext);
        if (samlHeader == null) {
            return Single.just(false);
        }
        final WebClientOptions options = new WebClientOptions().setUserAgent("Airmock/1.0.0");
        options.setKeepAlive(false);
        options.setConnectTimeout(2);
        options.setIdleTimeout(2);
        final WebClient client = WebClient.create(routingContext.vertx(), options);
        final HttpRequest<Buffer> request = client.post(getTokenExchangeUrl().getPort(), getTokenExchangeUrl().getHost(), getTokenExchangeUrl().getPath());

        request.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/x-www-form-urlencoded");

        return request.rxSendForm(tokenexchangeBody(samlHeader))
            .map(
                iamResponse -> {
                    final Optional<String> jwt = getJWT(iamResponse);

                    if (!jwt.isPresent()) {
                        setNotAuthorized(routingContext, "Response from IAM failed.");
                        return false;
                    }

                    replaceAndStoreAuthorizationHeader(routingContext, jwt.get());
                    airmockHandler.handleCookies(routingContext, iamResponse.cookies(), this);
                    Cookiemanager.storeCookies(routingContext, iamResponse.cookies(), this);
                    return true;
                })
            .onErrorReturn(
                error -> {
                    LOGGER.warn("tokenexchange: failed '{}'", error.getMessage(), routingContext);
                    routingContext.response().setStatusCode(500).end();
                    return false;
                });
    }

    private void setNotAuthorized(RoutingContext routingContext, String reason) {
        LOGGER.warn("setNotAuthorized: {}", reason, routingContext);
        routingContext.response().setStatusCode(UNAUTHORIZED.code());
    }

    private JWTValidator getJwtValidator() {
        final String certificateUrl = getConfigUtils().replaceEnvVariables(getConfig().get("certificateUrl"));
        return new JWTValidator(certificateUrl);
    }

    private Optional<String> getJWT(HttpResponse<Buffer> response) {
        if (response.statusCode() != OK.code()) {
            return Optional.empty();
        }
        final String jwt = extractJWT(response);
        return Optional.ofNullable(jwt);
    }

    private void replaceAndStoreAuthorizationHeader(RoutingContext routingContext, String jwt) {
        replaceAuthorizationHeader(routingContext, jwt);
        // This will be retrieved and sent back to the frontend as a SET_COOKIE header.
        routingContext.data().put(STORED_JWT_KEY, jwt);
    }

    private void replaceAuthorizationHeader(RoutingContext routingContext, String jwt) {
        routingContext.request().headers().remove(HttpHeaders.AUTHORIZATION);
        routingContext.request().headers().add(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + jwt);
    }

    private String extractJWT(HttpResponse<Buffer> response) {
        final JsonObject jsonObject = response.bodyAsJsonObject();
        return jsonObject.getString("access_token");
    }

    private String getSAMLHeader(RoutingContext routingContext) {
        String header = routingContext.request().getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            header = header.replaceFirst(BEARER_PREFIX, "");
        }
        return header;
    }

    // https://tools.ietf.org/html/draft-ietf-oauth-token-exchange-19#section-2.1
    private MultiMap tokenexchangeBody(String samlTokenUrlEncoded) {
        final String clientId = getClientId();
        final String subjectIssuer = getConfig().get("subjectIssuer");
        final String clientSecret = getConfig().get("clientSecret");

        if (clientId == null || subjectIssuer ==  null || clientSecret == null) {
            throw new IllegalStateException(
                    "missing config: clientId-> " + clientId + " subjectIssuer-> " + subjectIssuer + " clientSecret-> " + clientSecret);
        }

        return MultiMap.caseInsensitiveMultiMap()
                .set("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                .set("subject_token_type", "urn:ietf:params:oauth:token-type:saml2")
                .set("subject_token", samlTokenUrlEncoded)
                .set("subject_issuer", subjectIssuer)
                .set("client_id", clientId)
                .set("client_secret", clientSecret);
    }

    private String getClientId() {
        return getConfig().get("clientId");
    }

    URL getTokenExchangeUrl() {
        if (tokenExchangeUrl == null) {
            initializeTokenExchangeUrl();
        }
        return  tokenExchangeUrl;

    }

    private void initializeTokenExchangeUrl() {
        try {
            tokenExchangeUrl = new URL(getAccessDeniedUrl());
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
