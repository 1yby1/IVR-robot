package com.ivr.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class FlowHealthResponse {

    private Long flowId;
    private String flowName;
    private Integer score;
    private String grade;
    private String summary;
    private RuntimeStats runtimeStats = new RuntimeStats();
    private List<Issue> issues = new ArrayList<>();
    private List<NodeStat> nodes = new ArrayList<>();

    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public RuntimeStats getRuntimeStats() { return runtimeStats; }
    public void setRuntimeStats(RuntimeStats runtimeStats) { this.runtimeStats = runtimeStats; }
    public List<Issue> getIssues() { return issues; }
    public void setIssues(List<Issue> issues) { this.issues = issues; }
    public List<NodeStat> getNodes() { return nodes; }
    public void setNodes(List<NodeStat> nodes) { this.nodes = nodes; }

    public static class RuntimeStats {
        private Integer sampleCalls = 0;
        private Integer endedCalls = 0;
        private Integer runningCalls = 0;
        private Integer transferCalls = 0;
        private Integer errorCalls = 0;
        private Integer timeoutCalls = 0;
        private Integer avgDurationSeconds = 0;

        public Integer getSampleCalls() { return sampleCalls; }
        public void setSampleCalls(Integer sampleCalls) { this.sampleCalls = sampleCalls; }
        public Integer getEndedCalls() { return endedCalls; }
        public void setEndedCalls(Integer endedCalls) { this.endedCalls = endedCalls; }
        public Integer getRunningCalls() { return runningCalls; }
        public void setRunningCalls(Integer runningCalls) { this.runningCalls = runningCalls; }
        public Integer getTransferCalls() { return transferCalls; }
        public void setTransferCalls(Integer transferCalls) { this.transferCalls = transferCalls; }
        public Integer getErrorCalls() { return errorCalls; }
        public void setErrorCalls(Integer errorCalls) { this.errorCalls = errorCalls; }
        public Integer getTimeoutCalls() { return timeoutCalls; }
        public void setTimeoutCalls(Integer timeoutCalls) { this.timeoutCalls = timeoutCalls; }
        public Integer getAvgDurationSeconds() { return avgDurationSeconds; }
        public void setAvgDurationSeconds(Integer avgDurationSeconds) { this.avgDurationSeconds = avgDurationSeconds; }
    }

    public static class Issue {
        private String level;
        private String category;
        private String nodeId;
        private String nodeName;
        private String message;
        private String suggestion;

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    }

    public static class NodeStat {
        private String nodeId;
        private String nodeName;
        private String nodeType;
        private Integer incoming = 0;
        private Integer outgoing = 0;
        private Integer enterCount = 0;
        private Integer errorCount = 0;
        private Integer fallbackCount = 0;
        private Integer aiHitCount = 0;
        private Integer transferCount = 0;
        private Double successRate;
        private String healthLevel = "info";

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getNodeType() { return nodeType; }
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }
        public Integer getIncoming() { return incoming; }
        public void setIncoming(Integer incoming) { this.incoming = incoming; }
        public Integer getOutgoing() { return outgoing; }
        public void setOutgoing(Integer outgoing) { this.outgoing = outgoing; }
        public Integer getEnterCount() { return enterCount; }
        public void setEnterCount(Integer enterCount) { this.enterCount = enterCount; }
        public Integer getErrorCount() { return errorCount; }
        public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
        public Integer getFallbackCount() { return fallbackCount; }
        public void setFallbackCount(Integer fallbackCount) { this.fallbackCount = fallbackCount; }
        public Integer getAiHitCount() { return aiHitCount; }
        public void setAiHitCount(Integer aiHitCount) { this.aiHitCount = aiHitCount; }
        public Integer getTransferCount() { return transferCount; }
        public void setTransferCount(Integer transferCount) { this.transferCount = transferCount; }
        public Double getSuccessRate() { return successRate; }
        public void setSuccessRate(Double successRate) { this.successRate = successRate; }
        public String getHealthLevel() { return healthLevel; }
        public void setHealthLevel(String healthLevel) { this.healthLevel = healthLevel; }
    }
}
