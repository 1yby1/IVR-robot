package com.ivr.engine.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 占位实现：把所有控制命令打到日志，便于不接 FreeSWITCH 时调试流程引擎。
 *
 * <p>ivr-call 模块以后提供 FreeSwitchCallChannel 时通过 {@code @Primary} 或扣掉本类的
 * {@code @ConditionalOnMissingBean} 自动让位。
 */
@Component
@ConditionalOnMissingBean(CallChannel.class)
public class LoggingCallChannel implements CallChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingCallChannel.class);

    @Override
    public void answer(String callUuid) {
        log.info("[CALL {}] answer", callUuid);
    }

    @Override
    public void playback(String callUuid, PlaybackRequest request) {
        log.info("[CALL {}] playback text=\"{}\" audio=\"{}\" voice={}",
                callUuid, request.text(), request.audioUrl(), request.voice());
    }

    @Override
    public void collectDtmf(String callUuid, DtmfCollectRequest request) {
        log.info("[CALL {}] collectDtmf maxDigits={} timeoutSec={} terminator={}",
                callUuid, request.maxDigits(), request.timeoutSeconds(), request.terminator());
    }

    @Override
    public void collectAsr(String callUuid, AsrCollectRequest request) {
        log.info("[CALL {}] collectAsr maxSec={} lang={} prompt=\"{}\"",
                callUuid, request.maxSeconds(), request.language(), request.prompt());
    }

    @Override
    public void transfer(String callUuid, String target) {
        log.info("[CALL {}] transfer target={}", callUuid, target);
    }

    @Override
    public void record(String callUuid, RecordRequest request) {
        log.info("[CALL {}] record maxSec={} file={}", callUuid, request.maxSeconds(), request.filePath());
    }

    @Override
    public void hangup(String callUuid, String reason) {
        log.info("[CALL {}] hangup reason={}", callUuid, reason);
    }
}
