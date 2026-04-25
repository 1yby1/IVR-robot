package com.ivr.call.esl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IvrCallHandler {

    private static final Logger log = LoggerFactory.getLogger(IvrCallHandler.class);

    private final GatewayCallService gatewayCallService;

    public IvrCallHandler(GatewayCallService gatewayCallService) {
        this.gatewayCallService = gatewayCallService;
    }

    public void onChannelCreate(String uuid, String caller, String callee) {
        log.info("[Call] incoming uuid={} caller={} callee={}", uuid, caller, callee);
        gatewayCallService.onInboundCall(uuid, caller, callee);
    }

    public void onChannelAnswer(String uuid) {
        log.info("[Call] answered uuid={}", uuid);
        gatewayCallService.onAnswered(uuid);
    }

    public void onDtmf(String uuid, String digit) {
        log.info("[Call] dtmf uuid={} digit={}", uuid, digit);
        gatewayCallService.onDtmf(uuid, digit);
    }

    public void onChannelHangup(String uuid, String hangupCause) {
        log.info("[Call] hangup uuid={} cause={}", uuid, hangupCause);
        gatewayCallService.onHangup(uuid, hangupCause);
    }
}
