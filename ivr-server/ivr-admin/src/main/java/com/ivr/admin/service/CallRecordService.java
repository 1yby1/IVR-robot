package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.CallEventItem;
import com.ivr.admin.dto.CallLogListItem;
import com.ivr.admin.dto.CallReplayResponse;
import com.ivr.admin.dto.PageResult;
import com.ivr.admin.entity.CallEvent;
import com.ivr.admin.entity.CallLog;
import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.mapper.CallEventMapper;
import com.ivr.admin.mapper.CallLogMapper;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CallRecordService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CallLogMapper callLogMapper;
    private final CallEventMapper callEventMapper;
    private final IvrFlowMapper flowMapper;
    private final ObjectMapper objectMapper;

    public CallRecordService(CallLogMapper callLogMapper,
                             CallEventMapper callEventMapper,
                             IvrFlowMapper flowMapper,
                             ObjectMapper objectMapper) {
        this.callLogMapper = callLogMapper;
        this.callEventMapper = callEventMapper;
        this.flowMapper = flowMapper;
        this.objectMapper = objectMapper;
    }

    public PageResult<CallLogListItem> page(int current,
                                            int size,
                                            String keyword,
                                            Long flowId,
                                            String endReason,
                                            String dateFrom,
                                            String dateTo) {
        int safeCurrent = Math.max(current, 1);
        int safeSize = Math.max(size, 1);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<CallLog> wrapper = new LambdaQueryWrapper<CallLog>()
                .orderByDesc(CallLog::getStartTime)
                .orderByDesc(CallLog::getId);
        if (flowId != null) {
            wrapper.eq(CallLog::getFlowId, flowId);
        }
        if (StringUtils.hasText(endReason)) {
            wrapper.eq(CallLog::getEndReason, endReason.trim());
        }
        LocalDateTime from = parseDateStart(dateFrom);
        LocalDateTime to = parseDateEnd(dateTo);
        if (from != null) {
            wrapper.ge(CallLog::getStartTime, from);
        }
        if (to != null) {
            wrapper.lt(CallLog::getStartTime, to);
        }
        if (StringUtils.hasText(normalizedKeyword)) {
            wrapper.and(w -> w.like(CallLog::getCallUuid, normalizedKeyword)
                    .or()
                    .like(CallLog::getCaller, normalizedKeyword)
                    .or()
                    .like(CallLog::getCallee, normalizedKeyword)
                    .or()
                    .like(CallLog::getEndReason, normalizedKeyword));
        }

        Page<CallLog> page = callLogMapper.selectPage(Page.of(safeCurrent, safeSize), wrapper);
        Map<Long, IvrFlow> flows = loadFlows(page.getRecords().stream().map(CallLog::getFlowId).toList());
        PageResult<CallLogListItem> result = new PageResult<>();
        result.setRecords(page.getRecords().stream().map(item -> toListItem(item, flows.get(item.getFlowId()))).toList());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    public PageResult<CallLogListItem> page(int current, int size, String keyword) {
        return page(current, size, keyword, null, null, null, null);
    }

    public List<CallEventItem> events(String callUuid) {
        return callEventMapper.selectList(new LambdaQueryWrapper<CallEvent>()
                        .eq(CallEvent::getCallUuid, callUuid)
                        .orderByAsc(CallEvent::getEventTime)
                        .orderByAsc(CallEvent::getId))
                .stream()
                .map(this::toEventItem)
                .toList();
    }

    public CallReplayResponse replay(String callUuid) {
        CallLog log = findByCallUuid(callUuid);
        if (log == null) {
            throw new BusinessException(404, "通话记录不存在");
        }
        List<CallEvent> events = callEventMapper.selectList(new LambdaQueryWrapper<CallEvent>()
                .eq(CallEvent::getCallUuid, callUuid)
                .orderByAsc(CallEvent::getEventTime)
                .orderByAsc(CallEvent::getId));
        IvrFlow flow = log.getFlowId() == null ? null : flowMapper.selectById(log.getFlowId());

        CallReplayResponse response = new CallReplayResponse();
        response.setCallUuid(log.getCallUuid());
        response.setCaller(log.getCaller());
        response.setCallee(log.getCallee());
        response.setFlowId(log.getFlowId());
        response.setFlowCode(flow == null ? "" : flow.getFlowCode());
        response.setFlowName(flow == null ? "流程不存在" : flow.getFlowName());
        response.setFlowVersion(log.getFlowVersion());
        response.setStartTime(formatTime(log.getStartTime()));
        response.setEndTime(formatTime(log.getEndTime()));
        response.setDuration(log.getDuration());
        response.setEndReason(log.getEndReason());
        response.setTransferTo(log.getTransferTo());
        response.setEvents(events.stream().map(this::toReplayEvent).toList());
        response.setPath(buildReplayPath(events));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void startCall(String callUuid, String caller, String callee, Long flowId, Integer flowVersion) {
        CallLog existing = findByCallUuid(callUuid);
        if (existing != null) {
            return;
        }
        CallLog log = new CallLog();
        log.setCallUuid(callUuid);
        log.setCaller(caller);
        log.setCallee(callee);
        log.setFlowId(flowId);
        log.setFlowVersion(flowVersion);
        log.setStartTime(LocalDateTime.now());
        log.setAnswerTime(log.getStartTime());
        log.setDuration(0);
        log.setEndReason("running");
        log.setHangupBy("system");
        log.setAiHit(0);
        callLogMapper.insert(log);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAnswered(String callUuid) {
        CallLog log = findByCallUuid(callUuid);
        if (log == null || log.getAnswerTime() != null) {
            return;
        }
        log.setAnswerTime(LocalDateTime.now());
        callLogMapper.updateById(log);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordEvent(String callUuid, String nodeKey, String nodeType, String eventType, Map<String, Object> payload) {
        CallEvent event = new CallEvent();
        event.setCallUuid(callUuid);
        event.setNodeKey(nodeKey);
        event.setNodeType(nodeType);
        event.setEventType(eventType);
        event.setPayload(toJson(payload));
        event.setEventTime(LocalDateTime.now());
        callEventMapper.insert(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishCall(String callUuid, String endReason, String transferTo) {
        CallLog log = findByCallUuid(callUuid);
        if (log == null) {
            throw new BusinessException(404, "通话记录不存在");
        }
        applyFinish(log, endReason, transferTo);
    }

    /** finishCall 的非异常版本：通话记录不存在时返回 false 而不是抛 404；用于 ESL 异常事件回调。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean tryFinishCall(String callUuid, String endReason, String transferTo) {
        CallLog log = findByCallUuid(callUuid);
        if (log == null) {
            return false;
        }
        applyFinish(log, endReason, transferTo);
        return true;
    }

    private void applyFinish(CallLog log, String endReason, String transferTo) {
        if (log.getEndTime() != null) {
            return;
        }
        LocalDateTime endTime = LocalDateTime.now();
        log.setEndTime(endTime);
        LocalDateTime durationStart = log.getAnswerTime() == null ? log.getStartTime() : log.getAnswerTime();
        log.setDuration((int) Math.max(0, Duration.between(durationStart, endTime).toSeconds()));
        log.setEndReason(endReason);
        log.setTransferTo(transferTo);
        log.setHangupBy("system");
        callLogMapper.updateById(log);
    }

    private CallLog findByCallUuid(String callUuid) {
        return callLogMapper.selectOne(new LambdaQueryWrapper<CallLog>()
                .eq(CallLog::getCallUuid, callUuid)
                .last("LIMIT 1"));
    }

    private Map<Long, IvrFlow> loadFlows(List<Long> flowIds) {
        List<Long> ids = flowIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return flowMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(IvrFlow::getId, Function.identity()));
    }

    private CallLogListItem toListItem(CallLog log, IvrFlow flow) {
        CallLogListItem item = new CallLogListItem();
        item.setId(log.getId());
        item.setCallUuid(log.getCallUuid());
        item.setCaller(log.getCaller());
        item.setCallee(log.getCallee());
        item.setFlowId(log.getFlowId());
        item.setFlowCode(flow == null ? "" : flow.getFlowCode());
        item.setFlowName(flow == null ? "流程不存在" : flow.getFlowName());
        item.setFlowVersion(log.getFlowVersion());
        item.setStartTime(formatTime(log.getStartTime()));
        item.setAnswerTime(formatTime(log.getAnswerTime()));
        item.setEndTime(formatTime(log.getEndTime()));
        item.setDuration(log.getDuration());
        item.setEndReason(log.getEndReason());
        item.setTransferTo(log.getTransferTo());
        item.setHangupBy(log.getHangupBy());
        return item;
    }

    private CallEventItem toEventItem(CallEvent event) {
        CallEventItem item = new CallEventItem();
        item.setId(event.getId());
        item.setCallUuid(event.getCallUuid());
        item.setNodeKey(event.getNodeKey());
        item.setNodeType(event.getNodeType());
        item.setEventType(event.getEventType());
        item.setPayload(event.getPayload());
        item.setEventTime(formatTime(event.getEventTime()));
        return item;
    }

    private CallReplayResponse.Event toReplayEvent(CallEvent event) {
        Map<String, Object> payload = payloadMap(event.getPayload());
        CallReplayResponse.Event item = new CallReplayResponse.Event();
        item.setId(event.getId());
        item.setNodeKey(event.getNodeKey());
        item.setNodeType(event.getNodeType());
        item.setEventType(event.getEventType());
        item.setEventTime(formatTime(event.getEventTime()));
        item.setPayload(event.getPayload());
        item.setPayloadPretty(prettyPayload(event.getPayload()));
        item.setLevel(eventLevel(event, payload));
        item.setSummary(eventSummary(event, payload));
        return item;
    }

    private List<CallReplayResponse.PathStep> buildReplayPath(List<CallEvent> events) {
        List<CallReplayResponse.PathStep> path = new ArrayList<>();
        Map<String, String> nodeLevels = new LinkedHashMap<>();
        Map<String, String> nodeSummaries = new LinkedHashMap<>();
        for (CallEvent event : events) {
            if (!StringUtils.hasText(event.getNodeKey())) {
                continue;
            }
            Map<String, Object> payload = payloadMap(event.getPayload());
            String level = eventLevel(event, payload);
            String current = nodeLevels.get(event.getNodeKey());
            if (current == null || severity(level) > severity(current)) {
                nodeLevels.put(event.getNodeKey(), level);
                nodeSummaries.put(event.getNodeKey(), eventSummary(event, payload));
            }
        }
        int stepNo = 1;
        for (CallEvent event : events) {
            if (!"enter".equals(event.getEventType()) || !StringUtils.hasText(event.getNodeKey())) {
                continue;
            }
            Map<String, Object> payload = payloadMap(event.getPayload());
            CallReplayResponse.PathStep step = new CallReplayResponse.PathStep();
            step.setStepNo(stepNo++);
            step.setNodeKey(event.getNodeKey());
            step.setNodeType(event.getNodeType());
            step.setNodeName(Objects.toString(payload.get("name"), event.getNodeKey()));
            step.setEventTime(formatTime(event.getEventTime()));
            step.setLevel(Objects.requireNonNullElse(nodeLevels.get(event.getNodeKey()), "info"));
            step.setSummary(Objects.requireNonNullElse(nodeSummaries.get(event.getNodeKey()), "进入节点"));
            path.add(step);
        }
        return path;
    }

    private String eventLevel(CallEvent event, Map<String, Object> payload) {
        String status = lower(payload.get("status"));
        String reason = lower(payload.get("reason"));
        if ("error".equals(event.getEventType())
                || Set.of("failed", "llm_failed", "retrieve_failed", "non_2xx", "error").contains(status)
                || "error".equals(reason)) {
            return "danger";
        }
        if (Set.of("no_hits", "skipped").contains(status)
                || "timeout".equals(reason)
                || "transfer".equals(reason)
                || "other".equals(lower(payload.get("hit")))
                || "fallback".equals(lower(payload.get("hit")))) {
            return "warning";
        }
        if ("ok".equals(status) || ("intent".equals(event.getEventType()) && StringUtils.hasText(Objects.toString(payload.get("hit"), "")))) {
            return "success";
        }
        return "info";
    }

    private String eventSummary(CallEvent event, Map<String, Object> payload) {
        for (String key : List.of("error", "message", "result", "text", "input", "hit", "status", "waitFor", "name")) {
            Object value = payload.get(key);
            if (value != null && StringUtils.hasText(Objects.toString(value, ""))) {
                String text = Objects.toString(value, "");
                return text.length() > 120 ? text.substring(0, 117) + "..." : text;
            }
        }
        return switch (Objects.toString(event.getEventType(), "")) {
            case "enter" -> "进入节点";
            case "exit" -> "离开节点";
            case "terminate" -> "流程结束";
            default -> Objects.toString(event.getEventType(), "");
        };
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

    private String prettyPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return "";
        }
        try {
            Object value = objectMapper.readValue(payload, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return payload;
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }

    private LocalDateTime parseDateStart(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "开始日期格式不正确");
        }
    }

    private LocalDateTime parseDateEnd(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).plusDays(1).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "结束日期格式不正确");
        }
    }

    private String lower(Object value) {
        return Objects.toString(value, "").trim().toLowerCase();
    }

    private int severity(String level) {
        return switch (Objects.toString(level, "")) {
            case "danger" -> 3;
            case "warning" -> 2;
            case "success" -> 1;
            default -> 0;
        };
    }
}
