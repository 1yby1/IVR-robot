package com.ivr.engine.session;

import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.node.FlowContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次流程执行的会话状态。生命周期 = 一通通话。
 *
 * <p>对真实呼叫场景，{@link #sessionId} = FreeSWITCH callUuid；
 * 模拟调试场景可以使用任意唯一字符串。
 */
public class FlowSession {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_ENDED = "ended";

    private String sessionId;
    private Long flowId;
    private Integer flowVersion;
    private FlowGraph graph;
    private FlowContext context;
    private String currentNodeId;
    private String status = STATUS_RUNNING;
    /** 等待的事件类型："dtmf" / "asr" / null */
    private String waitingFor;
    private String terminateReason;
    private String errorMsg;
    private int stepCount;
    private final LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime endedAt;
    private final List<String> visitedNodes = new ArrayList<>();

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Integer getFlowVersion() { return flowVersion; }
    public void setFlowVersion(Integer flowVersion) { this.flowVersion = flowVersion; }
    public FlowGraph getGraph() { return graph; }
    public void setGraph(FlowGraph graph) { this.graph = graph; }
    public FlowContext getContext() { return context; }
    public void setContext(FlowContext context) { this.context = context; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWaitingFor() { return waitingFor; }
    public void setWaitingFor(String waitingFor) { this.waitingFor = waitingFor; }
    public String getTerminateReason() { return terminateReason; }
    public void setTerminateReason(String terminateReason) { this.terminateReason = terminateReason; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public int getStepCount() { return stepCount; }
    public void incrementStepCount() { this.stepCount++; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public List<String> getVisitedNodes() { return visitedNodes; }

    public boolean isWaiting() {
        return STATUS_WAITING.equals(status);
    }

    public boolean isEnded() {
        return STATUS_ENDED.equals(status);
    }
}
