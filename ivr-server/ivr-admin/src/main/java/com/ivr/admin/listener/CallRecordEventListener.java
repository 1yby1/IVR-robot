package com.ivr.admin.listener;

import com.ivr.admin.service.CallRecordService;
import com.ivr.engine.event.FlowEventListener;
import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.session.FlowSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 把 engine 的执行事件桥接到 {@code call_event} / {@code call_log} 表。
 *
 * <p>覆盖 engine 自带的 {@code LoggingFlowEventListener}。
 */
@Component
@Primary
public class CallRecordEventListener implements FlowEventListener {

    private static final Logger log = LoggerFactory.getLogger(CallRecordEventListener.class);

    private final CallRecordService callRecordService;

    public CallRecordEventListener(CallRecordService callRecordService) {
        this.callRecordService = callRecordService;
    }

    @Override
    public void onNodeEnter(FlowSession session, FlowGraph.Node node) {
        record(session, node, "enter", Map.of(
                "name", node.name(),
                "bizType", Objects.requireNonNullElse(node.bizType(), "")
        ));
    }

    @Override
    public void onNodeExit(FlowSession session, FlowGraph.Node node, Map<String, Object> payload) {
        record(session, node, "exit", payload == null ? Map.of() : payload);
    }

    @Override
    public void onWait(FlowSession session, FlowGraph.Node node, String waitFor) {
        record(session, node, "wait", Map.of("waitFor", waitFor));
    }

    @Override
    public void onTerminate(FlowSession session, String reason, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        if (message != null) {
            payload.put("message", message);
        }
        FlowGraph.Node node = session.getCurrentNodeId() == null
                ? null
                : session.getGraph().node(session.getCurrentNodeId());
        record(session, node, "terminate", payload);

        try {
            String transferTo = "transfer".equals(reason) && session.getContext() != null
                    ? Objects.toString(session.getContext().getVar("transferTo"), "")
                    : "";
            callRecordService.finishCall(session.getSessionId(), reason, transferTo);
        } catch (Exception e) {
            log.warn("[FlowListener] finishCall failed session={} err={}",
                    session.getSessionId(), e.toString());
        }
    }

    @Override
    public void onError(FlowSession session, FlowGraph.Node node, Throwable error) {
        record(session, node, "error", Map.of(
                "error", Objects.toString(error == null ? "" : error.getMessage(), "")
        ));
    }

    private void record(FlowSession session, FlowGraph.Node node, String eventType, Map<String, Object> payload) {
        try {
            callRecordService.recordEvent(
                    session.getSessionId(),
                    node == null ? "" : node.getId(),
                    node == null ? "" : Objects.requireNonNullElse(node.bizType(), ""),
                    eventType,
                    payload
            );
        } catch (Exception e) {
            log.warn("[FlowListener] recordEvent failed session={} event={} err={}",
                    session.getSessionId(), eventType, e.toString());
        }
    }
}
