package com.ivr.call.esl;

import com.ivr.engine.channel.CallChannel;
import link.thingscloud.freeswitch.esl.InboundClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 通过 ESL 把 {@link CallChannel} 调用翻译成 FreeSWITCH 的 uuid_* 命令。
 *
 * <p>启用方式：在 application.yml 设置 {@code ivr.freeswitch.enabled=true}。未启用时
 * 由 {@code LoggingCallChannel} 兜底（仅打日志），便于本地无 FreeSWITCH 时也能跑流程引擎。
 *
 * <p>命令对照：
 * <ul>
 *   <li>answer → {@code uuid_answer <uuid>}</li>
 *   <li>playback → {@code uuid_broadcast <uuid> <file|say::voice|text> aleg}</li>
 *   <li>collectDtmf → no-op（DTMF 事件已通过 ESL 事件流闭环）</li>
 *   <li>collectAsr / record → {@code uuid_record <uuid> start <file> <maxSec>}</li>
 *   <li>transfer → {@code uuid_transfer <uuid> <target> XML default}</li>
 *   <li>hangup → {@code uuid_kill <uuid> <reason>}</li>
 * </ul>
 */
@Component
@Primary
@ConditionalOnProperty(name = "ivr.freeswitch.enabled", havingValue = "true")
public class FreeSwitchCallChannel implements CallChannel {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchCallChannel.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final String address;
    private final String recordingDir;
    private final String defaultVoice;

    public FreeSwitchCallChannel(
            @Value("${ivr.freeswitch.address:localhost}") String address,
            @Value("${ivr.freeswitch.recording-dir:/tmp/ivr/recordings}") String recordingDir,
            @Value("${ivr.freeswitch.default-voice:zh|tts_commandline}") String defaultVoice) {
        this.address = address;
        this.recordingDir = recordingDir;
        this.defaultVoice = defaultVoice;
    }

    @Override
    public void answer(String callUuid) {
        sendApi("uuid_answer", callUuid);
    }

    @Override
    public void playback(String callUuid, PlaybackRequest request) {
        String arg;
        if (StringUtils.hasText(request.audioUrl())) {
            arg = request.audioUrl();
        } else if (StringUtils.hasText(request.text())) {
            String voice = StringUtils.hasText(request.voice()) ? request.voice() : defaultVoice;
            String text = EslArgs.sanitizeText(request.text());
            if (text.isEmpty()) {
                log.warn("[FS api] playback skipped: text empty after sanitize uuid={}", callUuid);
                return;
            }
            arg = "say::" + voice + "|" + text;
        } else {
            log.warn("[FS api] playback skipped: no text and no audioUrl uuid={}", callUuid);
            return;
        }
        sendApi("uuid_broadcast", callUuid + " " + arg + " aleg");
    }

    @Override
    public void collectDtmf(String callUuid, DtmfCollectRequest request) {
        // no-op: FreeSWITCH 已对接通通道默认发 DTMF 事件，由 FreeSwitchEslEventHandler 接到
        // 后续如需收集多位 / 设终止符，可在这里发 uuid_setvar playback_terminators=#
        log.info("[FS api] collectDtmf uuid={} maxDigits={} timeoutSec={} (passive listen)",
                callUuid, request.maxDigits(), request.timeoutSeconds());
    }

    @Override
    public void collectAsr(String callUuid, AsrCollectRequest request) {
        if (StringUtils.hasText(request.prompt())) {
            playback(callUuid, new PlaybackRequest(request.prompt(), "", ""));
        }
        String file = recordingPath(callUuid);
        int maxSec = Math.max(1, request.maxSeconds());
        sendApi("uuid_record", callUuid + " start " + file + " " + maxSec);
    }

    @Override
    public void transfer(String callUuid, String target) {
        String safe = EslArgs.sanitizeTarget(target);
        if (safe == null) {
            log.warn("[FS api] transfer skipped: invalid target uuid={} raw=\"{}\"", callUuid, target);
            return;
        }
        sendApi("uuid_transfer", callUuid + " " + safe + " XML default");
    }

    @Override
    public void record(String callUuid, RecordRequest request) {
        String file = StringUtils.hasText(request.filePath()) ? request.filePath() : recordingPath(callUuid);
        int maxSec = Math.max(1, request.maxSeconds());
        sendApi("uuid_record", callUuid + " start " + file + " " + maxSec);
    }

    @Override
    public void hangup(String callUuid, String reason) {
        String cause = StringUtils.hasText(reason) ? reason : "NORMAL_CLEARING";
        sendApi("uuid_kill", callUuid + " " + cause);
    }

    private String recordingPath(String callUuid) {
        String filename = callUuid + "-" + LocalDateTime.now().format(FILE_TS) + ".wav";
        // FreeSWITCH 一般跑 Linux，路径分隔符强制正斜杠
        return Paths.get(recordingDir, filename).toString().replace('\\', '/');
    }

    private void sendApi(String command, String args) {
        try {
            log.info("[FS api {}] {} {}", address, command, args);
            String jobId = InboundClient.getInstance().sendAsyncApiCommand(address, command, args);
            log.debug("[FS api {}] job={} for {}", address, jobId, command);
        } catch (Exception e) {
            log.warn("[FS api {}] failed cmd={} args={} err={}", address, command, args, e.toString());
        }
    }
}
