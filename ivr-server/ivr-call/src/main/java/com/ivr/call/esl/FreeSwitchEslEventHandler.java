package com.ivr.call.esl;

import link.thingscloud.freeswitch.esl.constant.EventNames;
import link.thingscloud.freeswitch.esl.spring.boot.starter.annotation.EslEventName;
import link.thingscloud.freeswitch.esl.spring.boot.starter.handler.EslEventHandler;
import link.thingscloud.freeswitch.esl.transport.event.EslEvent;
import link.thingscloud.freeswitch.esl.util.EslEventUtil;
import link.thingscloud.freeswitch.esl.util.VariableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@EslEventName({
        EventNames.CHANNEL_CREATE,
        EventNames.CHANNEL_ANSWER,
        EventNames.CHANNEL_HANGUP,
        EventNames.DTMF
})
public class FreeSwitchEslEventHandler implements EslEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchEslEventHandler.class);

    private final IvrCallHandler callHandler;

    public FreeSwitchEslEventHandler(IvrCallHandler callHandler) {
        this.callHandler = callHandler;
    }

    @Override
    public void handle(String address, EslEvent event) {
        String eventName = event.getEventName();
        String uuid = firstText(
                EslEventUtil.getUniqueId(event),
                EslEventUtil.getCallerUniqueId(event),
                header(event, "Unique-ID"),
                header(event, "Caller-Unique-ID")
        );
        if (!StringUtils.hasText(uuid)) {
            log.warn("[FreeSWITCH] ignore event without uuid address={} event={}", address, eventName);
            return;
        }

        switch (eventName) {
            case EventNames.CHANNEL_CREATE -> callHandler.onChannelCreate(uuid, caller(event), callee(event));
            case EventNames.CHANNEL_ANSWER -> callHandler.onChannelAnswer(uuid);
            case EventNames.DTMF -> callHandler.onDtmf(uuid, dtmfDigit(event));
            case EventNames.CHANNEL_HANGUP -> callHandler.onChannelHangup(uuid, hangupCause(event));
            default -> log.debug("[FreeSWITCH] ignore event address={} event={} uuid={}", address, eventName, uuid);
        }
    }

    private String caller(EslEvent event) {
        return firstText(
                EslEventUtil.getCallerCallerIdNumber(event),
                VariableUtil.getVar(event, "caller_id_number"),
                header(event, "Caller-Caller-ID-Number"),
                header(event, "variable_caller_id_number")
        );
    }

    private String callee(EslEvent event) {
        return firstText(
                EslEventUtil.getCallerDestinationNumber(event),
                VariableUtil.getVar(event, "destination_number"),
                header(event, "Caller-Destination-Number"),
                header(event, "variable_destination_number")
        );
    }

    private String dtmfDigit(EslEvent event) {
        return firstText(
                header(event, "DTMF-Digit"),
                VariableUtil.getVar(event, "dtmf_digit"),
                header(event, "variable_dtmf_digit")
        );
    }

    private String hangupCause(EslEvent event) {
        return firstText(
                header(event, "Hangup-Cause"),
                VariableUtil.getVar(event, "hangup_cause"),
                header(event, "variable_hangup_cause")
        );
    }

    private String header(EslEvent event, String key) {
        Map<String, String> headers = event.getEventHeaders();
        return headers == null ? null : headers.get(key);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
