package com.ivr.admin.dto;

public class CallEventItem {

    private Long id;
    private String callUuid;
    private String nodeKey;
    private String nodeType;
    private String eventType;
    private String payload;
    private String eventTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCallUuid() { return callUuid; }
    public void setCallUuid(String callUuid) { this.callUuid = callUuid; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }
}
