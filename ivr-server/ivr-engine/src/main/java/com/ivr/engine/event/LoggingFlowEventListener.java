package com.ivr.engine.event;

import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.session.FlowSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认监听器：仅写日志。admin 模块提供 {@code CallRecordEventListener} 时该 bean 自动让位。
 */
@Component
@ConditionalOnMissingBean(FlowEventListener.class)
public class LoggingFlowEventListener implements FlowEventListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingFlowEventListener.class);

    @Override
    public void onNodeEnter(FlowSession session, FlowGraph.Node node) {
        log.info("[Flow] enter session={} node={} type={}", session.getSessionId(), node.getId(), node.bizType());
    }

    @Override
    public void onNodeExit(FlowSession session, FlowGraph.Node node, Map<String, Object> payload) {
        log.info("[Flow] exit  session={} node={} payload={}", session.getSessionId(), node.getId(), payload);
    }

    @Override
    public void onWait(FlowSession session, FlowGraph.Node node, String waitFor) {
        log.info("[Flow] wait  session={} node={} waitFor={}", session.getSessionId(), node.getId(), waitFor);
    }

    @Override
    public void onTerminate(FlowSession session, String reason, String message) {
        log.info("[Flow] end   session={} reason={} msg={}", session.getSessionId(), reason, message);
    }

    @Override
    public void onError(FlowSession session, FlowGraph.Node node, Throwable error) {
        log.error("[Flow] error session={} node={} err={}",
                session.getSessionId(),
                node == null ? "" : node.getId(),
                error.toString(),
                error);
    }
}
