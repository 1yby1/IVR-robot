package com.ivr.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_eval_run")
public class RagEvalRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Integer topK;
    private Integer generateAnswer;
    private Integer totalCount;
    private Integer passedCount;
    private Integer passRate;
    private Integer hitRate;
    private Integer keywordPassRate;
    private Integer fallbackPassRate;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public Integer getGenerateAnswer() { return generateAnswer; }
    public void setGenerateAnswer(Integer generateAnswer) { this.generateAnswer = generateAnswer; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public Integer getPassedCount() { return passedCount; }
    public void setPassedCount(Integer passedCount) { this.passedCount = passedCount; }
    public Integer getPassRate() { return passRate; }
    public void setPassRate(Integer passRate) { this.passRate = passRate; }
    public Integer getHitRate() { return hitRate; }
    public void setHitRate(Integer hitRate) { this.hitRate = hitRate; }
    public Integer getKeywordPassRate() { return keywordPassRate; }
    public void setKeywordPassRate(Integer keywordPassRate) { this.keywordPassRate = keywordPassRate; }
    public Integer getFallbackPassRate() { return fallbackPassRate; }
    public void setFallbackPassRate(Integer fallbackPassRate) { this.fallbackPassRate = fallbackPassRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
