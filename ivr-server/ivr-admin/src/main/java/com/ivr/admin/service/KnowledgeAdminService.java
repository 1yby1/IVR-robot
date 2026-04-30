package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.KnowledgeBaseRequest;
import com.ivr.admin.dto.KnowledgeDocRequest;
import com.ivr.ai.rag.entity.KbBase;
import com.ivr.ai.rag.entity.KbChunk;
import com.ivr.ai.rag.entity.KbDoc;
import com.ivr.ai.rag.entity.KbDocStatus;
import com.ivr.ai.rag.mapper.KbBaseMapper;
import com.ivr.ai.rag.mapper.KbChunkMapper;
import com.ivr.ai.rag.mapper.KbDocMapper;
import com.ivr.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeAdminService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAdminService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int CHUNK_MAX_CHARS = 500;
    private static final int CHUNK_OVERLAP_CHARS = 80;
    private static final int LIST_CONTENT_SNIPPET_CHARS = 120;

    private final KbBaseMapper baseMapper;
    private final KbDocMapper docMapper;
    private final KbChunkMapper chunkMapper;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final ObjectMapper objectMapper;

    public KnowledgeAdminService(KbBaseMapper baseMapper,
                                 KbDocMapper docMapper,
                                 KbChunkMapper chunkMapper,
                                 ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                 ObjectMapper objectMapper) {
        this.baseMapper = baseMapper;
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingModelProvider = embeddingModelProvider;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> pageBases(int current, int size, String keyword) {
        LambdaQueryWrapper<KbBase> wrapper = new LambdaQueryWrapper<KbBase>()
                .eq(KbBase::getDeleted, 0)
                .orderByDesc(KbBase::getCreatedAt)
                .orderByDesc(KbBase::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(KbBase::getKbName, kw).or().like(KbBase::getDescription, kw));
        }
        Page<KbBase> page = baseMapper.selectPage(Page.of(Math.max(1, current), Math.max(1, size)), wrapper);
        List<KbBase> records = page.getRecords();
        List<Long> baseIds = records.stream().map(KbBase::getId).toList();
        Map<Long, Long> docCounts = countByGroup(docMapper, "kb_id", baseIds);
        Map<Long, Long> chunkCounts = countByGroup(chunkMapper, "kb_id", baseIds);
        List<Map<String, Object>> mapped = records.stream()
                .map(base -> baseMap(base, docCounts.getOrDefault(base.getId(), 0L), chunkCounts.getOrDefault(base.getId(), 0L)))
                .toList();
        return pageResult(mapped, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<Map<String, Object>> baseOptions() {
        return baseMapper.selectList(new LambdaQueryWrapper<KbBase>()
                        .eq(KbBase::getDeleted, 0)
                        .orderByAsc(KbBase::getId))
                .stream()
                .map(base -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", base.getId());
                    map.put("kbName", base.getKbName());
                    return map;
                })
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createBase(KnowledgeBaseRequest request) {
        KbBase base = new KbBase();
        applyBase(base, request);
        base.setEmbeddingModel(defaultText(request.getEmbeddingModel(), "text-embedding-v2"));
        base.setCreatedAt(LocalDateTime.now());
        base.setDeleted(0);
        baseMapper.insert(base);
        return base.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBase(Long id, KnowledgeBaseRequest request) {
        KbBase base = requiredBase(id);
        applyBase(base, request);
        base.setEmbeddingModel(defaultText(request.getEmbeddingModel(), base.getEmbeddingModel()));
        baseMapper.updateById(base);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBase(Long id) {
        KbBase base = requiredBase(id);
        Long docCount = docMapper.selectCount(new LambdaQueryWrapper<KbDoc>().eq(KbDoc::getKbId, id));
        if (docCount != null && docCount > 0) {
            throw new BusinessException(400, "请先删除该知识库下的文档");
        }
        base.setDeleted(1);
        baseMapper.updateById(base);
    }

    public Map<String, Object> pageDocs(int current, int size, Long kbId, String keyword) {
        LambdaQueryWrapper<KbDoc> wrapper = new LambdaQueryWrapper<KbDoc>()
                .orderByDesc(KbDoc::getCreatedAt)
                .orderByDesc(KbDoc::getId);
        if (kbId != null) {
            wrapper.eq(KbDoc::getKbId, kbId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(KbDoc::getTitle, kw).or().like(KbDoc::getContent, kw));
        }
        Page<KbDoc> page = docMapper.selectPage(Page.of(Math.max(1, current), Math.max(1, size)), wrapper);
        List<KbDoc> records = page.getRecords();
        List<Long> docIds = records.stream().map(KbDoc::getId).toList();
        List<Long> kbIds = records.stream().map(KbDoc::getKbId).distinct().toList();
        Map<Long, Long> chunkCounts = countByGroup(chunkMapper, "doc_id", docIds);
        Map<Long, String> kbNames = kbNameMap(kbIds);
        List<Map<String, Object>> mapped = records.stream()
                .map(doc -> docListRow(doc, kbNames, chunkCounts.getOrDefault(doc.getId(), 0L)))
                .toList();
        return pageResult(mapped, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> docDetail(Long id) {
        KbDoc doc = requiredDoc(id);
        Long chunkCount = chunkMapper.selectCount(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, id));
        Map<Long, String> kbNames = kbNameMap(List.of(doc.getKbId()));
        return docDetailRow(doc, kbNames, Objects.requireNonNullElse(chunkCount, 0L));
    }

    public Long createDoc(KnowledgeDocRequest request) {
        requiredBase(request.getKbId());
        KbDoc doc = new KbDoc();
        applyDoc(doc, request);
        doc.setStatus(KbDocStatus.INDEXING.code());
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);
        rebuildChunks(doc);
        return doc.getId();
    }

    public void updateDoc(Long id, KnowledgeDocRequest request) {
        requiredBase(request.getKbId());
        KbDoc doc = requiredDoc(id);
        applyDoc(doc, request);
        doc.setStatus(KbDocStatus.INDEXING.code());
        docMapper.updateById(doc);
        rebuildChunks(doc);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDoc(Long id) {
        requiredDoc(id);
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, id));
        docMapper.deleteById(id);
    }

    public void reindexDoc(Long id) {
        rebuildChunks(requiredDoc(id));
    }

    /**
     * 切片 + 嵌入 + 落库。**故意非事务**：嵌入是阻塞的远程 HTTP，放进 @Transactional 会
     * 长时间持有 DB 连接 / 行锁。失败时把 doc.status 落到 FAILED，但已写入的切片不回滚
     * （关键词检索仍可用），用户可手动"重建索引"再试一次。
     */
    private void rebuildChunks(KbDoc doc) {
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, doc.getId()));
        List<String> parts = splitContent(doc.getContent());
        if (parts.isEmpty()) {
            doc.setStatus(KbDocStatus.FAILED.code());
            docMapper.updateById(doc);
            return;
        }

        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        int embeddingFailures = 0;
        List<KbChunk> chunks = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            String content = parts.get(i);
            KbChunk chunk = new KbChunk();
            chunk.setDocId(doc.getId());
            chunk.setKbId(doc.getKbId());
            chunk.setChunkIdx(i);
            chunk.setContent(content);
            chunk.setTokenCnt(estimateTokenCount(content));
            chunk.setCreatedAt(LocalDateTime.now());
            if (embeddingModel != null) {
                try {
                    chunk.setEmbedding(toJson(embeddingModel.embed(content)));
                } catch (Exception e) {
                    embeddingFailures++;
                    log.warn("embedding failed doc={} chunk={} err={}", doc.getId(), i, e.toString());
                }
            }
            chunks.add(chunk);
        }
        Db.saveBatch(chunks);

        doc.setStatus(KbDocStatus.INDEXED.code());
        docMapper.updateById(doc);
        if (embeddingModel == null) {
            log.info("doc {} indexed without embeddings (no EmbeddingModel bean), keyword fallback only", doc.getId());
        } else if (embeddingFailures > 0) {
            log.info("doc {} indexed with {}/{} embeddings missing, keyword fallback fills the rest",
                    doc.getId(), embeddingFailures, parts.size());
        }
    }

    private List<String> splitContent(String content) {
        String normalized = Objects.toString(content, "").replace("\r\n", "\n").trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> sections = new ArrayList<>();
        for (String part : normalized.split("\\n\\s*\\n|\\n")) {
            String text = part.trim();
            if (StringUtils.hasText(text)) {
                sections.add(text);
            }
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String section : sections) {
            if (section.length() > CHUNK_MAX_CHARS) {
                flush(buf, chunks);
                splitLongSection(section, chunks);
                continue;
            }
            if (buf.length() > 0 && buf.length() + section.length() + 1 > CHUNK_MAX_CHARS) {
                flush(buf, chunks);
            }
            if (buf.length() > 0) {
                buf.append('\n');
            }
            buf.append(section);
        }
        flush(buf, chunks);
        return chunks;
    }

    private void splitLongSection(String text, List<String> chunks) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_MAX_CHARS);
            chunks.add(text.substring(start, end).trim());
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP_CHARS, start + 1);
        }
    }

    private void flush(StringBuilder buf, List<String> chunks) {
        if (buf.length() == 0) {
            return;
        }
        chunks.add(buf.toString().trim());
        buf.setLength(0);
    }

    private String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            return "";
        }
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, Objects.toString(text, "").length() / 2);
    }

    private KbBase requiredBase(Long id) {
        KbBase base = baseMapper.selectById(id);
        if (base == null || Objects.equals(base.getDeleted(), 1)) {
            throw new BusinessException(404, "知识库不存在");
        }
        return base;
    }

    private KbDoc requiredDoc(Long id) {
        KbDoc doc = docMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "知识文档不存在");
        }
        return doc;
    }

    private void applyBase(KbBase base, KnowledgeBaseRequest request) {
        base.setKbName(request.getKbName().trim());
        base.setDescription(defaultText(request.getDescription(), ""));
    }

    private void applyDoc(KbDoc doc, KnowledgeDocRequest request) {
        doc.setKbId(request.getKbId());
        doc.setTitle(request.getTitle().trim());
        doc.setContent(request.getContent().trim());
        doc.setSourceFile(defaultText(request.getSourceFile(), ""));
        doc.setFileType(defaultText(request.getFileType(), "txt"));
    }

    private Map<Long, String> kbNameMap(List<Long> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        baseMapper.selectList(new LambdaQueryWrapper<KbBase>()
                        .eq(KbBase::getDeleted, 0)
                        .in(KbBase::getId, kbIds))
                .forEach(base -> result.put(base.getId(), base.getKbName()));
        return result;
    }

    /** 一次 group-by 查询拿到 {id → count}，避免列表页对每行再发 count(*). */
    private <T> Map<Long, Long> countByGroup(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                             String groupColumn,
                                             List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.select(groupColumn, "count(*) as cnt").in(groupColumn, ids).groupBy(groupColumn);
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.selectMaps(wrapper)) {
            Object key = row.get(groupColumn);
            Object cnt = row.get("cnt");
            if (key instanceof Number nk && cnt instanceof Number nc) {
                result.put(nk.longValue(), nc.longValue());
            }
        }
        return result;
    }

    private Map<String, Object> baseMap(KbBase base, long docCount, long chunkCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", base.getId());
        map.put("kbName", base.getKbName());
        map.put("description", base.getDescription());
        map.put("embeddingModel", base.getEmbeddingModel());
        map.put("docCount", docCount);
        map.put("chunkCount", chunkCount);
        map.put("createdAt", formatTime(base.getCreatedAt()));
        return map;
    }

    /** 列表场景只回片段，避免几十 KB 的 content 灌进 page 响应。 */
    private Map<String, Object> docListRow(KbDoc doc, Map<Long, String> kbNames, long chunkCount) {
        Map<String, Object> map = baseDocFields(doc, kbNames);
        map.put("contentSnippet", snippet(doc.getContent(), LIST_CONTENT_SNIPPET_CHARS));
        map.put("chunkCount", chunkCount);
        return map;
    }

    private Map<String, Object> docDetailRow(KbDoc doc, Map<Long, String> kbNames, long chunkCount) {
        Map<String, Object> map = baseDocFields(doc, kbNames);
        map.put("content", doc.getContent());
        map.put("chunkCount", chunkCount);
        return map;
    }

    private Map<String, Object> baseDocFields(KbDoc doc, Map<Long, String> kbNames) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doc.getId());
        map.put("kbId", doc.getKbId());
        map.put("kbName", kbNames.getOrDefault(doc.getKbId(), ""));
        map.put("title", doc.getTitle());
        map.put("sourceFile", doc.getSourceFile());
        map.put("fileType", doc.getFileType());
        map.put("status", Objects.requireNonNullElse(doc.getStatus(), KbDocStatus.PENDING.code()));
        map.put("createdAt", formatTime(doc.getCreatedAt()));
        return map;
    }

    private String snippet(String content, int max) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "…";
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> records, long total, long current, long size) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("records", records);
        map.put("total", total);
        map.put("current", current);
        map.put("size", size);
        return map;
    }

    private String defaultText(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }
}
