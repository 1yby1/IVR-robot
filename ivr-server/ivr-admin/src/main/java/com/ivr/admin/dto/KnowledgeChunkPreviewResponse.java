package com.ivr.admin.dto;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeChunkPreviewResponse {

    private Integer totalCount;
    private Integer totalChars;
    private Integer totalTokens;
    private List<Chunk> chunks = new ArrayList<>();

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getTotalChars() { return totalChars; }
    public void setTotalChars(Integer totalChars) { this.totalChars = totalChars; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public List<Chunk> getChunks() { return chunks; }
    public void setChunks(List<Chunk> chunks) { this.chunks = chunks; }

    public static class Chunk {
        private Integer index;
        private String content;
        private Integer charCount;
        private Integer tokenCount;

        public Integer getIndex() { return index; }
        public void setIndex(Integer index) { this.index = index; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Integer getCharCount() { return charCount; }
        public void setCharCount(Integer charCount) { this.charCount = charCount; }
        public Integer getTokenCount() { return tokenCount; }
        public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    }
}
