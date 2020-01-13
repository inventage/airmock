package com.inventage.airmock.waf.jwt;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.ext.web.Cookie;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


public class JWTValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTValidator.class);

    private String certificateUrl;

    public JWTValidator(String certificateUrl) {
        this.certificateUrl = certificateUrl;
    }

    /**
     * Checks the validity of a jwt cookie.
     * @param jwtCoookie The cookie that contains the encoded jwt.
     * @param audience The audience to check for. Can be null.
     * @return Whether or not the cookie is valid according to the certificate url given in the constructor. If the
     * given jwtCookie is null, false will be returned.
     */
    public boolean isValidJWT(Cookie jwtCoookie, String audience) {
        if (jwtCoookie == null) {
            return false;
        }

        final String encodedJWT = jwtCoookie.getValue();
        try {
            final DecodedJWT decodedJWT = JWT.decode(encodedJWT);
            final JWTVerifier jwtVerifier = getVerifier(decodedJWT, audience);
            jwtVerifier.verify(encodedJWT);
        }
        catch (Exception e) {
            LOGGER.warn("Exception while verifying jwt: " + e.getMessage());
            return false;
        }
        LOGGER.info("JWT was successfully verified");
        return true;
    }

    private JWTVerifier getVerifier(DecodedJWT decodedJWT, String audience) throws JwkException, MalformedURLException {
        final Verification verification = JWT.require(Algorithm.RSA256(getPublicKey(decodedJWT), null));
        if (isNotBlank(audience)) {
            verification.withAudience(audience);
        }
        return verification.build();
    }

    private RSAPublicKey getPublicKey(DecodedJWT decodedJWT) throws JwkException, MalformedURLException {
        final URL certUrl = new URL(certificateUrl);
        final JwkProvider jwkProvider = new JwkProviderBuilder(certUrl).build();
        final Jwk jwk = jwkProvider.get(decodedJWT.getKeyId());
        return (RSAPublicKey) jwk.getPublicKey();
    }
}
