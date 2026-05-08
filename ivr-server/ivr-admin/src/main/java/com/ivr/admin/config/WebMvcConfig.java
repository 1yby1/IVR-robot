package com.ivr.admin.config;

import com.ivr.admin.security.PermissionInterceptor;
import com.ivr.admin.security.OperationAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;
    private final OperationAuditInterceptor operationAuditInterceptor;

    public WebMvcConfig(PermissionInterceptor permissionInterceptor,
                        OperationAuditInterceptor operationAuditInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
        this.operationAuditInterceptor = operationAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operationAuditInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/register", "/health");
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/register", "/health");
    }
}
