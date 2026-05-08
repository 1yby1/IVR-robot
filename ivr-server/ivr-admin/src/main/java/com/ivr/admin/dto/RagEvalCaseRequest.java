package com.ivr.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RagEvalCaseRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @NotBlank(message = "测试问题不能为空")
    @Size(max = 1000, message = "测试问题不能超过 1000 个字符")
    private String question;

    @Size(max = 255, message = "期望文档标题不能超过 255 个字符")
    private String expectedDocTitle;

    @Size(max = 1000, message = "期望关键词不能超过 1000 个字符")
    private String expectedKeywords;

    private Boolean shouldFallback = false;
    private Boolean enabled = true;

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getExpectedDocTitle() { return expectedDocTitle; }
    public void setExpectedDocTitle(String expectedDocTitle) { this.expectedDocTitle = expectedDocTitle; }
    public String getExpectedKeywords() { return expectedKeywords; }
    public void setExpectedKeywords(String expectedKeywords) { this.expectedKeywords = expectedKeywords; }
    public Boolean getShouldFallback() { return shouldFallback; }
    public void setShouldFallback(Boolean shouldFallback) { this.shouldFallback = shouldFallback; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
