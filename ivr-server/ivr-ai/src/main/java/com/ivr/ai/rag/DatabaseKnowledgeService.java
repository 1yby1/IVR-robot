package com.ivr.ai.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.ai.rag.entity.KbChunk;
import com.ivr.ai.rag.mapper.KbChunkMapper;
import com.ivr.ai.rag.mapper.KbDocMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseKnowledgeService implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseKnowledgeService.class);
    private static final int DEFAULT_TOP_K = 3;
    private static final int MAX_SCAN_CHUNKS = 1000;

    private final KbChunkMapper chunkMapper;
    private final KbDocMapper docMapper;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final ObjectMapper objectMapper;

    public DatabaseKnowledgeService(KbChunkMapper chunkMapper,
                                    KbDocMapper docMapper,
                                    ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                    ObjectMapper objectMapper) {
        this.chunkMapper = chunkMapper;
        this.docMapper = docMapper;
        this.embeddingModelProvider = embeddingModelProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<KnowledgeChunk> retrieve(Long kbId, String question, int topK) {
        int limit = topK > 0 ? topK : DEFAULT_TOP_K;
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        List<KbChunk> chunks = loadChunks(kbId);
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<String> keywords = KnowledgeTextUtils.tokenize(question);
        float[] queryVector = embedQuestion(question);
        List<ScoredChunk> scored = new ArrayList<>();
        for (KbChunk chunk : chunks) {
            double score = scoreChunk(chunk, keywords, queryVector);
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        Map<Long, String> titles = docTitles(scored.stream().map(s -> s.chunk().getDocId()).distinct().toList());
        return scored.stream()
                .limit(limit)
                .map(item -> new KnowledgeChunk(
                        String.valueOf(item.chunk().getDocId()),
                        titles.getOrDefault(item.chunk().getDocId(), "知识片段"),
                        item.chunk().getContent(),
                        item.score()))
                .toList();
    }

    private List<KbChunk> loadChunks(Long kbId) {
        LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<KbChunk>()
                .isNotNull(KbChunk::getContent)
                .orderByDesc(KbChunk::getId)
                .last("LIMIT " + MAX_SCAN_CHUNKS);
        if (kbId != null) {
            wrapper.eq(KbChunk::getKbId, kbId);
        }
        return chunkMapper.selectList(wrapper);
    }

    private float[] embedQuestion(String question) {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            return null;
        }
        try {
            return embeddingModel.embed(question);
        } catch (Exception e) {
            log.warn("query embedding failed, fallback to keyword search: {}", e.toString());
            return null;
        }
    }

    private double scoreChunk(KbChunk chunk, List<String> keywords, float[] queryVector) {
        double keyword = KnowledgeTextUtils.scoreContent(chunk.getContent(), keywords);
        if (queryVector != null && StringUtils.hasText(chunk.getEmbedding())) {
            float[] chunkVector = readVector(chunk.getEmbedding());
            double similarity = cosine(queryVector, chunkVector);
            if (similarity > 0) {
                return similarity + keyword * 0.001;
            }
        }
        return keyword;
    }

    private float[] readVector(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private Map<Long, String> docTitles(List<Long> docIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (docIds.isEmpty()) {
            return result;
        }
        docMapper.selectBatchIds(docIds).forEach(doc -> result.put(doc.getId(), doc.getTitle()));
        return result;
    }

    private record ScoredChunk(KbChunk chunk, double score) {}
}
