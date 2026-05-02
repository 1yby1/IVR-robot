package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.entity.CallEvent;
import com.ivr.admin.entity.CallLog;
import com.ivr.admin.entity.IvrFlow;
import com.ivr.admin.entity.SysUser;
import com.ivr.admin.mapper.CallEventMapper;
import com.ivr.admin.mapper.CallLogMapper;
import com.ivr.admin.mapper.IvrFlowMapper;
import com.ivr.admin.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final DateTimeFormatter RECENT_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int RECENT_LIMIT = 6;
    private static final int AI_SAMPLE_LIMIT = 1000;

    private final IvrFlowMapper flowMapper;
    private final CallLogMapper callLogMapper;
    private final CallEventMapper callEventMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public DashboardService(IvrFlowMapper flowMapper,
                            CallLogMapper callLogMapper,
                            CallEventMapper callEventMapper,
                            SysUserMapper userMapper,
                            ObjectMapper objectMapper) {
        this.flowMapper = flowMapper;
        this.callLogMapper = callLogMapper;
        this.callEventMapper = callEventMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        List<CallLog> todayLogs = callLogMapper.selectList(new LambdaQueryWrapper<CallLog>()
                .ge(CallLog::getStartTime, todayStart)
                .lt(CallLog::getStartTime, tomorrowStart)
                .orderByDesc(CallLog::getStartTime)
                .orderByDesc(CallLog::getId)
                .last("LIMIT " + AI_SAMPLE_LIMIT));
        long todayCalls = todayLogs.size();
        long endedCalls = todayLogs.stream().filter(log -> log.getEndTime() != null).count();
        long transferCalls = todayLogs.stream().filter(log -> "transfer".equals(log.getEndReason())).count();
        long aiResolvedCalls = aiResolvedCalls(todayLogs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayCalls", todayCalls);
        result.put("onlineFlows", countFlowsByStatus(1));
        result.put("draftFlows", countFlowsByStatus(0));
        result.put("activeUsers", activeUsers());
        result.put("aiResolutionRate", percent(aiResolvedCalls, endedCalls));
        result.put("transferRate", percent(transferCalls, endedCalls));
        result.put("recentCalls", recentCalls());
        return result;
    }

    private long countFlowsByStatus(int status) {
        Long count = flowMapper.selectCount(new LambdaQueryWrapper<IvrFlow>()
                .eq(IvrFlow::getDeleted, 0)
                .eq(IvrFlow::getStatus, status));
        return Objects.requireNonNullElse(count, 0L);
    }

    private long activeUsers() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .eq(SysUser::getStatus, 1));
        return Objects.requireNonNullElse(count, 0L);
    }

    private long aiResolvedCalls(List<CallLog> todayLogs) {
        List<String> callUuids = todayLogs.stream()
                .filter(log -> log.getEndTime() != null)
                .filter(log -> !"transfer".equals(log.getEndReason()))
                .map(CallLog::getCallUuid)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (callUuids.isEmpty()) {
            return 0;
        }
        return callEventMapper.selectList(new LambdaQueryWrapper<CallEvent>()
                        .in(CallEvent::getCallUuid, callUuids)
                        .in(CallEvent::getEventType, List.of("rag", "intent")))
                .stream()
                .filter(this::isAiHitEvent)
                .map(CallEvent::getCallUuid)
                .distinct()
                .count();
    }

    private boolean isAiHitEvent(CallEvent event) {
        Map<String, Object> payload = payloadMap(event.getPayload());
        if ("rag".equals(event.getEventType())) {
            return "ok".equals(Objects.toString(payload.get("status"), ""));
        }
        if ("intent".equals(event.getEventType())) {
            String hit = Objects.toString(payload.get("hit"), "").trim();
            return StringUtils.hasText(hit) && !Set.of("other", "fallback").contains(hit);
        }
        return false;
    }

    private List<Map<String, Object>> recentCalls() {
        List<CallLog> logs = callLogMapper.selectList(new LambdaQueryWrapper<CallLog>()
                .orderByDesc(CallLog::getStartTime)
                .orderByDesc(CallLog::getId)
                .last("LIMIT " + RECENT_LIMIT));
        Map<Long, IvrFlow> flows = loadFlows(logs.stream().map(CallLog::getFlowId).toList());
        return logs.stream().map(log -> {
            IvrFlow flow = flows.get(log.getFlowId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", log.getStartTime() == null ? "" : RECENT_TIME_FMT.format(log.getStartTime()));
            row.put("caller", maskPhone(log.getCaller()));
            row.put("flowName", flow == null ? "流程不存在" : flow.getFlowName());
            row.put("result", reasonText(log.getEndReason()));
            return row;
        }).toList();
    }

    private Map<Long, IvrFlow> loadFlows(List<Long> flowIds) {
        List<Long> ids = flowIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return flowMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(IvrFlow::getId, Function.identity()));
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

    private int percent(long value, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(value * 100.0 / total);
    }

    private String reasonText(String reason) {
        return switch (Objects.toString(reason, "")) {
            case "normal" -> "正常结束";
            case "transfer" -> "转人工";
            case "timeout" -> "超时";
            case "error" -> "异常";
            case "running" -> "进行中";
            default -> StringUtils.hasText(reason) ? reason : "未知";
        };
    }

    private String maskPhone(String phone) {
        String text = Objects.toString(phone, "");
        if (text.length() < 7) {
            return text;
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }
}
