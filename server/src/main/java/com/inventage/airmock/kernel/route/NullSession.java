package com.inventage.airmock.kernel.route;

import io.vertx.ext.web.Session;

import java.util.Collections;
import java.util.Map;

public enum NullSession implements Session {

    INSTANCE;

    @Override
    public Session regenerateId() {
        return this;
    }

    @Override
    public String id() {
        return null;
    }

    @Override
    public Session put(String s, Object o) {
        return this;
    }

    @Override
    public <T> T get(String s) {
        return null;
    }

    @Override
    public <T> T remove(String s) {
        return null;
    }

    @Override
    public Map<String, Object> data() {
        return Collections.emptyMap();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public long lastAccessed() {
        return 0;
    }

    @Override
    public void destroy() {
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }

    @Override
    public boolean isRegenerated() {
        return false;
    }

    @Override
    public String oldId() {
        return null;
    }

    @Override
    public long timeout() {
        return 0;
    }

    @Override
    public void setAccessed() {
    }
}
