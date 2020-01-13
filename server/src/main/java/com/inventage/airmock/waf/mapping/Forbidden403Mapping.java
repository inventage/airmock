package com.inventage.airmock.waf.mapping;

import io.netty.handler.codec.http.HttpResponseStatus;

public class Forbidden403Mapping extends Abstract400Mapping {
    @Override
    HttpResponseStatus getResponseStatus() {
        return HttpResponseStatus.FORBIDDEN;
    }
}
