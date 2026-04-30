package com.ivr.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class KnowledgeDocRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 255, message = "文档标题不能超过 255 个字符")
    private String title;

    @NotBlank(message = "文档内容不能为空")
    @Size(max = 200000, message = "文档内容不能超过 20 万字符")
    private String content;

    @Size(max = 500, message = "来源文件名不能超过 500 个字符")
    private String sourceFile;

    @Size(max = 16, message = "文件类型不能超过 16 个字符")
    private String fileType;

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
}
