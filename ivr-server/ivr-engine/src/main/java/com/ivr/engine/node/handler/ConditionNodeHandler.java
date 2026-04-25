package com.ivr.engine.node.handler;

import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 条件分支节点。
 *
 * <p>{@code expression} 字段是 SpEL 表达式，求值结果转字符串作为 branch 标签：
 * <ul>
 *   <li>{@code lastDtmf == '1'} → 输出 "true" / "false"</li>
 *   <li>{@code #vars['caller'].startsWith('138')} → 同上</li>
 *   <li>{@code lastDtmf} → 直接输出按键值，作为分支</li>
 * </ul>
 *
 * <p>SpEL 上下文里 {@code lastDtmf}、{@code lastAsr}、{@code caller}、{@code callee}、
 * {@code vars} 都可直接引用。
 */
@Component
public class ConditionNodeHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(ConditionNodeHandler.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    @Override
    public String type() {
        return "condition";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        String expression = stringProp(node, "expression", "");
        if (!StringUtils.hasText(expression)) {
            return NodeResult.next();
        }
        try {
            StandardEvaluationContext ec = new StandardEvaluationContext();
            ec.setVariable("vars", ctx.getVars());
            ec.setVariable("caller", ctx.getCaller());
            ec.setVariable("callee", ctx.getCallee());
            ec.setVariable("lastDtmf", ctx.getLastDtmf());
            ec.setVariable("lastAsr", ctx.getLastAsr());
            ec.setRootObject(ctx);
            Expression expr = PARSER.parseExpression(expression);
            Object value = expr.getValue(ec);
            String branch = value == null ? "default" : value.toString();
            return NodeResult.branch(branch);
        } catch (Exception e) {
            log.warn("[Condition] eval failed expr={} err={}", expression, e.toString());
            return NodeResult.error("condition expression error: " + e.getMessage());
        }
    }

    private String stringProp(FlowNode node, String key, String fallback) {
        if (node.properties == null) {
            return fallback;
        }
        Object value = node.properties.get(key);
        if (value == null) {
            return fallback;
        }
        String text = Objects.toString(value, "");
        return text.isEmpty() ? fallback : text;
    }
}
