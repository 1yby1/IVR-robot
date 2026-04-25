package com.ivr.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.FlowDebugInputRequest;
import com.ivr.admin.dto.FlowDebugResponse;
import com.ivr.admin.dto.FlowDebugStartRequest;
import com.ivr.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FlowDebugService {

    private static final int MAX_STEPS = 50;

    private final FlowStore flowStore;
    private final ObjectMapper objectMapper;
    private final CallRecordService callRecordService;
    private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();

    public FlowDebugService(FlowStore flowStore, ObjectMapper objectMapper, CallRecordService callRecordService) {
        this.flowStore = flowStore;
        this.objectMapper = objectMapper;
        this.callRecordService = callRecordService;
    }

    public FlowDebugResponse start(Long flowId, FlowDebugStartRequest request) {
        Map<String, Object> detail = flowStore.detail(flowId);
        Graph graph = parseGraph(Objects.toString(detail.get("graphJson"), ""));
        String startNodeId = graph.nodes.values().stream()
                .filter(node -> "start".equals(node.bizType()))
                .map(node -> node.id)
                .findFirst()
                .orElseGet(() -> graph.nodes.keySet().stream().findFirst()
                        .orElseThrow(() -> new BusinessException(400, "流程没有可执行节点")));

        DebugSession session = new DebugSession();
        session.sessionId = UUID.randomUUID().toString();
        session.flowId = flowId;
        session.flowVersion = numberValue(detail.get("currentVersion"));
        session.flowName = Objects.toString(detail.get("flowName"), "未命名流程");
        session.graph = graph;
        session.variables.put("caller", defaultText(request == null ? null : request.getCaller(), "13800000000"));
        session.variables.put("callee", defaultText(request == null ? null : request.getCallee(), "4001"));
        sessions.put(session.sessionId, session);
        callRecordService.startCall(
                session.sessionId,
                session.variables.get("caller"),
                session.variables.get("callee"),
                session.flowId,
                session.flowVersion
        );

        List<String> prompts = new ArrayList<>();
        List<String> events = new ArrayList<>();
        events.add("模拟呼叫进入流程：" + session.flowName);
        return advance(session, startNodeId, prompts, events);
    }

    public FlowDebugResponse input(String sessionId, FlowDebugInputRequest request) {
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(404, "模拟通话不存在或已过期");
        }
        if ("ended".equals(session.status)) {
            throw new BusinessException(400, "模拟通话已结束");
        }
        Node node = session.graph.nodes.get(session.currentNodeId);
        if (node == null || !"dtmf".equals(node.bizType())) {
            throw new BusinessException(400, "当前节点不需要按键输入");
        }

        String input = request.getInput().trim();
        session.variables.put("lastInput", input);
        List<String> prompts = new ArrayList<>();
        List<String> events = new ArrayList<>();
        events.add("用户输入：" + input);
        record(session, node, "dtmf", Map.of("input", input));

        if (outgoing(session.graph, node.id).isEmpty()) {
            events.add("按键节点没有后续连线，流程结束");
            return finish(session, "当前按键节点没有配置后续分支", prompts, events);
        }

        String nextNodeId = resolveDtmfNext(node, session.graph, input);
        if (!StringUtils.hasText(nextNodeId)) {
            events.add("按键未匹配，流程结束");
            return finish(session, "未匹配到按键分支", prompts, events);
        }
        return advance(session, nextNodeId, prompts, events);
    }

    private FlowDebugResponse advance(DebugSession session,
                                      String nodeId,
                                      List<String> prompts,
                                      List<String> events) {
        String currentNodeId = nodeId;
        for (int i = 0; i < MAX_STEPS; i++) {
            Node node = session.graph.nodes.get(currentNodeId);
            if (node == null) {
                return finish(session, "节点不存在：" + currentNodeId, prompts, events);
            }

            session.currentNodeId = node.id;
            session.status = "running";
            session.waitingFor = null;
            events.add("进入节点：" + node.name());
            record(session, node, "enter", Map.of("name", node.name(), "bizType", node.bizType()));

            String bizType = node.bizType();
            if ("start".equals(bizType)) {
                currentNodeId = firstNext(session.graph, node.id);
                if (!StringUtils.hasText(currentNodeId)) {
                    return finish(session, "开始节点没有后续节点", prompts, events);
                }
                continue;
            }
            if ("play".equals(bizType)) {
                String prompt = promptText(node, "播放语音");
                prompts.add(prompt);
                record(session, node, "prompt", Map.of("text", prompt));
                currentNodeId = firstNext(session.graph, node.id);
                if (!StringUtils.hasText(currentNodeId)) {
                    return finish(session, "播放完成，流程结束", prompts, events);
                }
                continue;
            }
            if ("dtmf".equals(bizType)) {
                session.status = "waiting";
                session.waitingFor = "dtmf";
                String prompt = promptText(node, "请输入按键");
                prompts.add(prompt);
                record(session, node, "prompt", Map.of("text", prompt));
                events.add("等待用户按键");
                return buildResponse(session, prompts, events);
            }
            if ("transfer".equals(bizType)) {
                return finish(session, "转人工：" + node.stringProp("target", "1000"), prompts, events);
            }
            if ("voicemail".equals(bizType)) {
                return finish(session, "进入留言流程", prompts, events);
            }
            if ("end".equals(bizType)) {
                return finish(session, "流程正常结束", prompts, events);
            }

            events.add("模拟执行：" + node.name());
            currentNodeId = firstNext(session.graph, node.id);
            if (!StringUtils.hasText(currentNodeId)) {
                return finish(session, node.name() + "执行完成，流程结束", prompts, events);
            }
        }
        return finish(session, "执行步数超过上限，请检查流程是否存在循环", prompts, events);
    }

    private FlowDebugResponse finish(DebugSession session,
                                     String result,
                                     List<String> prompts,
                                     List<String> events) {
        session.status = "ended";
        session.waitingFor = null;
        session.result = result;
        events.add(result);
        record(session, session.graph.nodes.get(session.currentNodeId), "exit", Map.of("result", result));
        callRecordService.finishCall(session.sessionId, endReason(result), transferTarget(result));
        return buildResponse(session, prompts, events);
    }

    private FlowDebugResponse buildResponse(DebugSession session,
                                            List<String> prompts,
                                            List<String> events) {
        Node current = session.graph.nodes.get(session.currentNodeId);
        FlowDebugResponse response = new FlowDebugResponse();
        response.setSessionId(session.sessionId);
        response.setFlowId(session.flowId);
        response.setFlowName(session.flowName);
        response.setCurrentNodeId(session.currentNodeId);
        response.setCurrentNodeName(current == null ? "" : current.name());
        response.setStatus(session.status);
        response.setWaitingFor(session.waitingFor);
        response.setResult(session.result);
        response.setPrompts(prompts);
        response.setEvents(events);
        response.setOptions("waiting".equals(session.status) && current != null
                ? options(current, session.graph)
                : List.of());
        response.setVariables(session.variables);
        return response;
    }

    private Graph parseGraph(String graphJson) {
        try {
            Map<String, Object> raw = objectMapper.readValue(graphJson, new TypeReference<>() {
            });
            Graph graph = new Graph();
            for (Object item : listValue(raw.get("nodes"))) {
                Map<String, Object> nodeMap = mapValue(item);
                String id = Objects.toString(nodeMap.get("id"), "");
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                Node node = new Node();
                node.id = id;
                node.type = Objects.toString(nodeMap.get("type"), "");
                node.text = textValue(nodeMap.get("text"));
                node.properties = mapValue(nodeMap.get("properties"));
                graph.nodes.put(node.id, node);
            }
            for (Object item : listValue(raw.get("edges"))) {
                Map<String, Object> edgeMap = mapValue(item);
                Edge edge = new Edge();
                edge.sourceNodeId = Objects.toString(edgeMap.get("sourceNodeId"), "");
                edge.targetNodeId = Objects.toString(edgeMap.get("targetNodeId"), "");
                edge.text = textValue(edgeMap.get("text"));
                edge.properties = mapValue(edgeMap.get("properties"));
                if (StringUtils.hasText(edge.sourceNodeId) && StringUtils.hasText(edge.targetNodeId)) {
                    graph.edges.add(edge);
                }
            }
            return graph;
        } catch (Exception e) {
            throw new BusinessException(400, "流程图数据格式不正确");
        }
    }

    private String resolveDtmfNext(Node node, Graph graph, String input) {
        Object mappings = node.properties.get("mappings");
        for (Object item : listValue(mappings)) {
            Map<String, Object> mapping = mapValue(item);
            String key = Objects.toString(mapping.get("key"), "");
            String nextNode = Objects.toString(mapping.get("nextNode"), "");
            if (input.equals(key) && StringUtils.hasText(nextNode)) {
                return nextNode;
            }
        }

        List<Edge> outgoing = outgoing(graph, node.id);
        for (Edge edge : outgoing) {
            if (input.equals(edge.branchKey())) {
                return edge.targetNodeId;
            }
        }
        if (outgoing.size() == 1) {
            return outgoing.get(0).targetNodeId;
        }
        int optionIndex = parseOptionIndex(input);
        if (optionIndex >= 0 && optionIndex < outgoing.size()) {
            return outgoing.get(optionIndex).targetNodeId;
        }
        return "";
    }

    private List<Map<String, String>> options(Node node, Graph graph) {
        List<Edge> outgoing = outgoing(graph, node.id);
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < outgoing.size(); i++) {
            Edge edge = outgoing.get(i);
            String key = StringUtils.hasText(edge.branchKey()) ? edge.branchKey() : String.valueOf(i + 1);
            Node target = graph.nodes.get(edge.targetNodeId);
            result.add(Map.of(
                    "key", key,
                    "label", target == null ? edge.targetNodeId : target.name(),
                    "targetNodeId", edge.targetNodeId
            ));
        }
        return result;
    }

    private String firstNext(Graph graph, String nodeId) {
        return outgoing(graph, nodeId).stream()
                .map(edge -> edge.targetNodeId)
                .findFirst()
                .orElse("");
    }

    private List<Edge> outgoing(Graph graph, String nodeId) {
        return graph.edges.stream()
                .filter(edge -> nodeId.equals(edge.sourceNodeId))
                .sorted(Comparator.comparing(edge -> edge.targetNodeId))
                .toList();
    }

    private int parseOptionIndex(String input) {
        try {
            return Integer.parseInt(input) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String promptText(Node node, String fallback) {
        String text = node.stringProp("ttsText", "");
        if (!StringUtils.hasText(text)) {
            text = node.nestedStringProp("tts", "text");
        }
        if (!StringUtils.hasText(text)) {
            text = node.stringProp("prompt", "");
        }
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Integer numberValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void record(DebugSession session, Node node, String eventType, Map<String, Object> payload) {
        callRecordService.recordEvent(
                session.sessionId,
                node == null ? "" : node.id,
                node == null ? "" : node.bizType(),
                eventType,
                payload
        );
    }

    private String endReason(String result) {
        if (result == null) {
            return "normal";
        }
        if (result.startsWith("转人工")) {
            return "transfer";
        }
        if (result.contains("超时") || result.contains("未匹配") || result.contains("没有")) {
            return "timeout";
        }
        if (result.contains("不存在") || result.contains("错误")) {
            return "error";
        }
        return "normal";
    }

    private String transferTarget(String result) {
        if (result != null && result.startsWith("转人工：")) {
            return result.substring("转人工：".length());
        }
        return "";
    }

    private String textValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("value");
            return text == null ? Objects.toString(map.get("text"), "") : Objects.toString(text, "");
        }
        return Objects.toString(value, "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private List<Object> listValue(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private static class DebugSession {
        private String sessionId;
        private Long flowId;
        private Integer flowVersion;
        private String flowName;
        private Graph graph;
        private String currentNodeId;
        private String status;
        private String waitingFor;
        private String result = "";
        private final Map<String, String> variables = new LinkedHashMap<>();
    }

    private static class Graph {
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final List<Edge> edges = new ArrayList<>();
    }

    private static class Node {
        private String id;
        private String type;
        private String text;
        private Map<String, Object> properties = new LinkedHashMap<>();

        private String bizType() {
            String value = stringProp("bizType", "");
            return StringUtils.hasText(value) ? value : type;
        }

        private String name() {
            String value = stringProp("name", "");
            if (StringUtils.hasText(value)) {
                return value;
            }
            return StringUtils.hasText(text) ? text : id;
        }

        private String stringProp(String key, String fallback) {
            Object value = properties.get(key);
            String textValue = value == null ? "" : Objects.toString(value, "");
            return StringUtils.hasText(textValue) ? textValue : fallback;
        }

        @SuppressWarnings("unchecked")
        private String nestedStringProp(String objectKey, String valueKey) {
            Object value = properties.get(objectKey);
            if (!(value instanceof Map<?, ?> map)) {
                return "";
            }
            Object nestedValue = ((Map<String, Object>) map).get(valueKey);
            return nestedValue == null ? "" : Objects.toString(nestedValue, "");
        }
    }

    private static class Edge {
        private String sourceNodeId;
        private String targetNodeId;
        private String text;
        private Map<String, Object> properties = new LinkedHashMap<>();

        private String branchKey() {
            for (String key : List.of("key", "dtmf", "digit", "value")) {
                Object value = properties.get(key);
                if (value != null && StringUtils.hasText(Objects.toString(value, ""))) {
                    return Objects.toString(value, "");
                }
            }
            return StringUtils.hasText(text) ? text : "";
        }
    }
}
