package com.ivr.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.FlowAiGenerateRequest;
import com.ivr.admin.dto.FlowAiGenerateResponse;
import com.ivr.ai.flow.FlowAiGeneratorService;
import com.ivr.ai.flow.GeneratedFlow;
import com.ivr.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FlowAiDraftService {

    private static final int START_X = 160;
    private static final int START_Y = 180;
    private static final int X_STEP = 220;
    private static final int Y_STEP = 120;
    private static final int MAX_COLUMNS = 4;

    private final FlowAiGeneratorService generatorService;
    private final ObjectMapper objectMapper;
    private final FlowStore flowStore;

    public FlowAiDraftService(FlowAiGeneratorService generatorService,
                              ObjectMapper objectMapper,
                              FlowStore flowStore) {
        this.generatorService = generatorService;
        this.objectMapper = objectMapper;
        this.flowStore = flowStore;
    }

    public FlowAiGenerateResponse generate(FlowAiGenerateRequest request) {
        String requirement = request == null ? "" : Objects.toString(request.getRequirement(), "").trim();
        if (!StringUtils.hasText(requirement)) {
            throw new BusinessException(400, "业务描述不能为空");
        }

        GeneratedFlow generated;
        try {
            generated = generatorService.generate(requirement);
        } catch (Exception e) {
            throw new BusinessException(500, "AI 生成流程失败：" + diagnostic(e));
        }

        String graphJson = normalizeGraphJson(generated.graphJson());
        FlowAiGenerateResponse response = new FlowAiGenerateResponse();
        response.setGraphJson(graphJson);
        response.setSummary(defaultText(generated.summary(), "已根据业务描述生成流程草稿"));
        response.setWarnings(generated.warnings() == null ? List.of() : generated.warnings());
        response.setValidationErrors(flowStore.validateGraphJson(graphJson));
        return response;
    }

    private String normalizeGraphJson(String graphJson) {
        Map<String, Object> raw = readGraph(graphJson);
        List<Object> nodes = listValue(raw.get("nodes"));
        List<Object> edges = listValue(raw.get("edges"));
        if (nodes.isEmpty()) {
            throw new BusinessException(500, "AI 返回的流程图没有节点");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        List<Map<String, Object>> normalizedNodes = new ArrayList<>();
        List<Map<String, Object>> normalizedEdges = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            normalizedNodes.add(normalizeNode(mapValue(nodes.get(i)), i));
        }
        for (int i = 0; i < edges.size(); i++) {
            normalizedEdges.add(normalizeEdge(mapValue(edges.get(i)), i));
        }

        normalized.put("nodes", normalizedNodes);
        normalized.put("edges", normalizedEdges);
        return writeJson(normalized);
    }

    private Map<String, Object> normalizeNode(Map<String, Object> node, int index) {
        Map<String, Object> properties = new LinkedHashMap<>(mapValue(node.get("properties")));
        String bizType = defaultText(Objects.toString(properties.get("bizType"), ""),
                defaultText(Objects.toString(node.get("bizType"), ""), Objects.toString(node.get("type"), "play")));
        if ("circle".equals(bizType) || "rect".equals(bizType)) {
            bizType = "play";
        }
        String id = defaultText(Objects.toString(node.get("id"), ""), "node-" + (index + 1));
        String name = defaultText(Objects.toString(properties.get("name"), ""), defaultNodeName(bizType, index));
        name = defaultText(textValue(node.get("text")), name);
        properties.put("bizType", bizType);
        properties.put("name", name);
        fillDefaultProperties(bizType, properties);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("type", isCircleNode(bizType) ? "circle" : "rect");
        result.put("x", numberValue(node.get("x"), START_X + (index % MAX_COLUMNS) * X_STEP));
        result.put("y", numberValue(node.get("y"), START_Y + (index / MAX_COLUMNS) * Y_STEP));
        result.put("text", name);
        result.put("properties", properties);
        return result;
    }

    private Map<String, Object> normalizeEdge(Map<String, Object> edge, int index) {
        Map<String, Object> properties = new LinkedHashMap<>(mapValue(edge.get("properties")));
        String source = Objects.toString(edge.get("sourceNodeId"), "");
        String target = Objects.toString(edge.get("targetNodeId"), "");
        String key = defaultText(Objects.toString(properties.get("key"), ""), textValue(edge.get("text")));
        if (StringUtils.hasText(key)) {
            properties.put("key", key);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", defaultText(Objects.toString(edge.get("id"), ""),
                "edge-" + source + "-" + target + "-" + (index + 1)));
        result.put("type", "polyline");
        result.put("sourceNodeId", source);
        result.put("targetNodeId", target);
        if (StringUtils.hasText(key)) {
            result.put("text", key);
        }
        result.put("properties", properties);
        return result;
    }

    private void fillDefaultProperties(String bizType, Map<String, Object> properties) {
        if ("play".equals(bizType)) {
            properties.putIfAbsent("ttsText", "欢迎致电，请根据语音提示选择服务。");
        }
        if ("dtmf".equals(bizType)) {
            properties.putIfAbsent("maxDigits", 1);
            properties.putIfAbsent("timeoutSec", 8);
        }
        if ("asr".equals(bizType)) {
            properties.putIfAbsent("maxSeconds", 8);
            properties.putIfAbsent("language", "zh-CN");
            properties.putIfAbsent("prompt", "请说出您的问题");
        }
        if ("intent".equals(bizType)) {
            properties.putIfAbsent("inputVar", "lastAsr");
            properties.putIfAbsent("fallbackBranch", "other");
        }
        if ("rag".equals(bizType)) {
            properties.putIfAbsent("topK", 3);
            properties.putIfAbsent("questionVar", "lastAsr");
            properties.putIfAbsent("answerVar", "ragAnswer");
            properties.putIfAbsent("fallbackBranch", "fallback");
        }
        if ("condition".equals(bizType)) {
            properties.putIfAbsent("expression", "true");
        }
        if ("var_assign".equals(bizType)) {
            properties.putIfAbsent("varName", "debugVar");
            properties.putIfAbsent("value", "");
        }
        if ("http".equals(bizType)) {
            properties.putIfAbsent("method", "GET");
            properties.putIfAbsent("timeoutSec", 5);
            properties.putIfAbsent("responseVar", "httpResponse");
            properties.putIfAbsent("statusVar", "httpStatus");
            properties.putIfAbsent("fallbackBranch", "fallback");
        }
        if ("transfer".equals(bizType)) {
            properties.putIfAbsent("target", "1000");
        }
        if ("voicemail".equals(bizType)) {
            properties.putIfAbsent("maxSeconds", 60);
            properties.putIfAbsent("filePath", "");
        }
    }

    private boolean isCircleNode(String bizType) {
        return "start".equals(bizType) || "end".equals(bizType);
    }

    private String defaultNodeName(String bizType, int index) {
        Map<String, String> names = Map.ofEntries(
                Map.entry("start", "开始"),
                Map.entry("end", "结束"),
                Map.entry("play", "播放语音"),
                Map.entry("dtmf", "按键收集"),
                Map.entry("asr", "语音识别"),
                Map.entry("intent", "AI 意图"),
                Map.entry("rag", "AI 问答"),
                Map.entry("condition", "条件判断"),
                Map.entry("var_assign", "变量赋值"),
                Map.entry("http", "HTTP 调用"),
                Map.entry("transfer", "转人工"),
                Map.entry("voicemail", "留言")
        );
        return names.getOrDefault(bizType, "节点 " + (index + 1));
    }

    private Map<String, Object> readGraph(String graphJson) {
        try {
            return objectMapper.readValue(graphJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException(500, "AI 返回的流程图不是合法 JSON");
        }
    }

    private String writeJson(Map<String, Object> graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        } catch (Exception e) {
            throw new BusinessException(500, "流程图 JSON 生成失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private List<Object> listValue(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private String textValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("value");
            return text == null ? Objects.toString(map.get("text"), "") : Objects.toString(text, "");
        }
        return Objects.toString(value, "");
    }

    private Object numberValue(Object value, int fallback) {
        return value instanceof Number ? value : fallback;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String diagnostic(Exception e) {
        String text = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
        return text.length() <= 300 ? text : text.substring(0, 300);
    }
}
