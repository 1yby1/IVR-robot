package com.ivr.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class CallReplayResponse {

    private String callUuid;
    private String caller;
    private String callee;
    private Long flowId;
    private String flowCode;
    private String flowName;
    private Integer flowVersion;
    private String startTime;
    private String endTime;
    private Integer duration;
    private String endReason;
    private String transferTo;
    private List<PathStep> path = new ArrayList<>();
    private List<Event> events = new ArrayList<>();

    public String getCallUuid() { return callUuid; }
    public void setCallUuid(String callUuid) { this.callUuid = callUuid; }
    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }
    public String getCallee() { return callee; }
    public void setCallee(String callee) { this.callee = callee; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }
    public Integer getFlowVersion() { return flowVersion; }
    public void setFlowVersion(Integer flowVersion) { this.flowVersion = flowVersion; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public String getTransferTo() { return transferTo; }
    public void setTransferTo(String transferTo) { this.transferTo = transferTo; }
    public List<PathStep> getPath() { return path; }
    public void setPath(List<PathStep> path) { this.path = path; }
    public List<Event> getEvents() { return events; }
    public void setEvents(List<Event> events) { this.events = events; }

    public static class PathStep {
        private Integer stepNo;
        private String nodeKey;
        private String nodeName;
        private String nodeType;
        private String eventTime;
        private String level;
        private String summary;

        public Integer getStepNo() { return stepNo; }
        public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
        public String getNodeKey() { return nodeKey; }
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public String getNodeType() { return nodeType; }
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }
        public String getEventTime() { return eventTime; }
        public void setEventTime(String eventTime) { this.eventTime = eventTime; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }

    public static class Event {
        private Long id;
        private String nodeKey;
        private String nodeType;
        private String eventType;
        private String eventTime;
        private String level;
        private String summary;
        private String payload;
        private String payloadPretty;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNodeKey() { return nodeKey; }
        public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
        public String getNodeType() { return nodeType; }
        public void setNodeType(String nodeType) { this.nodeType = nodeType; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getEventTime() { return eventTime; }
        public void setEventTime(String eventTime) { this.eventTime = eventTime; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getPayloadPretty() { return payloadPretty; }
        public void setPayloadPretty(String payloadPretty) { this.payloadPretty = payloadPretty; }
    }
}
