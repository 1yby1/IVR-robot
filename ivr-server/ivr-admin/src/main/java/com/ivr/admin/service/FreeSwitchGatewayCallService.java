package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.entity.IvrHotline;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.admin.mapper.IvrHotlineMapper;
import com.ivr.call.esl.GatewayCallService;
import com.ivr.common.exception.BusinessException;
import com.ivr.engine.FlowExecutor;
import com.ivr.engine.node.FlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

@Service
public class FreeSwitchGatewayCallService implements GatewayCallService {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchGatewayCallService.class);

    private final IvrHotlineMapper hotlineMapper;
    private final IvrFlowMapper flowMapper;
    private final FlowExecutor flowExecutor;
    private final CallRecordService callRecordService;

    public FreeSwitchGatewayCallService(IvrHotlineMapper hotlineMapper,
                                        IvrFlowMapper flowMapper,
                                        FlowExecutor flowExecutor,
                                        CallRecordService callRecordService) {
        this.hotlineMapper = hotlineMapper;
        this.flowMapper = flowMapper;
        this.flowExecutor = flowExecutor;
        this.callRecordService = callRecordService;
    }

    @Override
    public void onInboundCall(String callUuid, String caller, String callee) {
        IvrHotline hotline = findHotline(callee);
        if (hotline == null) {
            recordRejectedCall(callUuid, caller, callee, "No enabled hotline binding");
            return;
        }

        IvrFlow flow = flowMapper.selectById(hotline.getFlowId());
        if (!isPublished(flow)) {
            recordRejectedCall(callUuid, caller, callee, "Hotline flow is not published");
            return;
        }

        callRecordService.startCall(callUuid, caller, callee, flow.getId(), flow.getCurrentVersion());
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "inbound", Map.of(
                "caller", valueOrEmpty(caller),
                "callee", valueOrEmpty(callee),
                "hotlineId", hotline.getId(),
                "flowId", flow.getId(),
                "flowVersion", Objects.requireNonNullElse(flow.getCurrentVersion(), 0)
        ));

        FlowContext context = new FlowContext();
        context.setCallUuid(callUuid);
        context.setCaller(caller);
        context.setCallee(callee);
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
    }

    @Override
    public void onDtmf(String callUuid, String digit) {
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "dtmf", Map.of(
                "digit", valueOrEmpty(digit)
        ));
    }

    @Override
    public void onHangup(String callUuid, String hangupCause) {
        callRecordService.recordEvent(callUuid, "gateway", "gateway", "hangup", Map.of(
                "cause", valueOrEmpty(hangupCause)
        ));
        try {
            callRecordService.finishCall(callUuid, mapEndReason(hangupCause), "");
        } catch (BusinessException e) {
            log.warn("[FreeSWITCH] hangup for unknown call uuid={} cause={}", callUuid, hangupCause);
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
        if ("NORMAL_CLEARING".equals(normalized)) {
            return "completed";
        }
        if ("ORIGINATOR_CANCEL".equals(normalized) || "NO_ANSWER".equals(normalized)) {
            return "abandoned";
        }
        return normalized.toLowerCase();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
