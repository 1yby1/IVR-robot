package com.ivr.ai.node;

import com.ivr.ai.LlmService;
import com.ivr.ai.rag.KnowledgeService;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识库问答（RAG）节点。流程：检索 → 拼 prompt → LLM 生成 → 写 ctx.var。
 *
 * <p>检索由 {@link KnowledgeService} 完成；生成由 {@link LlmService} 完成。
 *
 * <p>本节点只把回答塞进上下文变量 {@code ragAnswer}，**不直接播报**——后续接一个
 * {@code play} 节点用 {@code ${ragAnswer}} 模板取出再走 TTS。这样生成-播报解耦。
 *
 * <p>配置字段：
 * <ul>
 *   <li>{@code kbId} —— 知识库 id（可选）</li>
 *   <li>{@code questionVar} —— 取问题的变量名，默认 {@code lastAsr}</li>
 *   <li>{@code topK} —— 检索片段数，默认 3</li>
 *   <li>{@code answerVar} —— 答案写入的变量名，默认 {@code ragAnswer}</li>
 *   <li>{@code fallbackBranch} —— 检索 / 生成失败时走的分支，默认 {@code fallback}</li>
 *   <li>{@code promptTemplate} —— 自定义 prompt，可用占位符 {@code {context}} {@code {question}}</li>
 * </ul>
 *
 * <p>命中且 LLM 成功 → 默认分支；任意环节失败 → fallback 分支（运营可在画布上接转人工）。
 */
@Component
public class RagNodeHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger(RagNodeHandler.class);

    private static final String DEFAULT_TEMPLATE = """
            你是客服助手，必须严格依据下方资料回答客户问题。资料中没有答案时直接回答「抱歉，这个问题需要人工帮您处理，正在为您转接」。
            资料：
            {context}

            客户问题：{question}
            请用 80 字以内、口语化的中文回答，不要重复「资料」二字。
            """;

    private final LlmService llmService;
    private final KnowledgeService knowledgeService;

    public RagNodeHandler(LlmService llmService, KnowledgeService knowledgeService) {
        this.llmService = llmService;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String type() {
        return "rag";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        String questionVar = stringProp(node, "questionVar", "lastAsr");
        String answerVar = stringProp(node, "answerVar", "ragAnswer");
        String fallback = stringProp(node, "fallbackBranch", "fallback");
        int topK = intProp(node, "topK", 3);
        Long kbId = longProp(node, "kbId");

        String question = resolveQuestion(ctx, questionVar);
        if (!StringUtils.hasText(question)) {
            log.warn("[RAG] empty question var={}", questionVar);
            return NodeResult.branch(fallback);
        }

        List<KnowledgeService.KnowledgeChunk> chunks;
        try {
            chunks = knowledgeService.retrieve(kbId, question, topK);
        } catch (Exception e) {
            log.warn("[RAG] retrieve failed kb={} err={}", kbId, e.toString());
            return NodeResult.branch(fallback);
        }
        if (chunks == null || chunks.isEmpty()) {
            log.info("[RAG] no chunks matched question=\"{}\"", question);
            return NodeResult.branch(fallback);
        }

        String context = chunks.stream()
                .map(c -> "- " + c.title() + "：" + c.content())
                .collect(Collectors.joining("\n"));
        String tpl = stringProp(node, "promptTemplate", DEFAULT_TEMPLATE);

        String answer;
        try {
            answer = llmService.chatTemplate(tpl, Map.of(
                    "context", context,
                    "question", question
            ));
        } catch (Exception e) {
            log.warn("[RAG] llm call failed err={}", e.toString());
            return NodeResult.branch(fallback);
        }
        if (!StringUtils.hasText(answer)) {
            return NodeResult.branch(fallback);
        }

        ctx.setVar(answerVar, answer.trim());
        ctx.setVar("ragHits", chunks.size());
        return NodeResult.next();
    }

    private String resolveQuestion(FlowContext ctx, String varName) {
        Object value = ctx.getVar(varName);
        if (value != null && StringUtils.hasText(value.toString())) {
            return value.toString();
        }
        return StringUtils.hasText(ctx.getLastAsr()) ? ctx.getLastAsr() : "";
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

    private Long longProp(FlowNode node, String key) {
        if (node.properties == null) {
            return null;
        }
        Object value = node.properties.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = Objects.toString(value, "");
            return StringUtils.hasText(text) ? Long.parseLong(text.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
