package com.inventage.airmock.waf.proxy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AirmockHttpProxyTest {

    @Test
    public void test_localhost() {
        // given
        final AirmockHttpProxy airmockHttpProxy = new AirmockHttpProxy(null, null);
        // when
        final String port = airmockHttpProxy.filterPort("localhost");
        // then
        Assertions.assertEquals("localhost", port);
    }

    @Test
    public void test_localhost_8080() {
        // given
        final AirmockHttpProxy airmockHttpProxy = new AirmockHttpProxy(null, null);
        // when
        final String port = airmockHttpProxy.filterPort("localhost:8080");
        // then
        Assertions.assertEquals("localhost", port);
    }
}
