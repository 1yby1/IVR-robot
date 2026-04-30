package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.entity.IvrHotline;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.admin.mapper.IvrHotlineMapper;
import com.ivr.ai.TtsAsrService;
import com.ivr.call.esl.GatewayCallService;
import com.ivr.engine.FlowExecutor;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.session.FlowSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

@Service
public class FreeSwitchGatewayCallService implements GatewayCallService {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchGatewayCallService.class);
    private static final int CALLER_CALLEE_MAX_CHARS = 32;
    /** call_log 表里 end_reason 值固定为这几种。 */
    private static final Map<String, String> HANGUP_CAUSE_MAP = Map.of(
            "NORMAL_CLEARING", "completed",
            "ORIGINATOR_CANCEL", "abandoned",
            "NO_ANSWER", "abandoned",
            "USER_BUSY", "busy",
            "CALL_REJECTED", "rejected"
    );

    private final IvrHotlineMapper hotlineMapper;
    private final IvrFlowMapper flowMapper;
    private final FlowExecutor flowExecutor;
    private final CallRecordService callRecordService;
    private final TtsAsrService ttsAsrService;
    private final Executor asrExecutor;
    private final Path recordingRoot;
    private final long maxRecordingBytes;

    public FreeSwitchGatewayCallService(IvrHotlineMapper hotlineMapper,
                                        IvrFlowMapper flowMapper,
                                        FlowExecutor flowExecutor,
                                        CallRecordService callRecordService,
                                        TtsAsrService ttsAsrService,
                                        @Qualifier("ivrAsrExecutor") Executor asrExecutor,
                                        @Value("${ivr.freeswitch.recording-dir:/tmp/ivr/recordings}") String recordingDir,
                                        @Value("${ivr.freeswitch.max-recording-bytes:33554432}") long maxRecordingBytes) {
        this.hotlineMapper = hotlineMapper;
        this.flowMapper = flowMapper;
        this.flowExecutor = flowExecutor;
        this.callRecordService = callRecordService;
        this.ttsAsrService = ttsAsrService;
        this.asrExecutor = asrExecutor;
        this.recordingRoot = Path.of(recordingDir).toAbsolutePath().normalize();
        this.maxRecordingBytes = maxRecordingBytes;
    }

    @Override
    public void onInboundCall(String callUuid, String caller, String callee) {
        String safeCaller = trimToMax(caller, CALLER_CALLEE_MAX_CHARS);
        String safeCallee = trimToMax(callee, CALLER_CALLEE_MAX_CHARS);

        IvrHotline hotline = findHotline(safeCallee);
        if (hotline == null) {
            recordRejectedCall(callUuid, safeCaller, safeCallee, "No enabled hotline binding");
            return;
        }

        IvrFlow flow = flowMapper.selectById(hotline.getFlowId());
        if (!isPublished(flow)) {
            recordRejectedCall(callUuid, safeCaller, safeCallee, "Hotline flow is not published");
            return;
        }

        callRecordService.startCall(callUuid, safeCaller, safeCallee, flow.getId(), flow.getCurrentVersion());
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "inbound", Map.of(
                "caller", valueOrEmpty(safeCaller),
                "callee", valueOrEmpty(safeCallee),
                "hotlineId", hotline.getId(),
                "flowId", flow.getId(),
                "flowVersion", Objects.requireNonNullElse(flow.getCurrentVersion(), 0)
        ));

        FlowContext context = new FlowContext();
        context.setCallUuid(callUuid);
        context.setCaller(safeCaller);
        context.setCallee(safeCallee);
        context.setFlowId(flow.getId());
        context.setFlowVersion(flow.getCurrentVersion());
        context.setVar("flowCode", flow.getFlowCode());
        context.setVar("flowName", flow.getFlowName());
        flowExecutor.start(context);
    }

    @Override
    public void onAnswered(String callUuid) {
        callRecordService.markAnswered(callUuid);
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "answer", Map.of());
        flowExecutor.resumeOnAnswer(callUuid);
    }

    @Override
    public void onDtmf(String callUuid, String digit) {
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "dtmf", Map.of(
                "digit", valueOrEmpty(digit)
        ));
        flowExecutor.resumeWithDtmf(callUuid, digit);
    }

    @Override
    public void onHangup(String callUuid, String hangupCause) {
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "hangup", Map.of(
                "cause", valueOrEmpty(hangupCause)
        ));
        flowExecutor.abort(callUuid);
        if (!callRecordService.tryFinishCall(callUuid, mapEndReason(hangupCause), "")) {
            log.warn("[FreeSWITCH] hangup for unknown call uuid={} cause={}", callUuid, hangupCause);
        }
    }

    @Override
    public void onRecordStop(String callUuid, String recordFile) {
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "record_stop", Map.of(
                "file", valueOrEmpty(recordFile)
        ));
        FlowSession session = flowExecutor.sessionOf(callUuid);
        if (session == null) {
            log.debug("[FreeSWITCH] record_stop for unknown session uuid={} file={}", callUuid, recordFile);
            return;
        }
        if (!"asr".equals(session.getWaitingFor())) {
            // voicemail / record 节点的录音落地，不需要 ASR 识别
            log.debug("[FreeSWITCH] record_stop ignored uuid={} waitingFor={} file={}",
                    callUuid, session.getWaitingFor(), recordFile);
            return;
        }
        // ASR 走 IO + 远程 HTTP，丢到 ivrAsrExecutor 跑，避免阻塞 ESL 事件线程
        asrExecutor.execute(() -> processAsr(callUuid, recordFile));
    }

    private void processAsr(String callUuid, String recordFile) {
        byte[] audio = readAudio(recordFile);
        String text = "";
        if (audio.length > 0) {
            try {
                String recognized = ttsAsrService.recognize(audio, "wav");
                if (StringUtils.hasText(recognized)) {
                    text = recognized;
                } else {
                    log.warn("[FreeSWITCH] ASR returned empty text uuid={} file={}", callUuid, recordFile);
                }
            } catch (Exception e) {
                log.warn("[FreeSWITCH] ASR recognize failed uuid={} file={} err={}", callUuid, recordFile, e.toString());
            }
        }
        flowExecutor.resumeWithAsr(callUuid, text);
    }

    /** 严格校验 recordFile 落在 recordingRoot 之内、且不超过 maxRecordingBytes。 */
    private byte[] readAudio(String recordFile) {
        if (!StringUtils.hasText(recordFile)) {
            return new byte[0];
        }
        Path resolved;
        try {
            resolved = Path.of(recordFile).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.warn("[FreeSWITCH] invalid record path file=\"{}\" err={}", recordFile, e.toString());
            return new byte[0];
        }
        if (!resolved.startsWith(recordingRoot)) {
            log.warn("[FreeSWITCH] record file outside recording-dir, rejected file=\"{}\" root=\"{}\"",
                    resolved, recordingRoot);
            return new byte[0];
        }
        try {
            long size = Files.size(resolved);
            if (size > maxRecordingBytes) {
                log.warn("[FreeSWITCH] record file too large file=\"{}\" size={} max={}",
                        resolved, size, maxRecordingBytes);
                return new byte[0];
            }
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            log.warn("[FreeSWITCH] read recording failed file=\"{}\" err={}", resolved, e.toString());
            return new byte[0];
        }
    }

    private IvrHotline findHotline(String callee) {
        if (!StringUtils.hasText(callee)) {
            return null;
        }
        return hotlineMapper.selectOne(new LambdaQueryWrapper<IvrHotline>()
                .eq(IvrHotline::getHotline, callee.trim())
                .eq(IvrHotline::getEnabled, 1)
                .last("LIMIT 1"));
    }

    private boolean isPublished(IvrFlow flow) {
        return flow != null
                && Objects.equals(flow.getDeleted(), 0)
                && Objects.equals(flow.getStatus(), 1)
                && Objects.requireNonNullElse(flow.getCurrentVersion(), 0) > 0;
    }

    private void recordRejectedCall(String callUuid, String caller, String callee, String message) {
        callRecordService.startCall(callUuid, caller, callee, null, 0);
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "reject", Map.of(
                "message", message,
                "caller", valueOrEmpty(caller),
                "callee", valueOrEmpty(callee)
        ));
        callRecordService.finishCall(callUuid, "rejected", "");
        log.warn("[FreeSWITCH] reject call uuid={} caller={} callee={} reason={}", callUuid, caller, callee, message);
    }

    private String mapEndReason(String hangupCause) {
        if (!StringUtils.hasText(hangupCause)) {
            return "hangup";
        }
        String normalized = hangupCause.trim().toUpperCase();
        return HANGUP_CAUSE_MAP.getOrDefault(normalized, "hangup");
    }

    private static String trimToMax(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
