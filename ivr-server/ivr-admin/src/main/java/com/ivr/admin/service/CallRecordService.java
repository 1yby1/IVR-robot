package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.CallEventItem;
import com.ivr.admin.dto.CallLogListItem;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public PageResult<CallLogListItem> page(int current, int size, String keyword) {
        int safeCurrent = Math.max(current, 1);
        int safeSize = Math.max(size, 1);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<CallLog> wrapper = new LambdaQueryWrapper<CallLog>()
                .orderByDesc(CallLog::getStartTime)
                .orderByDesc(CallLog::getId);
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

    public List<CallEventItem> events(String callUuid) {
        return callEventMapper.selectList(new LambdaQueryWrapper<CallEvent>()
                        .eq(CallEvent::getCallUuid, callUuid)
                        .orderByAsc(CallEvent::getEventTime)
                        .orderByAsc(CallEvent::getId))
                .stream()
                .map(this::toEventItem)
                .toList();
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
}
