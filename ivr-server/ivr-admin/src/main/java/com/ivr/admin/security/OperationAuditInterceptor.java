package com.ivr.admin.security;

import com.ivr.admin.entity.OperationAuditLog;
import com.ivr.admin.service.OperationAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

@Component
public class OperationAuditInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "operationAudit.startTime";
    private static final int TEXT_MAX_LEN = 2000;
    private static final int USER_AGENT_MAX_LEN = 500;
    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.PATCH.name()
    );

    private final OperationAuditService auditService;

    public OperationAuditInterceptor(OperationAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (shouldAudit(request)) {
            request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Object start = request.getAttribute(START_TIME_ATTR);
        if (!(start instanceof Long startTime)) {
            return;
        }

        OperationAuditLog logItem = new OperationAuditLog();
        logItem.setUserId(currentUserId());
        logItem.setModuleName(moduleName(request.getServletPath()));
        logItem.setOperationType(operationType(request));
        logItem.setOperationName(operationName(logItem.getModuleName(), logItem.getOperationType()));
        logItem.setRequestMethod(request.getMethod());
        logItem.setRequestUri(request.getServletPath());
        logItem.setQueryParams(truncate(request.getQueryString(), TEXT_MAX_LEN));
        logItem.setRequestBody(requestBody(request));
        logItem.setIp(clientIp(request));
        logItem.setUserAgent(truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LEN));
        logItem.setLatencyMs(System.currentTimeMillis() - startTime);

        Boolean resultSuccess = (Boolean) request.getAttribute(OperationAuditResultAdvice.RESULT_SUCCESS_ATTR);
        Integer resultCode = resultCode(request, response);
        boolean success = resultSuccess != null ? resultSuccess : ex == null && response.getStatus() < 400;
        logItem.setStatus(success ? "success" : "failed");
        logItem.setResultCode(resultCode);
        if (!success) {
            logItem.setErrorMessage(errorMessage(request, ex));
        }

        auditService.record(logItem);
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return false;
        }
        return !path.startsWith("/auth/login")
                && !path.startsWith("/auth/register")
                && !path.startsWith("/health")
                && !path.startsWith("/system/audit");
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Long userId ? userId : null;
    }

    private Integer resultCode(HttpServletRequest request, HttpServletResponse response) {
        Object code = request.getAttribute(OperationAuditResultAdvice.RESULT_CODE_ATTR);
        if (code instanceof Number number) {
            return number.intValue();
        }
        return response.getStatus();
    }

    private String errorMessage(HttpServletRequest request, Exception ex) {
        Object msg = request.getAttribute(OperationAuditResultAdvice.RESULT_MSG_ATTR);
        if (msg != null && StringUtils.hasText(msg.toString())) {
            return truncate(msg.toString(), TEXT_MAX_LEN);
        }
        return ex == null ? "" : truncate(ex.getMessage(), TEXT_MAX_LEN);
    }

    private String requestBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null || wrapper.getContentAsByteArray().length == 0) {
            return "";
        }
        String body = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        return truncate(maskSensitive(body), TEXT_MAX_LEN);
    }

    private String maskSensitive(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text
                .replaceAll("(?i)(\"(?:password|confirmPassword|token|authorization)\"\\s*:\\s*\")([^\"]*)(\")", "$1***$3")
                .replaceAll("(?i)((?:password|confirmPassword|token|authorization)=)([^&\\s]+)", "$1***");
    }

    private String moduleName(String path) {
        if (path.startsWith("/flow")) return "流程编排";
        if (path.startsWith("/robot/hotline")) return "热线管理";
        if (path.startsWith("/robot/call")) return "通话记录";
        if (path.startsWith("/knowledge/eval")) return "RAG评估";
        if (path.startsWith("/knowledge")) return "知识库";
        if (path.startsWith("/ai/llm")) return "LLM日志";
        if (path.startsWith("/system/user")) return "用户管理";
        if (path.startsWith("/system/role")) return "角色管理";
        if (path.startsWith("/system/menu")) return "菜单管理";
        if (path.startsWith("/auth")) return "认证";
        return "系统";
    }

    private String operationType(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (path.contains("/publish")) return "publish";
        if (path.contains("/offline")) return "offline";
        if (path.contains("/restore-draft")) return "restore";
        if (path.contains("/reindex")) return "reindex";
        if (path.contains("/ai/generate")) return "ai_generate";
        if (path.contains("/password")) return "reset_password";
        if (path.contains("/roles") || path.contains("/menus")) return "assign";
        if (path.contains("/enabled") || path.contains("/status")) return "status";
        if (HttpMethod.DELETE.name().equals(method)) return "delete";
        if (HttpMethod.PUT.name().equals(method) || HttpMethod.PATCH.name().equals(method)) return "update";
        if (HttpMethod.POST.name().equals(method)) return "create";
        return method.toLowerCase();
    }

    private String operationName(String moduleName, String operationType) {
        return moduleName + " - " + switch (operationType) {
            case "publish" -> "发布";
            case "offline" -> "下线";
            case "restore" -> "恢复草稿";
            case "reindex" -> "重建索引";
            case "ai_generate" -> "AI生成";
            case "reset_password" -> "重置密码";
            case "assign" -> "分配权限";
            case "status" -> "状态变更";
            case "delete" -> "删除";
            case "update" -> "修改";
            case "create" -> "新增/提交";
            default -> operationType;
        };
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }

    private String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = Objects.toString(text, "").trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen);
    }
}
