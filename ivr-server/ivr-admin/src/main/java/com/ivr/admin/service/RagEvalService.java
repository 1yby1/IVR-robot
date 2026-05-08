package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.RagEvalCaseRequest;
import com.ivr.admin.dto.RagEvalRunRequest;
import com.ivr.admin.entity.RagEvalCase;
import com.ivr.admin.entity.RagEvalResult;
import com.ivr.admin.entity.RagEvalRun;
import com.ivr.admin.mapper.RagEvalCaseMapper;
import com.ivr.admin.mapper.RagEvalResultMapper;
import com.ivr.admin.mapper.RagEvalRunMapper;
import com.ivr.ai.LlmService;
import com.ivr.ai.rag.KnowledgeService;
import com.ivr.ai.rag.entity.KbBase;
import com.ivr.ai.rag.mapper.KbBaseMapper;
import com.ivr.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class RagEvalService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern KEYWORD_SPLITTER = Pattern.compile("[,，\\n]");
    private static final String DEFAULT_RAG_TEMPLATE = """
            你是客服助手，必须严格依据下方资料回答客户问题。资料中没有答案时直接回答「抱歉，这个问题需要人工帮您处理，正在为您转接」。
            资料：
            {context}

            客户问题：{question}
            请用 80 字以内、口语化的中文回答，不要重复「资料」二字。
            """;

    private final RagEvalCaseMapper caseMapper;
    private final RagEvalRunMapper runMapper;
    private final RagEvalResultMapper resultMapper;
    private final KbBaseMapper baseMapper;
    private final KnowledgeService knowledgeService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public RagEvalService(RagEvalCaseMapper caseMapper,
                          RagEvalRunMapper runMapper,
                          RagEvalResultMapper resultMapper,
                          KbBaseMapper baseMapper,
                          KnowledgeService knowledgeService,
                          LlmService llmService,
                          ObjectMapper objectMapper) {
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.baseMapper = baseMapper;
        this.knowledgeService = knowledgeService;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> pageCases(int current, int size, Long kbId, String keyword) {
        LambdaQueryWrapper<RagEvalCase> wrapper = new LambdaQueryWrapper<RagEvalCase>()
                .orderByDesc(RagEvalCase::getUpdatedAt)
                .orderByDesc(RagEvalCase::getId);
        if (kbId != null) {
            wrapper.eq(RagEvalCase::getKbId, kbId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(RagEvalCase::getQuestion, kw)
                    .or()
                    .like(RagEvalCase::getExpectedDocTitle, kw)
                    .or()
                    .like(RagEvalCase::getExpectedKeywords, kw));
        }
        Page<RagEvalCase> page = caseMapper.selectPage(Page.of(Math.max(current, 1), Math.max(size, 1)), wrapper);
        Map<Long, String> kbNames = kbNames(page.getRecords().stream().map(RagEvalCase::getKbId).toList());
        return pageResult(page.getRecords().stream().map(item -> caseMap(item, kbNames.get(item.getKbId()))).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createCase(RagEvalCaseRequest request) {
        requiredBase(request.getKbId());
        RagEvalCase item = new RagEvalCase();
        applyCase(item, request);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(item.getCreatedAt());
        caseMapper.insert(item);
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCase(Long id, RagEvalCaseRequest request) {
        requiredBase(request.getKbId());
        RagEvalCase item = requiredCase(id);
        applyCase(item, request);
        item.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long id) {
        if (caseMapper.deleteById(id) == 0) {
            throw new BusinessException(404, "评估用例不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long run(RagEvalRunRequest request) {
        requiredBase(request.getKbId());
        int topK = request.getTopK() == null ? 3 : request.getTopK();
        boolean generateAnswer = !Boolean.FALSE.equals(request.getGenerateAnswer());
        List<RagEvalCase> cases = caseMapper.selectList(new LambdaQueryWrapper<RagEvalCase>()
                .eq(RagEvalCase::getKbId, request.getKbId())
                .eq(RagEvalCase::getEnabled, 1)
                .orderByAsc(RagEvalCase::getId));
        if (cases.isEmpty()) {
            throw new BusinessException(400, "当前知识库没有启用的评估用例");
        }

        RagEvalRun run = new RagEvalRun();
        run.setKbId(request.getKbId());
        run.setTopK(topK);
        run.setGenerateAnswer(bool(generateAnswer));
        run.setTotalCount(cases.size());
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);

        List<RagEvalResult> results = new ArrayList<>();
        for (RagEvalCase item : cases) {
            RagEvalResult result = evaluateOne(run.getId(), item, topK, generateAnswer);
            resultMapper.insert(result);
            results.add(result);
        }

        int total = results.size();
        int passed = count(results, RagEvalResult::getPassed);
        run.setPassedCount(passed);
        run.setPassRate(percent(passed, total));
        run.setHitRate(percent(count(results, RagEvalResult::getHitExpectedDoc), total));
        run.setKeywordPassRate(percent(count(results, RagEvalResult::getKeywordPassed), total));
        run.setFallbackPassRate(percent(count(results, RagEvalResult::getFallbackPassed), total));
        runMapper.updateById(run);
        return run.getId();
    }

    public Map<String, Object> pageRuns(int current, int size, Long kbId) {
        LambdaQueryWrapper<RagEvalRun> wrapper = new LambdaQueryWrapper<RagEvalRun>()
                .orderByDesc(RagEvalRun::getCreatedAt)
                .orderByDesc(RagEvalRun::getId);
        if (kbId != null) {
            wrapper.eq(RagEvalRun::getKbId, kbId);
        }
        Page<RagEvalRun> page = runMapper.selectPage(Page.of(Math.max(current, 1), Math.max(size, 1)), wrapper);
        Map<Long, String> kbNames = kbNames(page.getRecords().stream().map(RagEvalRun::getKbId).toList());
        return pageResult(page.getRecords().stream().map(item -> runMap(item, kbNames.get(item.getKbId()))).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<Map<String, Object>> results(Long runId) {
        if (runMapper.selectById(runId) == null) {
            throw new BusinessException(404, "评估记录不存在");
        }
        return resultMapper.selectList(new LambdaQueryWrapper<RagEvalResult>()
                        .eq(RagEvalResult::getRunId, runId)
                        .orderByAsc(RagEvalResult::getId))
                .stream()
                .map(this::resultMap)
                .toList();
    }

    private RagEvalResult evaluateOne(Long runId, RagEvalCase item, int topK, boolean generateAnswer) {
        RagEvalResult result = new RagEvalResult();
        result.setRunId(runId);
        result.setCaseId(item.getId());
        result.setQuestion(item.getQuestion());
        result.setCreatedAt(LocalDateTime.now());

        List<KnowledgeService.KnowledgeChunk> chunks;
        try {
            chunks = knowledgeService.retrieve(item.getKbId(), item.getQuestion(), topK);
        } catch (Exception e) {
            result.setRetrievedChunks("[]");
            result.setAnswer("");
            result.setHitExpectedDoc(0);
            result.setKeywordPassed(0);
            result.setFallbackPassed(0);
            result.setPassed(0);
            result.setFailReason("检索失败：" + diagnostic(e));
            return result;
        }

        String answer = "";
        if (!chunks.isEmpty() && generateAnswer) {
            try {
                answer = llmService.chatTemplate(DEFAULT_RAG_TEMPLATE, Map.of(
                        "context", context(chunks),
                        "question", item.getQuestion()
                ));
                answer = StringUtils.hasText(answer) ? answer.trim() : "";
            } catch (Exception e) {
                answer = "";
                result.setFailReason("生成失败：" + diagnostic(e));
            }
        }

        boolean shouldFallback = Objects.equals(item.getShouldFallback(), 1);
        boolean fallbackPassed = shouldFallback ? chunks.isEmpty() : !chunks.isEmpty();
        boolean hitDoc = shouldFallback || hitExpectedDoc(chunks, item.getExpectedDocTitle());
        boolean keywordPassed = shouldFallback || keywordsPassed(answer, item.getExpectedKeywords(), generateAnswer);
        boolean passed = fallbackPassed && hitDoc && keywordPassed && !StringUtils.hasText(result.getFailReason());

        result.setRetrievedChunks(toJson(chunks));
        result.setAnswer(answer);
        result.setHitExpectedDoc(bool(hitDoc));
        result.setKeywordPassed(bool(keywordPassed));
        result.setFallbackPassed(bool(fallbackPassed));
        result.setPassed(bool(passed));
        if (!passed && !StringUtils.hasText(result.getFailReason())) {
            result.setFailReason(failReason(shouldFallback, fallbackPassed, hitDoc, keywordPassed));
        }
        return result;
    }

    private boolean hitExpectedDoc(List<KnowledgeService.KnowledgeChunk> chunks, String expectedDocTitle) {
        if (!StringUtils.hasText(expectedDocTitle)) {
            return true;
        }
        String expected = expectedDocTitle.trim().toLowerCase();
        return chunks.stream().anyMatch(chunk -> chunk.title() != null && chunk.title().toLowerCase().contains(expected));
    }

    private boolean keywordsPassed(String answer, String expectedKeywords, boolean generateAnswer) {
        List<String> keywords = splitKeywords(expectedKeywords);
        if (keywords.isEmpty()) {
            return true;
        }
        if (!generateAnswer || !StringUtils.hasText(answer)) {
            return false;
        }
        String lowerAnswer = answer.toLowerCase();
        return keywords.stream().allMatch(keyword -> lowerAnswer.contains(keyword.toLowerCase()));
    }

    private String failReason(boolean shouldFallback, boolean fallbackPassed, boolean hitDoc, boolean keywordPassed) {
        List<String> reasons = new ArrayList<>();
        if (!fallbackPassed) {
            reasons.add(shouldFallback ? "期望 fallback，但实际命中了知识片段" : "期望命中知识片段，但实际未命中");
        }
        if (!hitDoc) {
            reasons.add("未命中期望文档");
        }
        if (!keywordPassed) {
            reasons.add("回答缺少期望关键词");
        }
        return String.join("；", reasons);
    }

    private void applyCase(RagEvalCase item, RagEvalCaseRequest request) {
        item.setKbId(request.getKbId());
        item.setQuestion(request.getQuestion().trim());
        item.setExpectedDocTitle(defaultText(request.getExpectedDocTitle(), ""));
        item.setExpectedKeywords(defaultText(request.getExpectedKeywords(), ""));
        item.setShouldFallback(Boolean.TRUE.equals(request.getShouldFallback()) ? 1 : 0);
        item.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
    }

    private RagEvalCase requiredCase(Long id) {
        RagEvalCase item = caseMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "评估用例不存在");
        }
        return item;
    }

    private KbBase requiredBase(Long id) {
        KbBase base = baseMapper.selectById(id);
        if (base == null || Objects.equals(base.getDeleted(), 1)) {
            throw new BusinessException(404, "知识库不存在");
        }
        return base;
    }

    private Map<Long, String> kbNames(List<Long> kbIds) {
        List<Long> ids = kbIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        baseMapper.selectBatchIds(ids).forEach(base -> result.put(base.getId(), base.getKbName()));
        return result;
    }

    private Map<String, Object> caseMap(RagEvalCase item, String kbName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("kbId", item.getKbId());
        map.put("kbName", Objects.toString(kbName, ""));
        map.put("question", item.getQuestion());
        map.put("expectedDocTitle", item.getExpectedDocTitle());
        map.put("expectedKeywords", item.getExpectedKeywords());
        map.put("shouldFallback", Objects.equals(item.getShouldFallback(), 1));
        map.put("enabled", !Objects.equals(item.getEnabled(), 0));
        map.put("createdAt", formatTime(item.getCreatedAt()));
        map.put("updatedAt", formatTime(item.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> runMap(RagEvalRun item, String kbName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("kbId", item.getKbId());
        map.put("kbName", Objects.toString(kbName, ""));
        map.put("topK", item.getTopK());
        map.put("generateAnswer", !Objects.equals(item.getGenerateAnswer(), 0));
        map.put("totalCount", item.getTotalCount());
        map.put("passedCount", item.getPassedCount());
        map.put("passRate", item.getPassRate());
        map.put("hitRate", item.getHitRate());
        map.put("keywordPassRate", item.getKeywordPassRate());
        map.put("fallbackPassRate", item.getFallbackPassRate());
        map.put("createdAt", formatTime(item.getCreatedAt()));
        return map;
    }

    private Map<String, Object> resultMap(RagEvalResult item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("runId", item.getRunId());
        map.put("caseId", item.getCaseId());
        map.put("question", item.getQuestion());
        map.put("retrievedChunks", item.getRetrievedChunks());
        map.put("answer", item.getAnswer());
        map.put("hitExpectedDoc", Objects.equals(item.getHitExpectedDoc(), 1));
        map.put("keywordPassed", Objects.equals(item.getKeywordPassed(), 1));
        map.put("fallbackPassed", Objects.equals(item.getFallbackPassed(), 1));
        map.put("passed", Objects.equals(item.getPassed(), 1));
        map.put("failReason", item.getFailReason());
        map.put("createdAt", formatTime(item.getCreatedAt()));
        return map;
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> records, long total, long current, long size) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("records", records);
        map.put("total", total);
        map.put("current", current);
        map.put("size", size);
        return map;
    }

    private String context(List<KnowledgeService.KnowledgeChunk> chunks) {
        return chunks.stream()
                .map(chunk -> "- " + chunk.title() + "：" + chunk.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String toJson(List<KnowledgeService.KnowledgeChunk> chunks) {
        try {
            List<Map<String, Object>> rows = chunks.stream().map(chunk -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("docId", chunk.docId());
                map.put("title", chunk.title());
                map.put("content", chunk.content());
                map.put("score", Math.round(chunk.score() * 10000) / 10000.0);
                return map;
            }).toList();
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            return "[]";
        }
    }

    private int count(List<RagEvalResult> results, java.util.function.Function<RagEvalResult, Integer> getter) {
        return (int) results.stream().filter(item -> Objects.equals(getter.apply(item), 1)).count();
    }

    private int percent(int value, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(value * 100.0 / total);
    }

    private int bool(boolean value) {
        return value ? 1 : 0;
    }

    private List<String> splitKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String part : KEYWORD_SPLITTER.split(value)) {
            String text = part.trim();
            if (StringUtils.hasText(text)) {
                keywords.add(text);
            }
        }
        return keywords;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }

    private String diagnostic(Throwable e) {
        String message = Objects.toString(e == null ? "" : e.getMessage(), "");
        String text = e == null ? "" : e.getClass().getSimpleName() + (StringUtils.hasText(message) ? ": " + message : "");
        text = text.replaceAll("(?i)(sk-[A-Za-z0-9_-]{6})[A-Za-z0-9_-]+", "$1***");
        text = text.replaceAll("(?i)(api[-_ ]?key\\s*[:=]\\s*)[^\\s,;}]+", "$1***");
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
