package com.inventage.airmock.waf.mapping;

import com.inventage.airmock.kernel.route.RoutingContextUtils;
import com.inventage.airmock.waf.AirmockHandler;
import com.inventage.airmock.waf.cookiebag.Cookiemanager;
import io.reactivex.Single;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static com.inventage.airmock.waf.AirmockHandler.WAF_ROLES;


public class OneShotMapping extends DefaultMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(OneShotMapping.class);

    @Override
    public Single<Boolean> canProceed(RoutingContext routingContext, AirmockHandler airmockHandler) {
        if (sessionContainsOneRestrictedRole(routingContext)) {
            return Single.just(true);
        }
        return oneshot(routingContext, airmockHandler);
    }

    private Single<Boolean> oneshot(RoutingContext routingContext, AirmockHandler airmockHandler) {
        LOGGER.info("oneshot: on path '{}'", routingContext.request().path(), routingContext);
        final WebClientOptions options = new WebClientOptions().setUserAgent("Airmock/1.0.0");
        options.setKeepAlive(false);
        final WebClient client = WebClient.create(routingContext.vertx(), options);
        final HttpRequest<Buffer> request = client.post(getAccessDeniedUrl());
//        final HttpRequest<Buffer> request = client.post(iamPort, iamHost, oneshotUrl);

        // maybe move this to endRequest (if no other flowType uses this feature)
        addHeadersToRequest(request, routingContext);

        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        Executors.newFixedThreadPool(1).execute(
            () -> request.sendForm(
                oneShotBody(), ar -> {
                    if (ar.succeeded()) {
                        final HttpResponse<Buffer> iamResponse = ar.result();
                        airmockHandler.handleCookies(routingContext, iamResponse.cookies(), this);
                        Cookiemanager.storeCookies(routingContext, iamResponse.cookies(), this);
                        future.complete(sessionContainsOneRestrictedRole(routingContext));
                    }
                    else {
                        final Throwable cause = ar.cause();
                        LOGGER.warn("oneshot: failed '{}'", cause.getMessage(), routingContext);
                        routingContext.response().setStatusCode(500).end();
                        future.completeExceptionally(cause);
                    }
                })
        );

        return Single.fromFuture(future);
    }

    private void addHeadersToRequest(HttpRequest<Buffer> request, RoutingContext routingContext) {
        routingContext.request().headers().names().stream()
                .filter(getHeaders()::contains)
                .forEach(name -> request.putHeader(name, routingContext.request().getHeader(name)));
    }

    private MultiMap oneShotBody() {
        return MultiMap.caseInsensitiveMultiMap()
                .set("grant_type", "password")
                .set("client_id", getName());
    }

    /**
     * Checks if the current routingContext contains at least one of the necessary roles.
     * The necessary roles are stored in field restrictedRoles.
     * In addition this implementation removes all WAF_ROLES from the routingContext after the check.
     * @param routingContext The routingContext to check.
     * @return Whether or not the routing context has at least one restricted role.
     * If restrictedRoles is empty, true is returned.
     */
    protected boolean sessionContainsOneRestrictedRole(RoutingContext routingContext) {
        final boolean hasRole = super.sessionContainsOneRestrictedRole(routingContext);
        RoutingContextUtils.session(routingContext).remove(WAF_ROLES);
        return hasRole;
    }
}
