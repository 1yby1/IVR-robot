package com.ivr.engine.event;

import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.session.FlowSession;

import java.util.Map;

/**
 * 流程执行事件监听。每一步节点进出、暂停、终止都会触发对应回调。
 *
 * <p>默认实现 {@link LoggingFlowEventListener} 仅打印日志；admin 模块通过
 * {@code CallRecordEventListener} 把事件桥接到 {@code call_event} 表。
 */
public interface FlowEventListener {

    /** 进入节点（即将执行 handler） */
    default void onNodeEnter(FlowSession session, FlowGraph.Node node) {}

    /** 节点执行完成（已得到 NodeResult） */
    default void onNodeExit(FlowSession session, FlowGraph.Node node, Map<String, Object> payload) {}

    /** 节点要求暂停等待外部事件（dtmf / asr） */
    default void onWait(FlowSession session, FlowGraph.Node node, String waitFor) {}

    /** 流程终止：reason in [normal, transfer, voicemail, hangup, error, dtmf-no-match, ...] */
    default void onTerminate(FlowSession session, String reason, String message) {}

    /** 节点执行抛异常，便于排错。executor 内部还会触发 onTerminate("error")。 */
    default void onError(FlowSession session, FlowGraph.Node node, Throwable error) {}
}
