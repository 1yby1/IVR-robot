package com.ivr.admin.dto;

public class CallLogListItem {

    private Long id;
    private String callUuid;
    private String caller;
    private String callee;
    private Long flowId;
    private String flowCode;
    private String flowName;
    private Integer flowVersion;
    private String startTime;
    private String answerTime;
    private String endTime;
    private Integer duration;
    private String endReason;
    private String transferTo;
    private String hangupBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getAnswerTime() { return answerTime; }
    public void setAnswerTime(String answerTime) { this.answerTime = answerTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public String getTransferTo() { return transferTo; }
    public void setTransferTo(String transferTo) { this.transferTo = transferTo; }
    public String getHangupBy() { return hangupBy; }
    public void setHangupBy(String hangupBy) { this.hangupBy = hangupBy; }
}
