package com.ivr.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RagEvalRunRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @Min(value = 1, message = "topK 不能小于 1")
    @Max(value = 10, message = "topK 不能大于 10")
    private Integer topK = 3;

    private Boolean generateAnswer = true;

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public Boolean getGenerateAnswer() { return generateAnswer; }
    public void setGenerateAnswer(Boolean generateAnswer) { this.generateAnswer = generateAnswer; }
}
