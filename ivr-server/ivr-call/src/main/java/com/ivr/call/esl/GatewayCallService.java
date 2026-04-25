package com.ivr.call.esl;

public interface GatewayCallService {

    void onInboundCall(String callUuid, String caller, String callee);

    void onAnswered(String callUuid);

    void onDtmf(String callUuid, String digit);

    void onHangup(String callUuid, String hangupCause);
}
