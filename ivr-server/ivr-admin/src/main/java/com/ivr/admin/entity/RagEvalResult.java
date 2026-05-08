package com.ivr.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_eval_result")
public class RagEvalResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long caseId;
    private String question;
    private String retrievedChunks;
    private String answer;
    private Integer hitExpectedDoc;
    private Integer keywordPassed;
    private Integer fallbackPassed;
    private Integer passed;
    private String failReason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getRetrievedChunks() { return retrievedChunks; }
    public void setRetrievedChunks(String retrievedChunks) { this.retrievedChunks = retrievedChunks; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Integer getHitExpectedDoc() { return hitExpectedDoc; }
    public void setHitExpectedDoc(Integer hitExpectedDoc) { this.hitExpectedDoc = hitExpectedDoc; }
    public Integer getKeywordPassed() { return keywordPassed; }
    public void setKeywordPassed(Integer keywordPassed) { this.keywordPassed = keywordPassed; }
    public Integer getFallbackPassed() { return fallbackPassed; }
    public void setFallbackPassed(Integer fallbackPassed) { this.fallbackPassed = fallbackPassed; }
    public Integer getPassed() { return passed; }
    public void setPassed(Integer passed) { this.passed = passed; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
