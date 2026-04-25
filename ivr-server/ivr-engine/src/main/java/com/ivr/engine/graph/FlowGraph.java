package com.ivr.engine.graph;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程图内存模型。LogicFlow 画布保存的 JSON 反序列化后的运行时表示。
 *
 * <p>节点按 id 索引，方便按 id 路由；边按出发节点分组遍历。
 */
public class FlowGraph {

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    public Map<String, Node> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }

    public Node node(String id) {
        return nodes.get(id);
    }

    public List<Edge> outgoing(String nodeId) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            if (nodeId.equals(edge.getSourceNodeId())) {
                result.add(edge);
            }
        }
        return result;
    }

    public Node findStart() {
        for (Node node : nodes.values()) {
            if ("start".equals(node.bizType())) {
                return node;
            }
        }
        return null;
    }

    public static class Node {
        private String id;
        private String type;
        private String text;
        private Map<String, Object> properties = new LinkedHashMap<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) {
            this.properties = properties == null ? new LinkedHashMap<>() : properties;
        }

        /** 节点的业务类型，优先取 properties.bizType；否则回退到 LogicFlow 的 type。 */
        public String bizType() {
            String value = stringProp("bizType", "");
            return StringUtils.hasText(value) ? value : type;
        }

        public String name() {
            String value = stringProp("name", "");
            if (StringUtils.hasText(value)) {
                return value;
            }
            return StringUtils.hasText(text) ? text : id;
        }

        public String stringProp(String key, String fallback) {
            Object value = properties.get(key);
            String textValue = value == null ? "" : value.toString();
            return StringUtils.hasText(textValue) ? textValue : fallback;
        }

        public int intProp(String key, int fallback) {
            Object value = properties.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(value == null ? "" : value.toString().trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    public static class Edge {
        private String id;
        private String sourceNodeId;
        private String targetNodeId;
        private String text;
        private Map<String, Object> properties = new LinkedHashMap<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSourceNodeId() { return sourceNodeId; }
        public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
        public String getTargetNodeId() { return targetNodeId; }
        public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) {
            this.properties = properties == null ? new LinkedHashMap<>() : properties;
        }

        /** 分支标识：先看 properties.key/dtmf/digit/value，再看 edge.text。 */
        public String branchKey() {
            for (String key : List.of("key", "dtmf", "digit", "value")) {
                Object value = properties.get(key);
                if (value != null) {
                    String textValue = value.toString();
                    if (StringUtils.hasText(textValue)) {
                        return textValue;
                    }
                }
            }
            return StringUtils.hasText(text) ? text : "";
        }
    }
}
