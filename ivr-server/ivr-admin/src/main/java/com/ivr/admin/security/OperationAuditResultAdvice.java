package com.ivr.admin.security;

import com.ivr.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class OperationAuditResultAdvice implements ResponseBodyAdvice<Object> {

    public static final String RESULT_CODE_ATTR = "operationAudit.resultCode";
    public static final String RESULT_MSG_ATTR = "operationAudit.resultMsg";
    public static final String RESULT_SUCCESS_ATTR = "operationAudit.resultSuccess";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof R<?> result && request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            httpRequest.setAttribute(RESULT_CODE_ATTR, result.getCode());
            httpRequest.setAttribute(RESULT_MSG_ATTR, result.getMsg());
            httpRequest.setAttribute(RESULT_SUCCESS_ATTR, result.isSuccess());
        }
        return body;
    }
}
