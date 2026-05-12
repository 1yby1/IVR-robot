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
        List<FlowHealthResponse.Diagnosis> diagnoses;
        List<FlowHealthResponse.PathStat> paths = new ArrayList<>();
        Map<String, FlowHealthResponse.NodeStat> nodeStats = new LinkedHashMap<>();
        Map<String, String> fallbackBranches = new HashMap<>();

        FlowGraph graph = parseGraph(graphJson, issues);
        if (graph != null) {
            buildNodeStats(graph, nodeStats, fallbackBranches);
            addPublishValidationIssues(graphJson, issues);
            addStructureAdvice(graph, issues);
        }

        FlowHealthResponse.RuntimeStats runtimeStats = applyRuntimeStats(flowId, nodeStats, fallbackBranches, issues, paths);
        applyNodeLevels(nodeStats, issues);
        diagnoses = buildDiagnoses(nodeStats, runtimeStats, issues, paths);

        response.setRuntimeStats(runtimeStats);
        response.setIssues(issues);
        response.setDiagnoses(diagnoses);
        response.setNodes(nodeStats.values().stream()
                .sorted(Comparator.comparing(FlowHealthResponse.NodeStat::getNodeId))
                .toList());
        response.setPaths(paths);
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
                                                              List<FlowHealthResponse.Issue> issues,
                                                              List<FlowHealthResponse.PathStat> paths) {
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

        List<CallEvent> events = loadEvents(logs);
        applyEventStats(events, nodeStats, fallbackBranches);
        paths.addAll(buildPathStats(logs, events, nodeStats));
        addRuntimeIssues(stats, issues);
        addNodeRuntimeIssues(nodeStats, issues);
        return stats;
    }

    private List<CallEvent> loadEvents(List<CallLog> logs) {
        List<String> callUuids = logs.stream()
                .map(CallLog::getCallUuid)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (callUuids.isEmpty()) {
            return List.of();
        }
        return callEventMapper.selectList(new LambdaQueryWrapper<CallEvent>()
                .in(CallEvent::getCallUuid, callUuids)
                .orderByAsc(CallEvent::getCallUuid)
                .orderByAsc(CallEvent::getEventTime)
                .orderByAsc(CallEvent::getId));
    }

    private void applyEventStats(List<CallEvent> events,
                                 Map<String, FlowHealthResponse.NodeStat> nodeStats,
                                 Map<String, String> fallbackBranches) {
        for (CallEvent event : events) {
            if (!StringUtils.hasText(event.getNodeKey())) {
                continue;
            }
            FlowHealthResponse.NodeStat stat = nodeStats.computeIfAbsent(event.getNodeKey(), key -> oldNodeStat(key, event));
            Map<String, Object> payload = payloadMap(event.getPayload());
            trackSampleCall(stat, event.getCallUuid());
            String status = diagnosticStatus(event, payload, fallbackBranches.get(event.getNodeKey()));
            if (StringUtils.hasText(status)) {
                increment(stat.getStatusCounts(), status);
            }
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

    private List<FlowHealthResponse.PathStat> buildPathStats(List<CallLog> logs,
                                                             List<CallEvent> events,
                                                             Map<String, FlowHealthResponse.NodeStat> nodeStats) {
        if (logs.isEmpty() || events.isEmpty()) {
            return List.of();
        }
        Map<String, CallLog> logsByUuid = logs.stream()
                .filter(log -> StringUtils.hasText(log.getCallUuid()))
                .collect(LinkedHashMap::new, (map, log) -> map.put(log.getCallUuid(), log), Map::putAll);
        Map<String, List<CallEvent>> eventsByCall = new LinkedHashMap<>();
        for (CallEvent event : events) {
            if (StringUtils.hasText(event.getCallUuid())) {
                eventsByCall.computeIfAbsent(event.getCallUuid(), key -> new ArrayList<>()).add(event);
            }
        }

        Map<String, PathBucket> buckets = new LinkedHashMap<>();
        for (Map.Entry<String, List<CallEvent>> entry : eventsByCall.entrySet()) {
            List<String> nodeIds = new ArrayList<>();
            for (CallEvent event : entry.getValue()) {
                if (!"enter".equals(event.getEventType()) || !StringUtils.hasText(event.getNodeKey())) {
                    continue;
                }
                if (nodeIds.isEmpty() || !nodeIds.get(nodeIds.size() - 1).equals(event.getNodeKey())) {
                    nodeIds.add(event.getNodeKey());
                }
            }
            if (nodeIds.isEmpty()) {
                continue;
            }
            String pathKey = String.join(">", nodeIds);
            PathBucket bucket = buckets.computeIfAbsent(pathKey, key -> new PathBucket(nodeIds, entry.getKey()));
            bucket.count++;
            CallLog log = logsByUuid.get(entry.getKey());
            String endReason = log == null ? "" : Objects.toString(log.getEndReason(), "");
            increment(bucket.endReasonCounts, StringUtils.hasText(endReason) ? endReason : "unknown");
            if (isBadEndReason(endReason)) {
                bucket.badCount++;
            }
        }

        return buckets.entrySet().stream()
                .map(entry -> toPathStat(entry.getKey(), entry.getValue(), nodeStats))
                .sorted(Comparator
                        .comparing(FlowHealthResponse.PathStat::getBadCount, Comparator.reverseOrder())
                        .thenComparing(FlowHealthResponse.PathStat::getCount, Comparator.reverseOrder()))
                .limit(8)
                .toList();
    }

    private FlowHealthResponse.PathStat toPathStat(String pathKey,
                                                   PathBucket bucket,
                                                   Map<String, FlowHealthResponse.NodeStat> nodeStats) {
        FlowHealthResponse.PathStat stat = new FlowHealthResponse.PathStat();
        stat.setPathKey(pathKey);
        stat.setPathText(bucket.nodeIds.stream()
                .map(nodeId -> {
                    FlowHealthResponse.NodeStat node = nodeStats.get(nodeId);
                    return node == null ? nodeId : node.getNodeName();
                })
                .collect(java.util.stream.Collectors.joining(" -> ")));
        stat.setCount(bucket.count);
        stat.setBadCount(bucket.badCount);
        stat.setBadRate(Math.round(ratio(bucket.badCount, bucket.count) * 1000) / 1000.0);
        stat.setMainEndReason(mainEndReason(bucket.endReasonCounts));
        stat.setSampleCallUuid(bucket.sampleCallUuid);
        if (bucket.badCount > 0 && stat.getBadRate() >= 0.5) {
            stat.setLevel("danger");
        } else if (bucket.badCount > 0) {
            stat.setLevel("warning");
        } else {
            stat.setLevel("success");
        }
        return stat;
    }

    private String mainEndReason(Map<String, Integer> endReasonCounts) {
        return endReasonCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private boolean isBadEndReason(String endReason) {
        return Set.of("transfer", "error", "timeout", "rejected").contains(lower(endReason));
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

    private List<FlowHealthResponse.Diagnosis> buildDiagnoses(Map<String, FlowHealthResponse.NodeStat> nodeStats,
                                                              FlowHealthResponse.RuntimeStats runtimeStats,
                                                              List<FlowHealthResponse.Issue> issues,
                                                              List<FlowHealthResponse.PathStat> paths) {
        List<FlowHealthResponse.Diagnosis> diagnoses = new ArrayList<>();
        long hardErrors = issues.stream().filter(issue -> "error".equals(issue.getLevel())).count();
        if (hardErrors > 0) {
            diagnoses.add(diagnosis(
                    "P0",
                    "error",
                    "流程存在发布级阻断问题",
                    "invalid_graph_or_required_config_missing",
                    "问题清单中存在 " + hardErrors + " 个严重问题，发布或线上运行可能直接失败。",
                    "先修复红色问题，再重新发布并跑一次调试样本。",
                    0.95,
                    "",
                    "",
                    List.of()
            ));
        }
        if (runtimeStats.getSampleCalls() == null || runtimeStats.getSampleCalls() == 0) {
            diagnoses.add(diagnosis(
                    "P2",
                    "info",
                    "缺少运行样本，暂时只能做静态诊断",
                    "no_runtime_samples",
                    "最近样本通话为 0，节点效果、路径风险和 AI 命中率还没有足够证据。",
                    "先用调试弹窗覆盖主路径、fallback 路径和异常路径，每条路径至少跑 1 到 2 次。",
                    0.9,
                    "",
                    "",
                    List.of()
            ));
        }

        for (FlowHealthResponse.NodeStat stat : nodeStats.values()) {
            int enter = value(stat.getEnterCount());
            Map<String, Integer> counts = stat.getStatusCounts() == null ? Map.of() : stat.getStatusCounts();
            String type = Objects.toString(stat.getNodeType(), "");
            if ("rag".equals(type)) {
                addRagDiagnoses(diagnoses, stat, enter, counts);
            } else if ("http".equals(type)) {
                addHttpDiagnoses(diagnoses, stat, enter, counts);
            } else if ("intent".equals(type)) {
                addIntentDiagnoses(diagnoses, stat, enter, counts);
            }
            addGenericNodeDiagnoses(diagnoses, stat, enter);
        }

        for (FlowHealthResponse.PathStat path : paths) {
            if (value(path.getBadCount()) <= 0) {
                continue;
            }
            if (value(path.getBadCount()) >= 2 || value(path.getCount()) <= 3) {
                diagnoses.add(diagnosis(
                        "P1",
                        "warning",
                        "存在高风险通话路径",
                        "bad_path_cluster",
                        "路径「" + path.getPathText() + "」出现 " + path.getCount() + " 次，其中 "
                                + path.getBadCount() + " 次以 " + path.getMainEndReason() + " 结束。",
                        "打开通话日志的路径回放，优先查看样本 " + path.getSampleCallUuid() + "，定位是话术、分支还是 AI 节点导致。",
                        Math.min(0.95, 0.55 + path.getBadRate()),
                        "",
                        "",
                        StringUtils.hasText(path.getSampleCallUuid()) ? List.of(path.getSampleCallUuid()) : List.of()
                ));
            }
        }

        if (diagnoses.isEmpty()) {
            diagnoses.add(diagnosis(
                    "P2",
                    "success",
                    "暂未发现明显瓶颈",
                    "healthy_baseline",
                    "当前结构校验、节点运行统计和路径样本没有暴露明显异常。",
                    "继续积累真实通话样本，后续可以重点观察 RAG 命中率、转人工率和平均耗时。",
                    0.75,
                    "",
                    "",
                    List.of()
            ));
        }
        return diagnoses.stream()
                .sorted(Comparator
                        .comparing((FlowHealthResponse.Diagnosis item) -> priorityWeight(item.getPriority()))
                        .thenComparing(FlowHealthResponse.Diagnosis::getConfidence, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();
    }

    private void addRagDiagnoses(List<FlowHealthResponse.Diagnosis> diagnoses,
                                 FlowHealthResponse.NodeStat stat,
                                 int enter,
                                 Map<String, Integer> counts) {
        int llmFailed = count(counts, "llm_failed");
        int retrieveFailed = count(counts, "retrieve_failed");
        int noHits = count(counts, "no_hits");
        int skipped = count(counts, "skipped");
        if (isNotable(llmFailed, enter, 0.2)) {
            diagnoses.add(nodeDiagnosis(
                    "P0",
                    "error",
                    "AI 问答节点生成失败较多",
                    "rag_llm_failed",
                    stat,
                    llmFailed + " 次 LLM 生成失败，占进入该节点样本的 " + percentText(llmFailed, enter) + "。",
                    "检查模型 API Key、base-url、模型名称和 LLM 调用日志；如果最近刚改环境变量，重启后端服务再测。",
                    confidence(llmFailed, enter)
            ));
        }
        if (isNotable(retrieveFailed, enter, 0.15)) {
            diagnoses.add(nodeDiagnosis(
                    "P0",
                    "error",
                    "AI 问答节点检索服务异常",
                    "rag_retrieve_failed",
                    stat,
                    retrieveFailed + " 次知识库检索失败，占进入该节点样本的 " + percentText(retrieveFailed, enter) + "。",
                    "检查知识库表、文档索引状态、向量库/检索实现是否可用，再用知识库调试功能单独验证。",
                    confidence(retrieveFailed, enter)
            ));
        }
        if (isNotable(noHits, enter, 0.25)) {
            diagnoses.add(nodeDiagnosis(
                    "P1",
                    "warning",
                    "AI 问答节点知识库未命中较多",
                    "rag_no_hits",
                    stat,
                    noHits + " 次未检索到知识片段，占进入该节点样本的 " + percentText(noHits, enter) + "。",
                    "补充知识库内容，检查 chunk 是否过短或过长；可以把 topK 从 3 调到 5 后用 RAG 测试集复测。",
                    confidence(noHits, enter)
            ));
        }
        if (isNotable(skipped, enter, 0.2)) {
            diagnoses.add(nodeDiagnosis(
                    "P1",
                    "warning",
                    "AI 问答节点经常没有拿到问题变量",
                    "rag_empty_question",
                    stat,
                    skipped + " 次跳过生成，占进入该节点样本的 " + percentText(skipped, enter) + "。",
                    "检查上游 ASR/意图节点是否把用户输入写入 questionVar，例如 lastAsr；必要时在流程调试变量表确认。",
                    confidence(skipped, enter)
            ));
        }
    }

    private void addHttpDiagnoses(List<FlowHealthResponse.Diagnosis> diagnoses,
                                  FlowHealthResponse.NodeStat stat,
                                  int enter,
                                  Map<String, Integer> counts) {
        int httpFailed = count(counts, "non_2xx") + count(counts, "failed") + count(counts, "error");
        if (isNotable(httpFailed, enter, 0.2)) {
            diagnoses.add(nodeDiagnosis(
                    "P1",
                    "warning",
                    "HTTP 节点接口失败较多",
                    "http_failed",
                    stat,
                    httpFailed + " 次接口异常或非 2xx 响应，占进入该节点样本的 " + percentText(httpFailed, enter) + "。",
                    "检查 URL、超时时间、请求体模板和外部接口稳定性；失败时应保留 fallback 分支。",
                    confidence(httpFailed, enter)
            ));
        }
    }

    private void addIntentDiagnoses(List<FlowHealthResponse.Diagnosis> diagnoses,
                                    FlowHealthResponse.NodeStat stat,
                                    int enter,
                                    Map<String, Integer> counts) {
        int fallbackHits = count(counts, "fallback_hit");
        if (isNotable(fallbackHits, enter, 0.3)) {
            diagnoses.add(nodeDiagnosis(
                    "P1",
                    "warning",
                    "意图识别经常走兜底分支",
                    "intent_fallback",
                    stat,
                    fallbackHits + " 次命中 fallback/other，占进入该节点样本的 " + percentText(fallbackHits, enter) + "。",
                    "补充候选意图和示例问法，避免多个意图描述过于相似；必要时增加转人工兜底。",
                    confidence(fallbackHits, enter)
            ));
        }
    }

    private void addGenericNodeDiagnoses(List<FlowHealthResponse.Diagnosis> diagnoses,
                                         FlowHealthResponse.NodeStat stat,
                                         int enter) {
        if (enter <= 0) {
            return;
        }
        int errors = value(stat.getErrorCount());
        int fallback = value(stat.getFallbackCount());
        if (isNotable(errors, enter, 0.2)) {
            diagnoses.add(nodeDiagnosis(
                    "P1",
                    "error",
                    "节点失败率偏高",
                    "node_error_rate_high",
                    stat,
                    errors + " 次失败，占进入该节点样本的 " + percentText(errors, enter) + "。",
                    "查看该节点相关通话事件 payload，优先处理配置异常、接口异常或模型异常。",
                    confidence(errors, enter)
            ));
        } else if (isNotable(fallback, enter, 0.35)) {
            diagnoses.add(nodeDiagnosis(
                    "P2",
                    "warning",
                    "节点 fallback 比例偏高",
                    "node_fallback_rate_high",
                    stat,
                    fallback + " 次走 fallback，占进入该节点样本的 " + percentText(fallback, enter) + "。",
                    "先回放该节点样本，确认是用户输入不清楚、分支缺失，还是 AI/RAG 命中效果不足。",
                    confidence(fallback, enter)
            ));
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

    private FlowHealthResponse.Diagnosis nodeDiagnosis(String priority,
                                                       String level,
                                                       String title,
                                                       String rootCause,
                                                       FlowHealthResponse.NodeStat node,
                                                       String evidence,
                                                       String action,
                                                       double confidence) {
        return diagnosis(
                priority,
                level,
                title,
                rootCause,
                evidence,
                action,
                confidence,
                node.getNodeId(),
                node.getNodeName(),
                node.getSampleCallUuids() == null ? List.of() : node.getSampleCallUuids().stream().limit(5).toList()
        );
    }

    private FlowHealthResponse.Diagnosis diagnosis(String priority,
                                                   String level,
                                                   String title,
                                                   String rootCause,
                                                   String evidence,
                                                   String action,
                                                   double confidence,
                                                   String relatedNodeId,
                                                   String relatedNodeName,
                                                   List<String> relatedCallUuids) {
        FlowHealthResponse.Diagnosis diagnosis = new FlowHealthResponse.Diagnosis();
        diagnosis.setPriority(priority);
        diagnosis.setLevel(level);
        diagnosis.setTitle(title);
        diagnosis.setRootCause(rootCause);
        diagnosis.setEvidence(evidence);
        diagnosis.setAction(action);
        diagnosis.setConfidence(Math.round(confidence * 1000) / 1000.0);
        diagnosis.setRelatedNodeId(relatedNodeId);
        diagnosis.setRelatedNodeName(relatedNodeName);
        diagnosis.setRelatedCallUuids(relatedCallUuids == null ? List.of() : relatedCallUuids);
        return diagnosis;
    }

    private String diagnosticStatus(CallEvent event, Map<String, Object> payload, String fallbackBranch) {
        String status = lower(payload.get("status"));
        if (StringUtils.hasText(status)) {
            return status;
        }
        if ("error".equals(event.getEventType())) {
            return "error";
        }
        if ("intent".equals(event.getEventType())) {
            String hit = lower(payload.get("hit"));
            if (!StringUtils.hasText(hit)) {
                return "";
            }
            if ((StringUtils.hasText(fallbackBranch) && fallbackBranch.equals(hit))
                    || Set.of("other", "fallback").contains(hit)) {
                return "fallback_hit";
            }
            return "intent_hit";
        }
        return "";
    }

    private void trackSampleCall(FlowHealthResponse.NodeStat stat, String callUuid) {
        if (!StringUtils.hasText(callUuid)) {
            return;
        }
        List<String> samples = stat.getSampleCallUuids();
        if (samples == null) {
            samples = new ArrayList<>();
            stat.setSampleCallUuids(samples);
        }
        if (samples.size() < 5 && !samples.contains(callUuid)) {
            samples.add(callUuid);
        }
    }

    private void increment(Map<String, Integer> counts, String key) {
        if (counts == null || !StringUtils.hasText(key)) {
            return;
        }
        counts.merge(key, 1, Integer::sum);
    }

    private int count(Map<String, Integer> counts, String key) {
        if (counts == null || key == null) {
            return 0;
        }
        return counts.getOrDefault(key, 0);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isNotable(int count, int total, double rate) {
        if (count <= 0) {
            return false;
        }
        if (total <= 3) {
            return true;
        }
        return count >= 2 || ratio(count, total) >= rate;
    }

    private String percentText(int value, int total) {
        if (total <= 0) {
            return "0%";
        }
        return Math.round(ratio(value, total) * 100) + "%";
    }

    private double confidence(int count, int total) {
        if (total <= 0) {
            return 0.55;
        }
        return Math.min(0.95, 0.55 + ratio(count, total) * 0.4 + Math.min(count, 5) * 0.03);
    }

    private int priorityWeight(String priority) {
        if ("P0".equals(priority)) {
            return 0;
        }
        if ("P1".equals(priority)) {
            return 1;
        }
        return 2;
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

    private static class PathBucket {
        private final List<String> nodeIds;
        private final String sampleCallUuid;
        private final Map<String, Integer> endReasonCounts = new LinkedHashMap<>();
        private int count;
        private int badCount;

        private PathBucket(List<String> nodeIds, String sampleCallUuid) {
            this.nodeIds = nodeIds;
            this.sampleCallUuid = sampleCallUuid;
        }
    }
}
