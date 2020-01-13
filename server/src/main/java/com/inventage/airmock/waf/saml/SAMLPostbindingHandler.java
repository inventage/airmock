package com.inventage.airmock.waf.saml;

import com.inventage.airmock.kernel.util.SAMLUtils;
import com.inventage.airmock.waf.headerbag.HeaderBag;
import com.inventage.airmock.waf.headerbag.Headermanager;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.ext.web.RoutingContext;

import static com.inventage.airmock.kernel.Constants.BEARER_PREFIX;
import static com.inventage.airmock.kernel.util.SAMLUtils.SAML_RESPONSE_PARAM_KEY;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.vertx.core.http.HttpHeaders.AUTHORIZATION;
import static io.vertx.core.http.HttpHeaders.LOCATION;

/**
 * This handler handles is meant to handle postbinding calls for the SAML Protocol. It should be configured for the
 * POSTBINDING_PATH. Please also make sure to add bodyHandler to that path.
 *
 * The handler reads the SAMLResponse from the post request and saves the contained SAMLAssertion in the routingContext.
 * It then redirects the browser to the original url, minus the POSTBINDING_PATH_PREFIX. This means, you will have to
 * configure your SAML Server to make its postbinding call go to <POSTBINDING_PATH_PREFIX>/<where-you-really-want-to-go>
 *
 * An example for a postbinding url could be http://localhost:10000/postbinding/syrius
 */
public class SAMLPostbindingHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SAMLPostbindingHandler.class);
    private String postbindingPathPrefix;

    public SAMLPostbindingHandler(String postbindingPathPrefix) {

        this.postbindingPathPrefix = postbindingPathPrefix;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        storeSAMLHeader(routingContext);
        redirect(routingContext);
    }

    public String getPostBindingPath() {
        return getPostbindingPathPrefix() + "/*";
    }

    private String getPostbindingPathPrefix() {
        return postbindingPathPrefix;
    }

    private void redirect(RoutingContext routingContext) {
        final String redirectPath = getRedirectPath(routingContext);
        LOGGER.info("redirecting postbinding to "  + redirectPath);
        routingContext.response()
                .setStatusCode(FOUND.code())
                .putHeader(LOCATION.toString(), redirectPath)
                .end();
    }

    private void storeSAMLHeader(RoutingContext routingContext) {
        if (isSAMLResponse(routingContext)) {
            Headermanager.storeHeader(routingContext, getSAMLAssertionHeader(routingContext));
        }
    }

    private boolean isSAMLResponse(RoutingContext routingContext) {
        final String samlResponseParam = getSamlResponseParam(routingContext);
        if (samlResponseParam == null) {
            return false;
        }
        return SAMLUtils.isSAMLToken(samlResponseParam);
    }

    private String getSamlResponseParam(RoutingContext routingContext) {
        return routingContext.request().getParam(SAML_RESPONSE_PARAM_KEY);
    }

    private HeaderBag.HttpHeader getSAMLAssertionHeader(RoutingContext routingContext) {
        final String samlResponseParam = getSamlResponseParam(routingContext);
        final String encodedAssertion = SAMLUtils.getEncodedAssertion(samlResponseParam);
        return new HeaderBag.HttpHeader(AUTHORIZATION, BEARER_PREFIX + encodedAssertion);
    }

    private String getRedirectPath(RoutingContext routingContext) {
        String path = routingContext.request().path();
        LOGGER.info("handling path " + path);
        if (path.startsWith(getPostbindingPathPrefix())) {
            path = path.replaceFirst(getPostbindingPathPrefix(), "");
            LOGGER.info("removed postbidning prefix. New Path is " + path);
        }

        return path;
    }
}
