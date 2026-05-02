package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.FlowHealthResponse;
import com.ivr.admin.entity.CallEvent;
import com.ivr.admin.entity.CallLog;
import com.ivr.admin.mapper.CallEventMapper;
import com.ivr.admin.mapper.CallLogMapper;
import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.graph.FlowGraphParser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class FlowHealthService {

    private static final int SAMPLE_LIMIT = 200;
    private static final int LONG_PROMPT_LIMIT = 120;

    private final FlowStore flowStore;
    private final FlowGraphParser graphParser;
    private final CallLogMapper callLogMapper;
    private final CallEventMapper callEventMapper;
    private final ObjectMapper objectMapper;

    public FlowHealthService(FlowStore flowStore,
                             FlowGraphParser graphParser,
                             CallLogMapper callLogMapper,
                             CallEventMapper callEventMapper,
                             ObjectMapper objectMapper) {
        this.flowStore = flowStore;
        this.graphParser = graphParser;
        this.callLogMapper = callLogMapper;
        this.callEventMapper = callEventMapper;
        this.objectMapper = objectMapper;
    }

    public FlowHealthResponse check(Long flowId) {
        Map<String, Object> detail = flowStore.detail(flowId);
        String flowName = Objects.toString(detail.get("flowName"), "未命名流程");
        String graphJson = Objects.toString(detail.get("graphJson"), "");

        FlowHealthResponse response = new FlowHealthResponse();
        response.setFlowId(flowId);
        response.setFlowName(flowName);

        List<FlowHealthResponse.Issue> issues = new ArrayList<>();
        Map<String, FlowHealthResponse.NodeStat> nodeStats = new LinkedHashMap<>();
        Map<String, String> fallbackBranches = new HashMap<>();

        FlowGraph graph = parseGraph(graphJson, issues);
        if (graph != null) {
            buildNodeStats(graph, nodeStats, fallbackBranches);
            addPublishValidationIssues(graphJson, issues);
            addStructureAdvice(graph, issues);
        }

        FlowHealthResponse.RuntimeStats runtimeStats = applyRuntimeStats(flowId, nodeStats, fallbackBranches, issues);
        applyNodeLevels(nodeStats, issues);

        response.setRuntimeStats(runtimeStats);
        response.setIssues(issues);
        response.setNodes(nodeStats.values().stream()
                .sorted(Comparator.comparing(FlowHealthResponse.NodeStat::getNodeId))
                .toList());
        response.setScore(score(issues, nodeStats));
        response.setGrade(grade(response.getScore()));
        response.setSummary(summary(response.getScore(), runtimeStats, issues));
        return response;
    }

    private FlowGraph parseGraph(String graphJson, List<FlowHealthResponse.Issue> issues) {
        try {
            return graphParser.parse(graphJson);
        } catch (Exception e) {
            issues.add(issue(
                    "error",
                    "结构",
                    "",
                    "",
                    "流程图 JSON 无法解析",
                    "请先回到流程编辑器保存一份合法的流程图。"
            ));
            return null;
        }
    }

    private void buildNodeStats(FlowGraph graph,
                                Map<String, FlowHealthResponse.NodeStat> nodeStats,
                                Map<String, String> fallbackBranches) {
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, Integer> outgoing = new HashMap<>();
        for (FlowGraph.Edge edge : graph.getEdges()) {
            outgoing.merge(edge.getSourceNodeId(), 1, Integer::sum);
            incoming.merge(edge.getTargetNodeId(), 1, Integer::sum);
        }
        for (FlowGraph.Node node : graph.getNodes().values()) {
            FlowHealthResponse.NodeStat stat = new FlowHealthResponse.NodeStat();
            stat.setNodeId(node.getId());
            stat.setNodeName(node.name());
            stat.setNodeType(node.bizType());
            stat.setIncoming(incoming.getOrDefault(node.getId(), 0));
            stat.setOutgoing(outgoing.getOrDefault(node.getId(), 0));
            nodeStats.put(node.getId(), stat);
            fallbackBranches.put(node.getId(), fallbackBranch(node));
        }
    }

    private void addPublishValidationIssues(String graphJson, List<FlowHealthResponse.Issue> issues) {
        for (String message : flowStore.validateGraphJson(graphJson)) {
            issues.add(issue(
                    "error",
                    "发布校验",
                    "",
                    "",
                    message,
                    "这是发布前必须修复的问题，请按提示补齐节点配置或连线。"
            ));
        }
    }

    private void addStructureAdvice(FlowGraph graph, List<FlowHealthResponse.Issue> issues) {
        if (graph.getNodes().size() > 20) {
            issues.add(issue(
                    "info",
                    "结构",
                    "",
                    "",
                    "流程节点较多，后续维护成本会变高",
                    "可以把重复问答或复杂分支拆成子流程，先保持主流程清晰。"
            ));
        }
        for (FlowGraph.Node node : graph.getNodes().values()) {
            String type = node.bizType();
            if ("dtmf".equals(type) && !hasFallbackLikeBranch(graph.outgoing(node.getId()))) {
                issues.add(issue(
                        "warning",
                        "体验",
                        node.getId(),
                        node.name(),
                        "按键节点缺少明显的兜底分支",
                        "建议增加 0、default 或 fallback 分支，用户按错时可以转人工或重新引导。"
                ));
            }
            if ("asr".equals(type) && !StringUtils.hasText(node.stringProp("prompt", ""))) {
                issues.add(issue(
                        "warning",
                        "体验",
                        node.getId(),
                        node.name(),
                        "语音识别节点没有配置提示语",
                        "建议明确告诉用户可以怎么说，例如「请说出您要咨询的问题」。"
                ));
            }
            if ("play".equals(type) && promptText(node).length() > LONG_PROMPT_LIMIT) {
                issues.add(issue(
                        "info",
                        "体验",
                        node.getId(),
                        node.name(),
                        "播放话术偏长，用户可能听不完",
                        "建议拆成短句，关键选项放在最后重复一次。"
                ));
            }
            if ("rag".equals(type)) {
                int topK = node.intProp("topK", 3);
                if (topK < 1 || topK > 8) {
                    issues.add(issue(
                            "warning",
                            "AI",
                            node.getId(),
                            node.name(),
                            "AI 问答节点 topK 参数不在推荐范围内",
                            "知识库较小时可设 3，资料相似度较分散时可调到 5，但不建议过大。"
                    ));
                }
            }
        }
        FlowGraph.Node start = graph.findStart();
        if (start != null) {
            Set<String> reachable = reachableNodeIds(start.getId(), graph);
            for (FlowGraph.Node node : graph.getNodes().values()) {
                if (!reachable.contains(node.getId())) {
                    issues.add(issue(
                            "error",
                            "结构",
                            node.getId(),
                            node.name(),
                            "节点未接入主流程",
                            "请删除该节点，或从开始节点可到达的路径接入它。"
                    ));
                }
            }
        }
    }

    private FlowHealthResponse.RuntimeStats applyRuntimeStats(Long flowId,
                                                              Map<String, FlowHealthResponse.NodeStat> nodeStats,
                                                              Map<String, String> fallbackBranches,
                                                              List<FlowHealthResponse.Issue> issues) {
        List<CallLog> logs = callLogMapper.selectList(new LambdaQueryWrapper<CallLog>()
                .eq(CallLog::getFlowId, flowId)
                .orderByDesc(CallLog::getStartTime)
                .orderByDesc(CallLog::getId)
                .last("LIMIT " + SAMPLE_LIMIT));
        FlowHealthResponse.RuntimeStats stats = new FlowHealthResponse.RuntimeStats();
        stats.setSampleCalls(logs.size());
        stats.setEndedCalls((int) logs.stream().filter(log -> log.getEndTime() != null).count());
        stats.setRunningCalls((int) logs.stream().filter(log -> log.getEndTime() == null).count());
        stats.setTransferCalls((int) logs.stream().filter(log -> "transfer".equals(log.getEndReason())).count());
        stats.setErrorCalls((int) logs.stream().filter(log -> "error".equals(log.getEndReason())).count());
        stats.setTimeoutCalls((int) logs.stream().filter(log -> "timeout".equals(log.getEndReason())).count());
        stats.setAvgDurationSeconds(avgDuration(logs));

        if (logs.isEmpty()) {
            issues.add(issue(
                    "info",
                    "运行",
                    "",
                    "",
                    "暂无通话样本，当前评分主要依据流程结构",
                    "发布并完成几次调试或真实呼叫后，节点效果统计会更有参考价值。"
            ));
            return stats;
        }

        applyEventStats(logs, nodeStats, fallbackBranches);
        addRuntimeIssues(stats, issues);
        addNodeRuntimeIssues(nodeStats, issues);
        return stats;
    }

    private void applyEventStats(List<CallLog> logs,
                                 Map<String, FlowHealthResponse.NodeStat> nodeStats,
                                 Map<String, String> fallbackBranches) {
        List<String> callUuids = logs.stream()
                .map(CallLog::getCallUuid)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (callUuids.isEmpty()) {
            return;
        }
        List<CallEvent> events = callEventMapper.selectList(new LambdaQueryWrapper<CallEvent>()
                .in(CallEvent::getCallUuid, callUuids)
                .orderByAsc(CallEvent::getCallUuid)
                .orderByAsc(CallEvent::getEventTime)
                .orderByAsc(CallEvent::getId));
        for (CallEvent event : events) {
            if (!StringUtils.hasText(event.getNodeKey())) {
                continue;
            }
            FlowHealthResponse.NodeStat stat = nodeStats.computeIfAbsent(event.getNodeKey(), key -> oldNodeStat(key, event));
            Map<String, Object> payload = payloadMap(event.getPayload());
            if ("enter".equals(event.getEventType())) {
                stat.setEnterCount(stat.getEnterCount() + 1);
            }
            if (isErrorEvent(event, payload)) {
                stat.setErrorCount(stat.getErrorCount() + 1);
            }
            if (isFallbackEvent(event, payload, fallbackBranches.get(event.getNodeKey()))) {
                stat.setFallbackCount(stat.getFallbackCount() + 1);
            }
            if (isAiHitEvent(event, payload, fallbackBranches.get(event.getNodeKey()))) {
                stat.setAiHitCount(stat.getAiHitCount() + 1);
            }
            if ("transfer".equals(event.getNodeType()) && "enter".equals(event.getEventType())) {
                stat.setTransferCount(stat.getTransferCount() + 1);
            }
        }
        for (FlowHealthResponse.NodeStat stat : nodeStats.values()) {
            if (stat.getEnterCount() == null || stat.getEnterCount() == 0) {
                continue;
            }
            int good = stat.getEnterCount() - stat.getErrorCount() - stat.getFallbackCount();
            double rate = Math.max(0, good) * 1.0 / stat.getEnterCount();
            stat.setSuccessRate(Math.round(rate * 1000) / 1000.0);
        }
    }

    private void addRuntimeIssues(FlowHealthResponse.RuntimeStats stats, List<FlowHealthResponse.Issue> issues) {
        int sampleCalls = stats.getSampleCalls();
        if (sampleCalls < 5) {
            issues.add(issue(
                    "info",
                    "运行",
                    "",
                    "",
                    "通话样本较少，运行评分可能不稳定",
                    "建议至少完成 5 到 10 次覆盖主要分支的调试后再判断效果。"
            ));
        }
        if (ratio(stats.getErrorCalls(), sampleCalls) >= 0.2) {
            issues.add(issue(
                    "error",
                    "运行",
                    "",
                    "",
                    "最近通话错误结束比例偏高",
                    "优先查看通话事件中的 error、llm_failed、retrieve_failed 等 payload。"
            ));
        }
        if (ratio(stats.getTimeoutCalls(), sampleCalls) >= 0.2) {
            issues.add(issue(
                    "warning",
                    "运行",
                    "",
                    "",
                    "最近通话超时或未匹配比例偏高",
                    "检查按键分支、默认分支和用户提示语是否足够清楚。"
            ));
        }
        if (ratio(stats.getTransferCalls(), sampleCalls) >= 0.6) {
            issues.add(issue(
                    "warning",
                    "运行",
                    "",
                    "",
                    "转人工比例偏高",
                    "如果目标是自助解决，建议补充知识库内容、意图分支和失败兜底话术。"
            ));
        }
    }

    private void addNodeRuntimeIssues(Map<String, FlowHealthResponse.NodeStat> nodeStats,
                                      List<FlowHealthResponse.Issue> issues) {
        for (FlowHealthResponse.NodeStat stat : nodeStats.values()) {
            if (stat.getEnterCount() == null || stat.getEnterCount() < 3) {
                continue;
            }
            double fallbackRate = ratio(stat.getFallbackCount(), stat.getEnterCount());
            double errorRate = ratio(stat.getErrorCount(), stat.getEnterCount());
            if (errorRate >= 0.2) {
                issues.add(issue(
                        "error",
                        "节点效果",
                        stat.getNodeId(),
                        stat.getNodeName(),
                        "节点失败率偏高",
                        "请查看该节点的通话事件 payload，优先处理接口、LLM 或配置异常。"
                ));
            } else if (fallbackRate >= 0.3) {
                issues.add(issue(
                        "warning",
                        "节点效果",
                        stat.getNodeId(),
                        stat.getNodeName(),
                        "节点 fallback 比例偏高",
                        "AI 节点可以优化知识库、提示词和 topK；按键节点可以补默认分支和更清晰话术。"
                ));
            } else if (stat.getSuccessRate() != null && stat.getSuccessRate() < 0.6) {
                issues.add(issue(
                        "warning",
                        "节点效果",
                        stat.getNodeId(),
                        stat.getNodeName(),
                        "节点成功率偏低",
                        "建议先用调试弹窗覆盖该节点的主要输入场景。"
                ));
            }
        }
    }

    private void applyNodeLevels(Map<String, FlowHealthResponse.NodeStat> nodeStats,
                                 List<FlowHealthResponse.Issue> issues) {
        Map<String, String> levels = new HashMap<>();
        for (FlowHealthResponse.Issue issue : issues) {
            if (!StringUtils.hasText(issue.getNodeId())) {
                continue;
            }
            String current = levels.get(issue.getNodeId());
            if (current == null || severity(issue.getLevel()) > severity(current)) {
                levels.put(issue.getNodeId(), issue.getLevel());
            }
        }
        for (FlowHealthResponse.NodeStat stat : nodeStats.values()) {
            String issueLevel = levels.get(stat.getNodeId());
            if ("error".equals(issueLevel)) {
                stat.setHealthLevel("danger");
            } else if ("warning".equals(issueLevel)) {
                stat.setHealthLevel("warning");
            } else if (stat.getEnterCount() != null && stat.getEnterCount() > 0) {
                stat.setHealthLevel("success");
            } else {
                stat.setHealthLevel("info");
            }
        }
    }

    private int score(List<FlowHealthResponse.Issue> issues,
                      Map<String, FlowHealthResponse.NodeStat> nodeStats) {
        int result = 100;
        boolean hasHardError = false;
        for (FlowHealthResponse.Issue issue : issues) {
            if ("error".equals(issue.getLevel())) {
                result -= 18;
                hasHardError = true;
            } else if ("warning".equals(issue.getLevel())) {
                result -= 8;
            } else {
                result -= 2;
            }
        }
        for (FlowHealthResponse.NodeStat stat : nodeStats.values()) {
            if (stat.getEnterCount() != null && stat.getEnterCount() >= 3 && stat.getSuccessRate() != null) {
                if (stat.getSuccessRate() < 0.5) {
                    result -= 8;
                } else if (stat.getSuccessRate() < 0.75) {
                    result -= 4;
                }
            }
        }
        result = Math.max(0, Math.min(100, result));
        if (hasHardError) {
            result = Math.min(result, 59);
        }
        return result;
    }

    private String grade(int score) {
        if (score >= 90) {
            return "A";
        }
        if (score >= 75) {
            return "B";
        }
        if (score >= 60) {
            return "C";
        }
        return "D";
    }

    private String summary(int score,
                           FlowHealthResponse.RuntimeStats stats,
                           List<FlowHealthResponse.Issue> issues) {
        long errors = issues.stream().filter(issue -> "error".equals(issue.getLevel())).count();
        long warnings = issues.stream().filter(issue -> "warning".equals(issue.getLevel())).count();
        if (score >= 90) {
            return "流程结构较完整，近期运行数据也比较稳定，可以继续补充更多样本观察。";
        }
        if (errors > 0) {
            return "流程存在必须修复的问题，建议先处理红色问题后再发布或绑定热线。";
        }
        if (warnings > 0) {
            return "流程可以运行，但还有影响体验或命中效果的风险点，建议按问题列表逐项优化。";
        }
        if (stats.getSampleCalls() == 0) {
            return "流程结构暂无明显问题，但还没有运行样本，评分暂时只能代表静态体检结果。";
        }
        return "流程整体可用，后续可以结合更多真实通话继续优化。";
    }

    private FlowHealthResponse.NodeStat oldNodeStat(String nodeKey, CallEvent event) {
        FlowHealthResponse.NodeStat stat = new FlowHealthResponse.NodeStat();
        stat.setNodeId(nodeKey);
        stat.setNodeName(nodeKey);
        stat.setNodeType(StringUtils.hasText(event.getNodeType()) ? event.getNodeType() : "unknown");
        return stat;
    }

    private Map<String, Object> payloadMap(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean isErrorEvent(CallEvent event, Map<String, Object> payload) {
        if ("error".equals(event.getEventType())) {
            return true;
        }
        String status = lower(payload.get("status"));
        return Set.of("failed", "llm_failed", "retrieve_failed", "non_2xx", "error").contains(status);
    }

    private boolean isFallbackEvent(CallEvent event, Map<String, Object> payload, String fallbackBranch) {
        String status = lower(payload.get("status"));
        if (Set.of("skipped", "no_hits", "llm_failed", "retrieve_failed", "non_2xx", "failed").contains(status)) {
            return true;
        }
        if ("intent".equals(event.getEventType())) {
            String hit = Objects.toString(payload.get("hit"), "");
            return StringUtils.hasText(fallbackBranch) && fallbackBranch.equals(hit);
        }
        return false;
    }

    private boolean isAiHitEvent(CallEvent event, Map<String, Object> payload, String fallbackBranch) {
        if ("rag".equals(event.getEventType())) {
            return "ok".equals(lower(payload.get("status")));
        }
        if ("intent".equals(event.getEventType())) {
            String hit = Objects.toString(payload.get("hit"), "");
            return StringUtils.hasText(hit) && !Objects.equals(hit, fallbackBranch);
        }
        return false;
    }

    private String fallbackBranch(FlowGraph.Node node) {
        String type = node.bizType();
        if ("intent".equals(type)) {
            return node.stringProp("fallbackBranch", "other");
        }
        if ("http".equals(type) || "rag".equals(type)) {
            return node.stringProp("fallbackBranch", "fallback");
        }
        return "";
    }

    private boolean hasFallbackLikeBranch(List<FlowGraph.Edge> outgoing) {
        for (FlowGraph.Edge edge : outgoing) {
            String key = edge.branchKey();
            if (Set.of("0", "default", "fallback", "other").contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String promptText(FlowGraph.Node node) {
        String text = node.stringProp("ttsText", "");
        if (!StringUtils.hasText(text)) {
            text = node.stringProp("prompt", "");
        }
        return text;
    }

    private Set<String> reachableNodeIds(String startNodeId, FlowGraph graph) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            if (!visited.add(nodeId)) {
                continue;
            }
            for (FlowGraph.Edge edge : graph.outgoing(nodeId)) {
                if (!visited.contains(edge.getTargetNodeId())) {
                    queue.add(edge.getTargetNodeId());
                }
            }
        }
        return visited;
    }

    private FlowHealthResponse.Issue issue(String level,
                                           String category,
                                           String nodeId,
                                           String nodeName,
                                           String message,
                                           String suggestion) {
        FlowHealthResponse.Issue issue = new FlowHealthResponse.Issue();
        issue.setLevel(level);
        issue.setCategory(category);
        issue.setNodeId(nodeId);
        issue.setNodeName(nodeName);
        issue.setMessage(message);
        issue.setSuggestion(suggestion);
        return issue;
    }

    private int avgDuration(List<CallLog> logs) {
        List<Integer> durations = logs.stream()
                .map(CallLog::getDuration)
                .filter(Objects::nonNull)
                .filter(duration -> duration >= 0)
                .toList();
        if (durations.isEmpty()) {
            return 0;
        }
        return (int) Math.round(durations.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private double ratio(Integer value, Integer total) {
        if (value == null || total == null || total == 0) {
            return 0;
        }
        return value * 1.0 / total;
    }

    private int severity(String level) {
        if ("error".equals(level)) {
            return 3;
        }
        if ("warning".equals(level)) {
            return 2;
        }
        return 1;
    }

    private String lower(Object value) {
        return Objects.toString(value, "").trim().toLowerCase();
    }
}
