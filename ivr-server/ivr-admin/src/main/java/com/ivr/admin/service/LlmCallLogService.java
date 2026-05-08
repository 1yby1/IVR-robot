package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ivr.admin.entity.LlmCallLog;
import com.ivr.admin.mapper.LlmCallLogMapper;
import com.ivr.ai.LlmCallLogRecord;
import com.ivr.ai.LlmCallLogSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class LlmCallLogService implements LlmCallLogSink {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LlmCallLogMapper mapper;

    public LlmCallLogService(LlmCallLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(LlmCallLogRecord record) {
        try {
            LlmCallLog entity = new LlmCallLog();
            entity.setTraceId(record.getTraceId());
            entity.setScene(record.getScene());
            entity.setProvider(record.getProvider());
            entity.setModel(record.getModel());
            entity.setStatus(record.getStatus());
            entity.setPromptTokens(record.getPromptTokens());
            entity.setCompletionTokens(record.getCompletionTokens());
            entity.setTotalTokens(record.getTotalTokens());
            entity.setTokenEstimated(record.getTokenEstimated());
            entity.setPromptChars(record.getPromptChars());
            entity.setResponseChars(record.getResponseChars());
            entity.setLatencyMs(record.getLatencyMs());
            entity.setErrorMessage(record.getErrorMessage());
            entity.setPromptPreview(record.getPromptPreview());
            entity.setResponsePreview(record.getResponsePreview());
            entity.setCreatedAt(LocalDateTime.now());
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("[LLM] persist call log failed traceId={} err={}", record.getTraceId(), e.toString());
        }
    }

    public Map<String, Object> overview() {
        QueryWrapper<LlmCallLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "COUNT(*) AS total_count",
                "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_count",
                "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_count",
                "COALESCE(ROUND(AVG(latency_ms)), 0) AS avg_latency_ms",
                "COALESCE(MAX(latency_ms), 0) AS max_latency_ms",
                "COALESCE(SUM(total_tokens), 0) AS total_tokens",
                "COALESCE(ROUND(AVG(total_tokens)), 0) AS avg_tokens"
        );
        Map<String, Object> row = mapper.selectMaps(wrapper).stream().findFirst().orElse(Map.of());
        long total = longVal(row.get("total_count"));
        long success = longVal(row.get("success_count"));
        long failed = longVal(row.get("failed_count"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", total);
        result.put("successCount", success);
        result.put("failedCount", failed);
        result.put("successRate", percent(success, total));
        result.put("avgLatencyMs", longVal(row.get("avg_latency_ms")));
        result.put("maxLatencyMs", longVal(row.get("max_latency_ms")));
        result.put("totalTokens", longVal(row.get("total_tokens")));
        result.put("avgTokens", longVal(row.get("avg_tokens")));
        return result;
    }

    public Map<String, Object> page(int current, int size, String scene, String status, String keyword) {
        LambdaQueryWrapper<LlmCallLog> wrapper = new LambdaQueryWrapper<LlmCallLog>()
                .orderByDesc(LlmCallLog::getCreatedAt)
                .orderByDesc(LlmCallLog::getId);
        if (StringUtils.hasText(scene)) {
            wrapper.eq(LlmCallLog::getScene, scene.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(LlmCallLog::getStatus, status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(LlmCallLog::getTraceId, kw)
                    .or()
                    .like(LlmCallLog::getModel, kw)
                    .or()
                    .like(LlmCallLog::getPromptPreview, kw)
                    .or()
                    .like(LlmCallLog::getResponsePreview, kw)
                    .or()
                    .like(LlmCallLog::getErrorMessage, kw));
        }

        Page<LlmCallLog> page = mapper.selectPage(Page.of(Math.max(current, 1), Math.max(size, 1)), wrapper);
        return pageResult(page.getRecords().stream().map(this::toMap).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<String> scenes() {
        QueryWrapper<LlmCallLog> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT scene").isNotNull("scene").orderByAsc("scene");
        return mapper.selectMaps(wrapper).stream()
                .map(row -> Objects.toString(row.get("scene"), ""))
                .filter(StringUtils::hasText)
                .toList();
    }

    private Map<String, Object> toMap(LlmCallLog item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("traceId", item.getTraceId());
        map.put("scene", item.getScene());
        map.put("provider", item.getProvider());
        map.put("model", item.getModel());
        map.put("status", item.getStatus());
        map.put("promptTokens", item.getPromptTokens());
        map.put("completionTokens", item.getCompletionTokens());
        map.put("totalTokens", item.getTotalTokens());
        map.put("tokenEstimated", Objects.equals(item.getTokenEstimated(), 1));
        map.put("promptChars", item.getPromptChars());
        map.put("responseChars", item.getResponseChars());
        map.put("latencyMs", item.getLatencyMs());
        map.put("errorMessage", item.getErrorMessage());
        map.put("promptPreview", item.getPromptPreview());
        map.put("responsePreview", item.getResponsePreview());
        map.put("createdAt", item.getCreatedAt() == null ? "" : TIME_FMT.format(item.getCreatedAt()));
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

    private long longVal(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private int percent(long value, long total) {
        if (total <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(value * 100)
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
