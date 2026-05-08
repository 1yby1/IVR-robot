package com.ivr.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class KnowledgeChunkPreviewRequest {

    @NotBlank(message = "文档内容不能为空")
    @Size(max = 200000, message = "文档内容不能超过 20 万字符")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
