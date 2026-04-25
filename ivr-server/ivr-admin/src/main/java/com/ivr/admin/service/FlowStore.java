package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ivr.admin.dto.FlowOptionItem;
import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.entity.IvrFlowVersion;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.admin.mapper.IvrFlowVersionMapper;
import com.ivr.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class FlowStore {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_GRAPH = """
            {"nodes":[{"id":"start","type":"circle","x":160,"y":200,"text":"Start","properties":{"bizType":"start","name":"Start"}},{"id":"welcome","type":"rect","x":360,"y":200,"text":"Welcome","properties":{"bizType":"play","name":"Welcome","ttsText":"Welcome to the IVR demo. Press 1 for business service, press 2 to transfer."}},{"id":"collect","type":"rect","x":590,"y":200,"text":"DTMF","properties":{"bizType":"dtmf","name":"DTMF","maxDigits":1,"timeoutSec":8,"mappings":[{"key":"1","nextNode":"business"},{"key":"2","nextNode":"transfer"}]}},{"id":"business","type":"rect","x":820,"y":120,"text":"Business","properties":{"bizType":"play","name":"Business","ttsText":"Business self service is complete."}},{"id":"transfer","type":"rect","x":820,"y":280,"text":"Transfer","properties":{"bizType":"transfer","name":"Transfer","target":"1000"}},{"id":"end","type":"circle","x":1040,"y":200,"text":"End","properties":{"bizType":"end","name":"End"}}],"edges":[{"id":"edge-start-welcome","type":"polyline","sourceNodeId":"start","targetNodeId":"welcome"},{"id":"edge-welcome-collect","type":"polyline","sourceNodeId":"welcome","targetNodeId":"collect"},{"id":"edge-collect-business","type":"polyline","sourceNodeId":"collect","targetNodeId":"business","text":"1","properties":{"key":"1"}},{"id":"edge-collect-transfer","type":"polyline","sourceNodeId":"collect","targetNodeId":"transfer","text":"2","properties":{"key":"2"}},{"id":"edge-business-end","type":"polyline","sourceNodeId":"business","targetNodeId":"end"}]}
            """;

    private final IvrFlowMapper flowMapper;
    private final IvrFlowVersionMapper versionMapper;

    public FlowStore(IvrFlowMapper flowMapper, IvrFlowVersionMapper versionMapper) {
        this.flowMapper = flowMapper;
        this.versionMapper = versionMapper;
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

    private IvrFlow getRequired(Long id) {
        IvrFlow flow = flowMapper.selectById(id);
        if (flow == null || Objects.equals(flow.getDeleted(), 1)) {
            throw new BusinessException(404, "Flow does not exist");
        }
        return flow;
    }

    private IvrFlowVersion editableVersion(IvrFlow flow) {
        IvrFlowVersion draft = versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                .eq(IvrFlowVersion::getFlowId, flow.getId())
                .eq(IvrFlowVersion::getVersion, 0)
                .last("LIMIT 1"));
        if (draft != null) {
            return draft;
        }
        Integer currentVersion = Objects.requireNonNullElse(flow.getCurrentVersion(), 0);
        if (currentVersion > 0) {
            IvrFlowVersion published = versionMapper.selectOne(new LambdaQueryWrapper<IvrFlowVersion>()
                    .eq(IvrFlowVersion::getFlowId, flow.getId())
                    .eq(IvrFlowVersion::getVersion, currentVersion)
                    .last("LIMIT 1"));
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
        map.put("updatedAt", formatTime(flow.getUpdatedAt()));
        return map;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }
}


