package com.inventage.airmock.waf.cookiebag;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.HttpCookie;

/**
 * Cookie Bag.
 */
public final class CookieBag {
    public static final String VALUE_FIELD = "value";

    static final String NAME_FIELD = "name";

    private CookieBag() {
    }

    static JsonArray addOrReplace(HttpCookie newCookie, JsonArray cookiesForPath) {
        final JsonObject cookieWithSameName = cookiesForPath.stream()
                .map(entry -> (JsonObject) entry)
                .filter(cookie -> cookie.getString(NAME_FIELD).equals(newCookie.getName()))
                .findFirst().orElse(null);
        if (cookieWithSameName == null) {
            return add(newCookie, cookiesForPath);
        }
        else {
            return replace(cookieWithSameName, newCookie, cookiesForPath);
        }
    }

    private static JsonArray replace(JsonObject cookieWithSameName, HttpCookie newCookie, JsonArray cookiesForPath) {
        cookiesForPath.remove(cookieWithSameName);
        return add(newCookie, cookiesForPath);
    }

    private static JsonArray add(HttpCookie newCookie, JsonArray cookiesForPath) {
        final JsonObject cookieToAdd = new JsonObject();
        cookieToAdd.put(NAME_FIELD, newCookie.getName());
        cookieToAdd.put(VALUE_FIELD, newCookie.getValue());
        cookiesForPath.add(cookieToAdd);
        return cookiesForPath;
    }
}
