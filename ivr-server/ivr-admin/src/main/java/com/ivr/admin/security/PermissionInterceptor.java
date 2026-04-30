package com.ivr.admin.security;

import com.ivr.admin.service.PermissionService;
import com.ivr.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionService permissionService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<Rule> rules = new ArrayList<>();

    public PermissionInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
        registerRules();
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String requiredPerm = findRequiredPerm(request.getMethod(), request.getServletPath());
        if (requiredPerm == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof Long userId)) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        if (!permissionService.hasPerm(userId, requiredPerm)) {
            throw new BusinessException(403, "没有操作权限");
        }
        return true;
    }

    private String findRequiredPerm(String method, String path) {
        return rules.stream()
                .filter(rule -> rule.matches(method, path))
                .map(Rule::perm)
                .findFirst()
                .orElse(null);
    }

    private void registerRules() {
        rules.add(new Rule(HttpMethod.GET.name(), "/dashboard/overview", "robot:home:view"));
        rules.add(new Rule(null, "/robot/hotline/**", "robot:hotline:list"));
        rules.add(new Rule(null, "/robot/call/**", "robot:call:list"));

        rules.add(new Rule(HttpMethod.GET.name(), "/flow/page", "flow:list"));
        rules.add(new Rule(HttpMethod.GET.name(), "/flow/*", "flow:list"));
        rules.add(new Rule(HttpMethod.POST.name(), "/flow", "flow:add"));
        rules.add(new Rule(HttpMethod.POST.name(), "/flow/*/debug/start", "flow:list"));
        rules.add(new Rule(HttpMethod.POST.name(), "/flow/debug/*/input", "flow:list"));
        rules.add(new Rule(HttpMethod.PUT.name(), "/flow/*", "flow:edit"));
        rules.add(new Rule(HttpMethod.POST.name(), "/flow/*/publish", "flow:publish"));
        rules.add(new Rule(HttpMethod.POST.name(), "/flow/*/offline", "flow:publish"));
        rules.add(new Rule(HttpMethod.DELETE.name(), "/flow/*", "flow:delete"));

        rules.add(new Rule(HttpMethod.GET.name(), "/knowledge/bases/**", "kb:base:list"));
        rules.add(new Rule(HttpMethod.POST.name(), "/knowledge/bases", "kb:base:add"));
        rules.add(new Rule(HttpMethod.PUT.name(), "/knowledge/bases/*", "kb:base:edit"));
        rules.add(new Rule(HttpMethod.DELETE.name(), "/knowledge/bases/*", "kb:base:delete"));

        rules.add(new Rule(HttpMethod.GET.name(), "/knowledge/docs/**", "kb:doc:list"));
        rules.add(new Rule(HttpMethod.POST.name(), "/knowledge/docs/*/reindex", "kb:doc:reindex"));
        rules.add(new Rule(HttpMethod.POST.name(), "/knowledge/docs", "kb:doc:add"));
        rules.add(new Rule(HttpMethod.PUT.name(), "/knowledge/docs/*", "kb:doc:edit"));
        rules.add(new Rule(HttpMethod.DELETE.name(), "/knowledge/docs/*", "kb:doc:delete"));

        rules.add(new Rule(null, "/system/user/**", "system:user:list"));
        rules.add(new Rule(null, "/system/role/**", "system:role:list"));
        rules.add(new Rule(null, "/system/menu/**", "system:menu:list"));
    }

    private record Rule(String method, String pattern, String perm) {

        boolean matches(String requestMethod, String path) {
            boolean methodMatches = method == null || method.equals(requestMethod);
            return methodMatches && new AntPathMatcher().match(pattern, path);
        }
    }
}
