package com.ivr.ai.rag;

import java.util.List;

/**
 * 知识库检索抽象。RagNodeHandler 通过本接口取检索片段，再交给 LLM 生成回答。
 *
 * <p>容器中的默认实现是 {@link DatabaseKnowledgeService}（向量 + 关键词混合检索）。
 * 接入向量库（Milvus / pgvector）时仅需提供新的实现 Bean 替换它。
 *
 * <p>{@link InMemoryKnowledgeService} 现仅供单测使用，已不再注册为 Bean。
 */
public interface KnowledgeService {

    /**
     * 检索与问题最相关的 topK 个文本片段。
     *
     * @param kbId     知识库 id；null 表示默认全局库
     * @param question 用户问题
     * @param topK     最多返回片段数；&lt;=0 时按实现默认
     * @return 命中片段；未命中返回空列表
     */
    List<KnowledgeChunk> retrieve(Long kbId, String question, int topK);

    record KnowledgeChunk(String docId, String title, String content, double score) {}
}
