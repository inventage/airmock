package com.inventage.airmock.waf.mapping;

import io.netty.handler.codec.http.HttpResponseStatus;

public class Unauthorized401Mapping extends Abstract400Mapping {


    @Override
    HttpResponseStatus getResponseStatus() {
        return HttpResponseStatus.UNAUTHORIZED;
    }
}
