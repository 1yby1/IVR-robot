package com.ivr.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ivr.admin.dto.FlowOptionItem;
import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.entity.IvrFlowVersion;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.admin.mapper.IvrFlowVersionMapper;
import com.ivr.common.exception.BusinessException;
import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.graph.FlowGraphParser;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class FlowStore {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_GRAPH = """
            {"nodes":[{"id":"start","type":"circle","x":160,"y":200,"text":"Start","properties":{"bizType":"start","name":"Start"}},{"id":"welcome","type":"rect","x":360,"y":200,"text":"Welcome","properties":{"bizType":"play","name":"Welcome","ttsText":"Welcome to the IVR demo. Press 1 for business service, press 2 to transfer."}},{"id":"collect","type":"rect","x":590,"y":200,"text":"DTMF","properties":{"bizType":"dtmf","name":"DTMF","maxDigits":1,"timeoutSec":8,"mappings":[{"key":"1","nextNode":"business"},{"key":"2","nextNode":"transfer"}]}},{"id":"business","type":"rect","x":820,"y":120,"text":"Business","properties":{"bizType":"play","name":"Business","ttsText":"Business self service is complete."}},{"id":"transfer","type":"rect","x":820,"y":280,"text":"Transfer","properties":{"bizType":"transfer","name":"Transfer","target":"1000"}},{"id":"end","type":"circle","x":1040,"y":200,"text":"End","properties":{"bizType":"end","name":"End"}}],"edges":[{"id":"edge-start-welcome","type":"polyline","sourceNodeId":"start","targetNodeId":"welcome"},{"id":"edge-welcome-collect","type":"polyline","sourceNodeId":"welcome","targetNodeId":"collect"},{"id":"edge-collect-business","type":"polyline","sourceNodeId":"collect","targetNodeId":"business","text":"1","properties":{"key":"1"}},{"id":"edge-collect-transfer","type":"polyline","sourceNodeId":"collect","targetNodeId":"transfer","text":"2","properties":{"key":"2"}},{"id":"edge-business-end","type":"polyline","sourceNodeId":"business","targetNodeId":"end"}]}
            """;

    private final IvrFlowMapper flowMapper;
    private final IvrFlowVersionMapper versionMapper;
    private final FlowGraphParser graphParser;
    private final ObjectMapper objectMapper;

    public FlowStore(IvrFlowMapper flowMapper,
                     IvrFlowVersionMapper versionMapper,
                     FlowGraphParser graphParser,
                     ObjectMapper objectMapper) {
        this.flowMapper = flowMapper;
        this.versionMapper = versionMapper;
        this.graphParser = graphParser;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void ensureDemoFlow() {
        Long count = flowMapper.selectCount(new LambdaQueryWrapper<IvrFlow>().eq(IvrFlow::getDeleted, 0));
        if (count != null && count > 0) {
            return;
        }
        IvrFlow demo = new IvrFlow();
        demo.setFlowCode("demo-welcome");
        demo.setFlowName("Demo Welcome Flow");
        demo.setDescription("Demo flow: press 1 for self-service, press 2 to transfer.");
        demo.setStatus(1);
        demo.setCurrentVersion(1);
        demo.setCreatedBy(1L);
        demo.setUpdatedBy(1L);
        demo.setCreatedAt(LocalDateTime.now());
        demo.setUpdatedAt(demo.getCreatedAt());
        demo.setDeleted(0);
        flowMapper.insert(demo);

        IvrFlowVersion version = new IvrFlowVersion();
        version.setFlowId(demo.getId());
        version.setVersion(1);
        version.setGraphJson(DEFAULT_GRAPH);
        version.setChangeNote("Initial demo version");
        version.setPublished(1);
        version.setCreatedBy(1L);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);
    }

    public Map<String, Object> page(int current, int size, String keyword) {
        int safeCurrent = Math.max(current, 1);
        int safeSize = Math.max(size, 1);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<IvrFlow> wrapper = new LambdaQueryWrapper<IvrFlow>()
                .eq(IvrFlow::getDeleted, 0)
                .orderByDesc(IvrFlow::getUpdatedAt)
                .orderByDesc(IvrFlow::getId);
        if (StringUtils.hasText(normalizedKeyword)) {
            wrapper.and(w -> w.like(IvrFlow::getFlowCode, normalizedKeyword)
                    .or()
                    .like(IvrFlow::getFlowName, normalizedKeyword)
                    .or()
                    .like(IvrFlow::getDescription, normalizedKeyword));
        }

        Page<IvrFlow> page = flowMapper.selectPage(Page.of(safeCurrent, safeSize), wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords().stream().map(this::toListMap).toList());
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    public Map<String, Object> detail(Long id) {
        IvrFlow flow = getRequired(id);
        Map<String, Object> map = toListMap(flow);
        IvrFlowVersion version = editableVersion(flow);
        map.put("graphJson", version == null ? DEFAULT_GRAPH : version.getGraphJson());
        map.put("createdAt", formatTime(flow.getCreatedAt()));
        return map;
    }

    public String graphJson(Long id) {
        IvrFlow flow = getRequired(id);
        IvrFlowVersion version = editableVersion(flow);
        return version == null ? DEFAULT_GRAPH : version.getGraphJson();
    }

    public List<FlowOptionItem> publishedOptions() {
        return flowMapper.selectList(new LambdaQueryWrapper<IvrFlow>()
                        .eq(IvrFlow::getDeleted, 0)
                        .eq(IvrFlow::getStatus, 1)
                        .orderByDesc(IvrFlow::getUpdatedAt)
                        .orderByDesc(IvrFlow::getId))
                .stream()
                .map(flow -> {
                    FlowOptionItem item = new FlowOptionItem();
                    item.setId(flow.getId());
                    item.setFlowCode(flow.getFlowCode());
                    item.setFlowName(flow.getFlowName());
                    item.setCurrentVersion(Objects.requireNonNullElse(flow.getCurrentVersion(), 0));
                    return item;
                })
                .toList();
    }

    public List<Map<String, Object>> versions(Long id) {
        IvrFlow flow = getRequired(id);
        Integer currentVersion = Objects.requireNonNullElse(flow.getCurrentVersion(), 0);
        boolean draftDiff = hasDraftDiff(flow);
        return versionMapper.selectList(new LambdaQueryWrapper<IvrFlowVersion>()
                        .eq(IvrFlowVersion::getFlowId, id)
                        .orderByDesc(IvrFlowVersion::getVersion)
                        .orderByDesc(IvrFlowVersion::getId))
                .stream()
                .map(version -> toVersionMap(version, currentVersion, draftDiff))
                .toList();
    }

    public List<String> validateGraphJson(String graphJson) {
        try {
            validateBeforePublish(graphJson);
            return List.of();
        } catch (BusinessException e) {
            return splitValidationMessage(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> body, Long currentUserId) {
        IvrFlow flow = new IvrFlow();
        applyBody(flow, body);
        if (!StringUtils.hasText(flow.getFlowCode())) {
            flow.setFlowCode("flow-" + System.currentTimeMillis());
        }
        if (!StringUtils.hasText(flow.getFlowName())) {
            flow.setFlowName("Untitled Flow");
        }
        flow.setStatus(0);
        flow.setCurrentVersion(0);
        flow.setCreatedAt(LocalDateTime.now());
        flow.setUpdatedAt(flow.getCreatedAt());
        flow.setDeleted(0);

        try {
            flowMapper.insert(flow);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "Flow code already exists");
        }
        saveDraftVersion(flow.getId(), graphJsonValue(body), currentUserId);
        return flow.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Long id, Map<String, Object> body, Long currentUserId) {
        IvrFlow flow = getRequired(id);
        applyBody(flow, body);
        flow.setUpdatedBy(currentUserId);
        flow.setUpdatedAt(LocalDateTime.now());
        try {
            flowMapper.updateById(flow);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "Flow code already exists");
        }
        saveDraftVersion(id, graphJsonValue(body), flow.getUpdatedBy());
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Long currentUserId) {
        IvrFlow flow = getRequired(id);
        IvrFlowVersion editable = editableVersion(flow);
        String graphJson = editable == null ? DEFAULT_GRAPH : editable.getGraphJson();
        validateBeforePublish(graphJson);
        int nextVersion = Math.max(0, Objects.requireNonNullElse(flow.getCurrentVersion(), 0)) + 1;

        IvrFlowVersion version = new IvrFlowVersion();
        version.setFlowId(id);
        version.setVersion(nextVersion);
        version.setGraphJson(graphJson);
        version.setChangeNote("Publish version " + nextVersion);
        version.setPublished(1);
        version.setCreatedBy(currentUserId);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);

        flow.setStatus(1);
        flow.setCurrentVersion(nextVersion);
        flow.setUpdatedBy(currentUserId);
        flow.setUpdatedAt(LocalDateTime.now());
        flowMapper.updateById(flow);
    }

    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id, Long currentUserId) {
        IvrFlow flow = getRequired(id);
        flow.setStatus(2);
        flow.setUpdatedBy(currentUserId);
        flow.setUpdatedAt(LocalDateTime.now());
        flowMapper.updateById(flow);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreVersionToDraft(Long id, Integer version, Long currentUserId) {
        IvrFlow flow = getRequired(id);
        IvrFlowVersion source = versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                .eq(IvrFlowVersion::getFlowId, id)
                .eq(IvrFlowVersion::getVersion, version)
                .last("LIMIT 1"));
        if (source == null) {
            throw new BusinessException(404, "流程版本不存在");
        }
        saveDraftVersion(id, source.getGraphJson(), currentUserId);
        flow.setUpdatedBy(currentUserId);
        flow.setUpdatedAt(LocalDateTime.now());
        flowMapper.updateById(flow);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long currentUserId) {
        IvrFlow flow = getRequired(id);
        flow.setDeleted(1);
        flow.setUpdatedBy(currentUserId);
        flow.setUpdatedAt(LocalDateTime.now());
        flowMapper.updateById(flow);
    }

    public Map<String, Object> dashboardStats() {
        long published = flowMapper.selectCount(new LambdaQueryWrapper<IvrFlow>()
                .eq(IvrFlow::getDeleted, 0)
                .eq(IvrFlow::getStatus, 1));
        long drafts = flowMapper.selectCount(new LambdaQueryWrapper<IvrFlow>()
                .eq(IvrFlow::getDeleted, 0)
                .eq(IvrFlow::getStatus, 0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayCalls", 128);
        result.put("onlineFlows", published);
        result.put("draftFlows", drafts);
        result.put("activeUsers", 3);
        result.put("aiResolutionRate", 68);
        result.put("transferRate", 22);

        List<Map<String, Object>> recent = new ArrayList<>();
        recent.add(Map.of("time", "09:12", "caller", "138****1024", "flowName", "Demo Welcome Flow", "result", "Self-service"));
        recent.add(Map.of("time", "10:05", "caller", "186****3318", "flowName", "After-sales Hotline", "result", "Transfer"));
        recent.add(Map.of("time", "11:36", "caller", "159****8762", "flowName", "Voicemail Flow", "result", "Voicemail"));
        result.put("recentCalls", recent);
        return result;
    }

    private void validateBeforePublish(String graphJson) {
        FlowGraph graph;
        try {
            graph = graphParser.parse(graphJson);
        } catch (Exception e) {
            throw new BusinessException(400, "流程图 JSON 格式不正确");
        }

        List<String> errors = new ArrayList<>();
        if (graph.getNodes().isEmpty()) {
            errors.add("请至少添加一个节点");
        }

        List<FlowGraph.Node> startNodes = graph.getNodes().values().stream()
                .filter(node -> "start".equals(node.bizType()))
                .toList();
        if (startNodes.size() != 1) {
            errors.add("流程必须且只能有一个开始节点");
        }
        boolean hasTerminal = graph.getNodes().values().stream()
                .anyMatch(node -> isTerminal(node.bizType()));
        if (!hasTerminal) {
            errors.add("请至少添加一个结束、转人工或留言节点");
        }

        Set<String> nodeIds = graph.getNodes().keySet();
        for (FlowGraph.Edge edge : graph.getEdges()) {
            if (!nodeIds.contains(edge.getSourceNodeId()) || !nodeIds.contains(edge.getTargetNodeId())) {
                errors.add("存在指向不存在节点的连线");
            }
        }

        for (FlowGraph.Node node : graph.getNodes().values()) {
            String type = node.bizType();
            List<FlowGraph.Edge> outgoing = graph.outgoing(node.getId());
            if (!supportedNodeTypes().contains(type)) {
                errors.add("节点「" + node.name() + "」类型暂不支持：" + type);
                continue;
            }
            if (!isTerminal(type) && outgoing.isEmpty()) {
                errors.add("节点「" + node.name() + "」必须连接后续节点");
            }
            validateNodeConfig(node, outgoing, errors);
            validateDuplicateBranches(node, outgoing, errors);
        }

        if (startNodes.size() == 1) {
            Set<String> reachable = collectReachable(startNodes.get(0).getId(), graph);
            List<String> unreachable = graph.getNodes().values().stream()
                    .filter(node -> !reachable.contains(node.getId()))
                    .map(FlowGraph.Node::name)
                    .toList();
            if (!unreachable.isEmpty()) {
                errors.add("存在未接入主流程的节点：" + String.join("、", unreachable));
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(400, "流程无法发布：" + String.join("；", errors));
        }
    }

    private List<String> splitValidationMessage(String message) {
        String text = Objects.toString(message, "").trim();
        String prefix = "流程无法发布：";
        if (text.startsWith(prefix)) {
            text = text.substring(prefix.length());
        }
        if (!StringUtils.hasText(text)) {
            return List.of("流程图校验失败");
        }
        return List.of(text.split("；"));
    }

    private Set<String> supportedNodeTypes() {
        return Set.of("start", "end", "play", "dtmf", "condition", "var_assign",
                "http", "transfer", "voicemail", "asr", "intent", "rag");
    }

    private boolean isTerminal(String type) {
        return "end".equals(type) || "transfer".equals(type) || "voicemail".equals(type);
    }

    private void validateNodeConfig(FlowGraph.Node node, List<FlowGraph.Edge> outgoing, List<String> errors) {
        String type = node.bizType();
        if ("play".equals(type) && !StringUtils.hasText(node.stringProp("ttsText", ""))
                && !StringUtils.hasText(node.stringProp("audioUrl", ""))) {
            errors.add("播放节点「" + node.name() + "」需要配置播放文本或音频地址");
        }
        if ("transfer".equals(type) && !StringUtils.hasText(node.stringProp("target", ""))) {
            errors.add("转人工节点「" + node.name() + "」需要配置坐席号");
        }
        if ("condition".equals(type) && !StringUtils.hasText(node.stringProp("expression", ""))) {
            errors.add("条件节点「" + node.name() + "」需要配置表达式");
        }
        if ("var_assign".equals(type) && !StringUtils.hasText(node.stringProp("varName", ""))) {
            errors.add("变量赋值节点「" + node.name() + "」需要配置变量名");
        }
        if ("http".equals(type)) {
            if (!StringUtils.hasText(node.stringProp("url", ""))) {
                errors.add("HTTP 节点「" + node.name() + "」需要配置 URL");
            }
            if (!hasDefaultBranch(outgoing)) {
                errors.add("HTTP 节点「" + node.name() + "」需要一条默认成功连线");
            }
            requireBranch(node, outgoing, node.stringProp("fallbackBranch", "fallback"), errors);
        }
        if ("dtmf".equals(type)) {
            for (FlowGraph.Edge edge : outgoing) {
                if (!StringUtils.hasText(edge.branchKey())) {
                    errors.add("按键节点「" + node.name() + "」的每条连线都需要填写分支按键");
                    break;
                }
            }
        }
        if ("intent".equals(type)) {
            List<String> intents = readStringList(node.getProperties().get("intents"));
            if (intents.isEmpty()) {
                errors.add("AI 意图节点「" + node.name() + "」需要配置候选意图");
            }
            for (String intent : intents) {
                requireBranch(node, outgoing, intent, errors);
            }
            requireBranch(node, outgoing, node.stringProp("fallbackBranch", "other"), errors);
        }
        if ("rag".equals(type)) {
            if (!hasDefaultBranch(outgoing)) {
                errors.add("AI 问答节点「" + node.name() + "」需要一条默认成功连线");
            }
            requireBranch(node, outgoing, node.stringProp("fallbackBranch", "fallback"), errors);
        }
    }

    private void validateDuplicateBranches(FlowGraph.Node node, List<FlowGraph.Edge> outgoing, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (FlowGraph.Edge edge : outgoing) {
            String key = edge.branchKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (!seen.add(key)) {
                errors.add("节点「" + node.name() + "」存在重复分支：" + key);
            }
        }
    }

    private void requireBranch(FlowGraph.Node node,
                               List<FlowGraph.Edge> outgoing,
                               String branchKey,
                               List<String> errors) {
        if (!StringUtils.hasText(branchKey)) {
            return;
        }
        boolean exists = outgoing.stream().anyMatch(edge -> branchKey.equals(edge.branchKey()));
        if (!exists) {
            errors.add("节点「" + node.name() + "」缺少分支连线：" + branchKey);
        }
    }

    private boolean hasDefaultBranch(List<FlowGraph.Edge> outgoing) {
        return outgoing.stream().anyMatch(edge -> !StringUtils.hasText(edge.branchKey()));
    }

    private Set<String> collectReachable(String startNodeId, FlowGraph graph) {
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

    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = item.toString().trim();
                if (StringUtils.hasText(text)) {
                    result.add(text);
                }
            }
            return result;
        }
        if (value instanceof String text) {
            List<String> result = new ArrayList<>();
            for (String part : text.split("[,，\\n]")) {
                String item = part.trim();
                if (StringUtils.hasText(item)) {
                    result.add(item);
                }
            }
            return result;
        }
        return List.of();
    }

    private IvrFlow getRequired(Long id) {
        IvrFlow flow = flowMapper.selectById(id);
        if (flow == null || Objects.equals(flow.getDeleted(), 1)) {
            throw new BusinessException(404, "Flow does not exist");
        }
        return flow;
    }

    private IvrFlowVersion editableVersion(IvrFlow flow) {
        IvrFlowVersion draft = draftVersion(flow.getId());
        if (draft != null) {
            return draft;
        }
        Integer currentVersion = Objects.requireNonNullElse(flow.getCurrentVersion(), 0);
        if (currentVersion > 0) {
            IvrFlowVersion published = versionByNumber(flow.getId(), currentVersion);
            if (published != null) {
                return published;
            }
        }
        return versionMapper.selectList(new LambdaQueryWrapper<IvrFlowVersion>()
                        .eq(IvrFlowVersion::getFlowId, flow.getId())
                        .orderByDesc(IvrFlowVersion::getVersion)
                        .orderByDesc(IvrFlowVersion::getId))
                .stream()
                .max(Comparator.comparing(IvrFlowVersion::getVersion))
                .orElse(null);
    }

    private boolean hasDraftDiff(IvrFlow flow) {
        Integer currentVersion = Objects.requireNonNullElse(flow.getCurrentVersion(), 0);
        if (currentVersion <= 0) {
            return false;
        }
        IvrFlowVersion draft = draftVersion(flow.getId());
        if (draft == null) {
            return false;
        }
        IvrFlowVersion published = versionByNumber(flow.getId(), currentVersion);
        if (published == null) {
            return true;
        }
        return !sameGraph(draft.getGraphJson(), published.getGraphJson());
    }

    private IvrFlowVersion draftVersion(Long flowId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                .eq(IvrFlowVersion::getFlowId, flowId)
                .eq(IvrFlowVersion::getVersion, 0)
                .last("LIMIT 1"));
    }

    private IvrFlowVersion versionByNumber(Long flowId, Integer version) {
        return versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                .eq(IvrFlowVersion::getFlowId, flowId)
                .eq(IvrFlowVersion::getVersion, version)
                .last("LIMIT 1"));
    }

    private boolean sameGraph(String left, String right) {
        String safeLeft = Objects.toString(left, "").trim();
        String safeRight = Objects.toString(right, "").trim();
        if (!StringUtils.hasText(safeLeft) || !StringUtils.hasText(safeRight)) {
            return Objects.equals(safeLeft, safeRight);
        }
        try {
            return Objects.equals(objectMapper.readTree(safeLeft), objectMapper.readTree(safeRight));
        } catch (Exception e) {
            return Objects.equals(safeLeft, safeRight);
        }
    }

    private void saveDraftVersion(Long flowId, String graphJson, Long userId) {
        IvrFlowVersion draft = versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                .eq(IvrFlowVersion::getFlowId, flowId)
                .eq(IvrFlowVersion::getVersion, 0)
                .last("LIMIT 1"));
        if (draft == null) {
            draft = new IvrFlowVersion();
            draft.setFlowId(flowId);
            draft.setVersion(0);
            draft.setGraphJson(graphJson);
            draft.setChangeNote("Draft");
            draft.setPublished(0);
            draft.setCreatedBy(userId);
            draft.setCreatedAt(LocalDateTime.now());
            versionMapper.insert(draft);
            return;
        }
        draft.setGraphJson(graphJson);
        draft.setChangeNote("Draft updated");
        draft.setCreatedBy(userId);
        draft.setCreatedAt(LocalDateTime.now());
        versionMapper.updateById(draft);
    }

    private void applyBody(IvrFlow flow, Map<String, Object> body) {
        flow.setFlowCode(stringValue(body.get("flowCode"), flow.getFlowCode()));
        flow.setFlowName(stringValue(body.get("flowName"), flow.getFlowName()));
        flow.setDescription(stringValue(body.get("description"), flow.getDescription()));
    }

    private String graphJsonValue(Map<String, Object> body) {
        return stringValue(body.get("graphJson"), DEFAULT_GRAPH);
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = Objects.toString(value, "").trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private Map<String, Object> toListMap(IvrFlow flow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", flow.getId());
        map.put("flowCode", flow.getFlowCode());
        map.put("flowName", flow.getFlowName());
        map.put("description", flow.getDescription());
        map.put("status", Objects.requireNonNullElse(flow.getStatus(), 0));
        map.put("currentVersion", Objects.requireNonNullElse(flow.getCurrentVersion(), 0));
        map.put("hasDraftDiff", hasDraftDiff(flow));
        map.put("updatedAt", formatTime(flow.getUpdatedAt()));
        return map;
    }

    private Map<String, Object> toVersionMap(IvrFlowVersion version, Integer currentVersion, boolean draftDiff) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", version.getId());
        map.put("flowId", version.getFlowId());
        map.put("version", version.getVersion());
        map.put("versionLabel", Objects.equals(version.getVersion(), 0) ? "草稿" : "v" + version.getVersion());
        map.put("draft", Objects.equals(version.getVersion(), 0));
        map.put("published", Objects.equals(version.getPublished(), 1));
        map.put("current", Objects.equals(version.getVersion(), currentVersion));
        map.put("diffFromPublished", Objects.equals(version.getVersion(), 0) && draftDiff);
        map.put("changeNote", version.getChangeNote());
        map.put("createdBy", version.getCreatedBy());
        map.put("createdAt", formatTime(version.getCreatedAt()));
        map.put("graphJson", version.getGraphJson());
        try {
            FlowGraph graph = graphParser.parse(version.getGraphJson());
            map.put("nodeCount", graph.getNodes().size());
            map.put("edgeCount", graph.getEdges().size());
        } catch (Exception e) {
            map.put("nodeCount", 0);
            map.put("edgeCount", 0);
        }
        return map;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }
}


