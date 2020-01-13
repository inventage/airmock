package com.inventage.airmock.kernel.proxy.internal;

import io.vertx.reactivex.ext.web.Cookie;

import java.util.function.Predicate;

public class CookieFilter2 implements Predicate<Cookie> {
    private String namePart;

    public CookieFilter2(String namePart) {
        this.namePart = namePart;
    }

    @Override
    public boolean test(Cookie cookie) {
        return nameMustNotContainNamePart(cookie);
    }

    private boolean nameMustNotContainNamePart(Cookie cookie) {
        return !cookie.getName().contains(namePart);
    }
}
