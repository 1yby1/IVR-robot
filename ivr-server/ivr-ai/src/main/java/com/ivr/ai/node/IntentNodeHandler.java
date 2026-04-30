package com.ivr.ai.node;

import com.ivr.ai.LlmService;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 意图识别节点。把客户语音转写后的文本（或上一个 dtmf/输入）送给 LLM 做 zero-shot 分类，
 * 命中的意图标签作为分支标识返回，executor 据此找对应连线。
 *
 * <p>配置字段：
 * <ul>
 *   <li>{@code inputVar} —— 用于识别的变量名（默认 {@code lastAsr}，回退 {@code lastDtmf} → {@code lastInput}）</li>
 *   <li>{@code intents} —— 候选意图标签数组，与连线的 key/text 对应</li>
 *   <li>{@code fallbackBranch} —— 全部未命中时的分支名，默认 {@code other}</li>
 * </ul>
 *
 * <p>命中结果同时写入 ctx.var {@code lastIntent}，方便下游节点引用。
 */
@Component
public class IntentNodeHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(IntentNodeHandler.class);

    private final LlmService llmService;

    public IntentNodeHandler(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String type() {
        return "intent";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        String inputVarName = stringProp(node, "inputVar", "lastAsr");
        String text = resolveInput(ctx, inputVarName);
        List<String> intents = readIntents(node);
        String fallback = stringProp(node, "fallbackBranch", "other");

        if (!StringUtils.hasText(text)) {
            log.warn("[Intent] empty input var={}", inputVarName);
            ctx.setVar("lastIntent", fallback);
            return NodeResult.branch(fallback);
        }
        if (intents.isEmpty()) {
            log.warn("[Intent] no candidate intents configured, fallback={}", fallback);
            ctx.setVar("lastIntent", fallback);
            return NodeResult.branch(fallback);
        }

        String hit;
        try {
            hit = llmService.detectIntent(text, intents);
        } catch (Exception e) {
            log.warn("[Intent] llm call failed input=\"{}\" err={}", text, e.toString());
            ctx.setVar("lastIntent", fallback);
            return NodeResult.branch(fallback);
        }
        if (!StringUtils.hasText(hit) || "other".equals(hit)) {
            ctx.setVar("lastIntent", fallback);
            return NodeResult.branch(fallback);
        }
        ctx.setVar("lastIntent", hit);
        return NodeResult.branch(hit);
    }

    private String resolveInput(FlowContext ctx, String varName) {
        Object value = ctx.getVar(varName);
        if (value != null && StringUtils.hasText(value.toString())) {
            return value.toString();
        }
        // 回退顺序：显式变量 → lastAsr → lastDtmf → lastInput
        if (StringUtils.hasText(ctx.getLastAsr())) {
            return ctx.getLastAsr();
        }
        if (StringUtils.hasText(ctx.getLastDtmf())) {
            return ctx.getLastDtmf();
        }
        Object lastInput = ctx.getVar("lastInput");
        return lastInput == null ? "" : lastInput.toString();
    }

    private List<String> readIntents(FlowNode node) {
        if (node.properties == null) {
            return List.of();
        }
        Object value = node.properties.get("intents");
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                String s = item.toString().trim();
                if (StringUtils.hasText(s)) {
                    result.add(s);
                }
            }
            return result;
        }
        if (value instanceof String s) {
            // 兼容用逗号分隔的字符串
            List<String> result = new ArrayList<>();
            for (String part : s.split("[,，]")) {
                String t = part.trim();
                if (StringUtils.hasText(t)) {
                    result.add(t);
                }
            }
            return result;
        }
        return List.of();
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
        return StringUtils.hasText(text) ? text : fallback;
    }
}
