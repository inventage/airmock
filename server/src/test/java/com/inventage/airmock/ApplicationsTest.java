package com.inventage.airmock;

import com.inventage.airmock.kernel.AirmockApplication;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ApplicationsTest {

    @Test
    public void test_constructor_null() {
        // given
        // when
        try {
            new Applications(null);
            Assertions.fail(IllegalArgumentException.class.getName() + " expected.");
        }
        catch (Exception e) {
            // then
        }
    }

    @Test
    public void test_constructor() {
        // given
        JsonObject config = new JsonObject();
        // when
        new Applications(config);
        // then
    }

    @Test
    public void test_create() {
        // given
        final Applications applications = new Applications(new JsonObject());
        // when
        final List<AirmockApplication> list = applications.create();
        // then
        Assertions.assertEquals(1, list.size());
    }

    @Test
    public void test_create_with_ui() {
        // given
        final Applications applications = new Applications(new JsonObject().put("com.inventage.airmock.waf.ui.WafUiVerticle.path-prefix", "/prefix"));
        // when
        final List<AirmockApplication> list = applications.create();
        // then
        Assertions.assertEquals(2, list.size());
    }

    @Test
    public void test_create_with_saml() {
        // given
        final Applications applications = new Applications(new JsonObject().put("com.inventage.airmock.waf.saml.SamlPostBindingVerticle.post-binding-path-prefix", "/prefix"));
        // when
        final List<AirmockApplication> list = applications.create();
        // then
        Assertions.assertEquals(2, list.size());
    }

    @Test
    public void test_create_with_ui_and_saml() {
        // given
        final Applications applications = new Applications(new JsonObject()
                .put("com.inventage.airmock.waf.ui.WafUiVerticle.path-prefix", "/prefix")
                .put("com.inventage.airmock.waf.saml.SamlPostBindingVerticle.post-binding-path-prefix", "/prefix"));
        // when
        final List<AirmockApplication> list = applications.create();
        // then
        Assertions.assertEquals(3, list.size());
    }
}
