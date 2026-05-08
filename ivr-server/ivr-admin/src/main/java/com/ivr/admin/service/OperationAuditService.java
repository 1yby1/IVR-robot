package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ivr.admin.entity.OperationAuditLog;
import com.ivr.admin.entity.SysUser;
import com.ivr.admin.mapper.OperationAuditLogMapper;
import com.ivr.admin.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OperationAuditService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationAuditLogMapper auditMapper;
    private final SysUserMapper userMapper;

    public OperationAuditService(OperationAuditLogMapper auditMapper, SysUserMapper userMapper) {
        this.auditMapper = auditMapper;
        this.userMapper = userMapper;
    }

    public void record(OperationAuditLog logItem) {
        try {
            fillUser(logItem);
            logItem.setCreatedAt(LocalDateTime.now());
            auditMapper.insert(logItem);
        } catch (Exception e) {
            log.warn("[Audit] persist operation audit failed uri={} err={}", logItem.getRequestUri(), e.toString());
        }
    }

    public Map<String, Object> page(int current,
                                    int size,
                                    String keyword,
                                    String moduleName,
                                    String status) {
        LambdaQueryWrapper<OperationAuditLog> wrapper = new LambdaQueryWrapper<OperationAuditLog>()
                .orderByDesc(OperationAuditLog::getCreatedAt)
                .orderByDesc(OperationAuditLog::getId);
        if (StringUtils.hasText(moduleName)) {
            wrapper.eq(OperationAuditLog::getModuleName, moduleName.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OperationAuditLog::getStatus, status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(OperationAuditLog::getUsername, kw)
                    .or()
                    .like(OperationAuditLog::getNickname, kw)
                    .or()
                    .like(OperationAuditLog::getOperationName, kw)
                    .or()
                    .like(OperationAuditLog::getRequestUri, kw)
                    .or()
                    .like(OperationAuditLog::getRequestBody, kw)
                    .or()
                    .like(OperationAuditLog::getErrorMessage, kw)
                    .or()
                    .like(OperationAuditLog::getIp, kw));
        }

        Page<OperationAuditLog> page = auditMapper.selectPage(Page.of(Math.max(current, 1), Math.max(size, 1)), wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords().stream().map(this::toMap).toList());
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    public List<String> modules() {
        return auditMapper.selectList(new LambdaQueryWrapper<OperationAuditLog>()
                        .select(OperationAuditLog::getModuleName)
                        .isNotNull(OperationAuditLog::getModuleName)
                        .groupBy(OperationAuditLog::getModuleName)
                        .orderByAsc(OperationAuditLog::getModuleName))
                .stream()
                .map(OperationAuditLog::getModuleName)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void fillUser(OperationAuditLog logItem) {
        if (logItem.getUserId() == null) {
            return;
        }
        SysUser user = userMapper.selectById(logItem.getUserId());
        if (user == null) {
            return;
        }
        logItem.setUsername(user.getUsername());
        logItem.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
    }

    private Map<String, Object> toMap(OperationAuditLog item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("userId", item.getUserId());
        map.put("username", item.getUsername());
        map.put("nickname", item.getNickname());
        map.put("moduleName", item.getModuleName());
        map.put("operationType", item.getOperationType());
        map.put("operationName", item.getOperationName());
        map.put("requestMethod", item.getRequestMethod());
        map.put("requestUri", item.getRequestUri());
        map.put("queryParams", item.getQueryParams());
        map.put("requestBody", item.getRequestBody());
        map.put("ip", item.getIp());
        map.put("userAgent", item.getUserAgent());
        map.put("status", item.getStatus());
        map.put("resultCode", item.getResultCode());
        map.put("errorMessage", item.getErrorMessage());
        map.put("latencyMs", item.getLatencyMs());
        map.put("createdAt", item.getCreatedAt() == null ? "" : TIME_FMT.format(item.getCreatedAt()));
        return map;
    }
}
