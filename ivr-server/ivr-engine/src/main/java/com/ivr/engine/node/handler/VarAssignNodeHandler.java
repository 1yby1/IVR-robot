package com.ivr.engine.node.handler;

import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VarAssignNodeHandler implements NodeHandler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    @Override
    public String type() {
        return "var_assign";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        String varName = stringProp(node, "varName", "").trim();
        if (!StringUtils.hasText(varName)) {
            return NodeResult.error("var_assign varName is empty");
        }
        String value = renderVars(stringProp(node, "value", ""), ctx);
        ctx.setVar(varName, value);
        return NodeResult.next();
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
