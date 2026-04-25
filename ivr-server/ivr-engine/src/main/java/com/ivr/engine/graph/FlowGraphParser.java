package com.ivr.engine.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LogicFlow JSON → {@link FlowGraph} 反序列化器。
 *
 * <p>LogicFlow 画布导出的结构示例（{@code FlowStore.DEFAULT_GRAPH}）：
 * <pre>
 * { "nodes": [ {"id":"start","type":"circle","text":"...","properties":{"bizType":"start",...}} ],
 *   "edges": [ {"sourceNodeId":"start","targetNodeId":"play","properties":{"key":"1"}} ] }
 * </pre>
 *
 * <p>{@code text} 字段在 LogicFlow 里有时是 {@code {value:"..."}} 嵌套结构，这里统一拍平。
 */
@Component
public class FlowGraphParser {

    private final ObjectMapper objectMapper;

    public FlowGraphParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FlowGraph parse(String graphJson) {
        if (!StringUtils.hasText(graphJson)) {
            throw new IllegalArgumentException("graphJson is empty");
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(graphJson, new TypeReference<>() {
            });
            return parse(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("graphJson is malformed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public FlowGraph parse(Map<String, Object> raw) {
        FlowGraph graph = new FlowGraph();
        for (Object item : asList(raw.get("nodes"))) {
            Map<String, Object> nodeMap = asMap(item);
            String id = Objects.toString(nodeMap.get("id"), "");
            if (!StringUtils.hasText(id)) {
                continue;
            }
            FlowGraph.Node node = new FlowGraph.Node();
            node.setId(id);
            node.setType(Objects.toString(nodeMap.get("type"), ""));
            node.setText(extractText(nodeMap.get("text")));
            node.setProperties(asMap(nodeMap.get("properties")));
            graph.getNodes().put(node.getId(), node);
        }
        for (Object item : asList(raw.get("edges"))) {
            Map<String, Object> edgeMap = asMap(item);
            FlowGraph.Edge edge = new FlowGraph.Edge();
            edge.setId(Objects.toString(edgeMap.get("id"), ""));
            edge.setSourceNodeId(Objects.toString(edgeMap.get("sourceNodeId"), ""));
            edge.setTargetNodeId(Objects.toString(edgeMap.get("targetNodeId"), ""));
            edge.setText(extractText(edgeMap.get("text")));
            edge.setProperties(asMap(edgeMap.get("properties")));
            if (StringUtils.hasText(edge.getSourceNodeId()) && StringUtils.hasText(edge.getTargetNodeId())) {
                graph.getEdges().add(edge);
            }
        }
        return graph;
    }

    private String extractText(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object inner = map.get("value");
            if (inner == null) {
                inner = map.get("text");
            }
            return inner == null ? "" : inner.toString();
        }
        return value == null ? "" : value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }
}
