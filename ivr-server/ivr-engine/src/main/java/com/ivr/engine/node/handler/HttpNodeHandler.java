package com.ivr.engine.node.handler;

import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HttpNodeHandler implements NodeHandler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String type() {
        return "http";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        String fallback = stringProp(node, "fallbackBranch", "fallback");
        String url = renderVars(stringProp(node, "url", ""), ctx);
        if (!StringUtils.hasText(url)) {
            return NodeResult.branch(fallback);
        }

        String method = stringProp(node, "method", "GET").trim().toUpperCase();
        String body = renderVars(stringProp(node, "bodyTemplate", ""), ctx);
        String responseVar = stringProp(node, "responseVar", "httpResponse");
        String statusVar = stringProp(node, "statusVar", "httpStatus");
        int timeoutSec = Math.max(1, intProp(node, "timeoutSec", 5));

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec));
            if (StringUtils.hasText(body) && !hasBody(method)) {
                method = "POST";
            }
            if (hasBody(method)) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ctx.setVar(responseVar, response.body());
            ctx.setVar(statusVar, response.statusCode());
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? NodeResult.next()
                    : NodeResult.branch(fallback);
        } catch (Exception e) {
            ctx.setVar(statusVar, "error");
            ctx.setVar(responseVar, e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), ""));
            return NodeResult.branch(fallback);
        }
    }

    private boolean hasBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private int intProp(FlowNode node, String key, int fallback) {
        if (node.properties == null) {
            return fallback;
        }
        Object value = node.properties.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String stringProp(FlowNode node, String key, String fallback) {
        if (node.properties == null) {
            return fallback;
        }
        Object value = node.properties.get(key);
        return value == null ? fallback : Objects.toString(value, fallback);
    }

    private String renderVars(String template, FlowContext ctx) {
        if (!StringUtils.hasText(template)) {
            return template;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        if (ctx.getVars() != null) {
            vars.putAll(ctx.getVars());
        }
        vars.putIfAbsent("caller", ctx.getCaller());
        vars.putIfAbsent("callee", ctx.getCallee());
        vars.putIfAbsent("callUuid", ctx.getCallUuid());
        vars.putIfAbsent("lastDtmf", ctx.getLastDtmf());
        vars.putIfAbsent("lastAsr", ctx.getLastAsr());

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(Objects.toString(vars.get(key), "")));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
