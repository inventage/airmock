package com.inventage.airmock.waf.headerbag;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Header Bag.
 */
public final class HeaderBag {

    public static final String NAME_FIELD = "name";
    public static final String VALUE_FIELD = "value";
    public static final String MAPPING_FIELD = "mapping";

    private HeaderBag() {
    }

    static JsonArray addOrReplace(HttpHeader newHeader, JsonArray headers) {
        final JsonObject headerWithSameName = headers.stream()
                .map(entry -> (JsonObject) entry)
                .filter(header -> header.getString(NAME_FIELD).equals(newHeader.getName()))
                .findFirst().orElse(null);
        if (headerWithSameName == null) {
            return add(newHeader, headers);
        }
        else {
            return replace(headerWithSameName, newHeader, headers);
        }
    }

    private static JsonArray replace(JsonObject headerWithSameName, HttpHeader newHeader, JsonArray headers) {
        headers.remove(headerWithSameName);
        return add(newHeader, headers);
    }

    private static JsonArray add(HttpHeader newHeader, JsonArray headers) {
        final JsonObject headerToAdd = new JsonObject();
        headerToAdd.put(NAME_FIELD, newHeader.getName());
        headerToAdd.put(VALUE_FIELD, newHeader.getValue());
        headerToAdd.put(MAPPING_FIELD, newHeader.getMappingName());
        headers.add(headerToAdd);
        return headers;
    }

    public static class HttpHeader {
        private CharSequence name;
        private String value;
        private String mappingName;

        public HttpHeader(CharSequence name, String value) {
            this(name, value, null);
        }

        public HttpHeader(CharSequence name, String value, String mappingName) {
            this.name = name;
            this.value = value;
            this.mappingName = mappingName;
        }

        public CharSequence getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getMappingName() { return mappingName; }
    }
}
