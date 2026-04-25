package com.ivr.common.constant;

public interface CommonConst {

    String AUTH_HEADER = "Authorization";
    String TOKEN_PREFIX = "Bearer ";

    String REDIS_FLOW_KEY = "ivr:flow:";
    String REDIS_HOTLINE_KEY = "ivr:hotline:";
    String REDIS_USER_KEY = "ivr:user:";

    String FLOW_PUBLISH_CHANNEL = "ivr:flow:updated";
}
