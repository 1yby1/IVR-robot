package com.ivr.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("call_log")
public class CallLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String callUuid;
    private String caller;
    private String callee;
    private Long flowId;
    private Integer flowVersion;
    private LocalDateTime startTime;
    private LocalDateTime answerTime;
    private LocalDateTime endTime;
    private Integer duration;
    private String endReason;
    private String transferTo;
    private String hangupBy;
    private Integer aiHit;
    private Integer satisfaction;

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
    public Integer getFlowVersion() { return flowVersion; }
    public void setFlowVersion(Integer flowVersion) { this.flowVersion = flowVersion; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getAnswerTime() { return answerTime; }
    public void setAnswerTime(LocalDateTime answerTime) { this.answerTime = answerTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getEndReason() { return endReason; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public String getTransferTo() { return transferTo; }
    public void setTransferTo(String transferTo) { this.transferTo = transferTo; }
    public String getHangupBy() { return hangupBy; }
    public void setHangupBy(String hangupBy) { this.hangupBy = hangupBy; }
    public Integer getAiHit() { return aiHit; }
    public void setAiHit(Integer aiHit) { this.aiHit = aiHit; }
    public Integer getSatisfaction() { return satisfaction; }
    public void setSatisfaction(Integer satisfaction) { this.satisfaction = satisfaction; }
}
