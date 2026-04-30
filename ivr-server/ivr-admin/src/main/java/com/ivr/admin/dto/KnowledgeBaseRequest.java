package com.ivr.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class KnowledgeBaseRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称不能超过 128 个字符")
    private String kbName;

    @Size(max = 500, message = "知识库描述不能超过 500 个字符")
    private String description;

    @Size(max = 64, message = "Embedding 模型名不能超过 64 个字符")
    private String embeddingModel;

    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
}
