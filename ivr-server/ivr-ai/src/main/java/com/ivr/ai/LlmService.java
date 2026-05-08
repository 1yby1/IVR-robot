package com.ivr.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 大模型服务门面。封装 Spring AI，业务模块不直接依赖 Spring AI API，
 * 便于后续切换 provider（通义 / DeepSeek / 智谱 / 本地 Ollama）。
 */
@Slf4j
@Service
public class LlmService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectProvider<LlmCallLogSink> logSinkProvider;

    public LlmService(ChatClient.Builder chatClientBuilder,
                      ObjectProvider<LlmCallLogSink> logSinkProvider) {
        this.chatClientBuilder = chatClientBuilder;
        this.logSinkProvider = logSinkProvider;
    }

    /** 简单一问一答 */
    public String chat(String userText) {
        return invoke(new Prompt(userText), "chat");
    }

    /** 基于模板的调用 */
    public String chatTemplate(String tpl, Map<String, Object> vars) {
        PromptTemplate promptTemplate = new PromptTemplate(tpl);
        Prompt prompt = promptTemplate.create(vars);
        return invoke(prompt, "chat_template");
    }

    /**
     * 意图识别：zero-shot 分类。
     *
     * @param userText 用户语音转文字
     * @param intents  候选意图标签
     * @return 命中的意图；未命中返回 "other"
     */
    public String detectIntent(String userText, List<String> intents) {
        String tpl = """
            你是客服意图识别助手。用户说：「{userText}」
            请从以下意图中选择一个最匹配的标签：{intents}
            仅输出一个标签文本，不要任何多余字符；都不匹配则输出 other。
            """;
        PromptTemplate promptTemplate = new PromptTemplate(tpl);
        Prompt prompt = promptTemplate.create(Map.of(
                "userText", userText,
                "intents", String.join(" / ", intents)
        ));
        String result = invoke(prompt, "intent_detect");
        String trimmed = result == null ? "other" : result.trim();
        return intents.contains(trimmed) ? trimmed : "other";
    }

    private String invoke(Prompt prompt, String scene) {
        long startNs = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        String promptText = prompt.getContents();
        ChatResponse response = null;
        String content = "";
        Throwable error = null;
        try {
            response = chatClientBuilder.build().prompt(prompt).call().chatResponse();
            content = content(response);
            return content;
        } catch (RuntimeException e) {
            error = e;
            throw e;
        } finally {
            long latencyMs = Math.max(0, (System.nanoTime() - startNs) / 1_000_000);
            record(traceId, scene, promptText, content, response, latencyMs, error);
        }
    }

    private String content(ChatResponse response) {
        if (response == null) {
            return "";
        }
        Generation result = response.getResult();
        if (result == null || result.getOutput() == null) {
            return "";
        }
        return Objects.toString(result.getOutput().getText(), "");
    }

    private void record(String traceId,
                        String scene,
                        String promptText,
                        String content,
                        ChatResponse response,
                        long latencyMs,
                        Throwable error) {
        LlmCallLogSink sink = logSinkProvider.getIfAvailable();
        if (sink == null) {
            return;
        }
        try {
            ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
            Usage usage = metadata == null ? null : metadata.getUsage();
            Integer promptTokens = usage == null ? null : usage.getPromptTokens();
            Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
            Integer totalTokens = usage == null ? null : usage.getTotalTokens();
            boolean estimated = promptTokens == null || completionTokens == null || totalTokens == null;
            if (promptTokens == null) {
                promptTokens = estimateTokens(promptText);
            }
            if (completionTokens == null) {
                completionTokens = estimateTokens(content);
            }
            if (totalTokens == null) {
                totalTokens = promptTokens + completionTokens;
            }

            LlmCallLogRecord record = new LlmCallLogRecord();
            record.setTraceId(traceId);
            record.setScene(scene);
            record.setProvider("spring-ai");
            record.setModel(metadata == null ? "" : Objects.toString(metadata.getModel(), ""));
            record.setStatus(error == null ? "success" : "failed");
            record.setPromptTokens(promptTokens);
            record.setCompletionTokens(completionTokens);
            record.setTotalTokens(totalTokens);
            record.setTokenEstimated(estimated ? 1 : 0);
            record.setPromptChars(length(promptText));
            record.setResponseChars(length(content));
            record.setLatencyMs(latencyMs);
            record.setErrorMessage(error == null ? "" : safeText(error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "")));
            record.setPromptPreview(preview(promptText));
            record.setResponsePreview(preview(content));
            sink.record(record);
        } catch (Exception e) {
            log.warn("[LLM] call log record failed traceId={} err={}", traceId, e.toString());
        }
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                cjk++;
            } else if (!Character.isWhitespace(ch)) {
                other++;
            }
        }
        return cjk + (int) Math.ceil(other / 4.0);
    }

    private int length(String text) {
        return text == null ? 0 : text.length();
    }

    private String preview(String text) {
        String safe = safeText(text);
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }

    private String safeText(String text) {
        if (text == null) {
            return "";
        }
        String safe = text.replaceAll("(?i)(sk-[A-Za-z0-9_-]{6})[A-Za-z0-9_-]+", "$1***");
        safe = safe.replaceAll("(?i)(api[-_ ]?key\\s*[:=]\\s*)[^\\s,;}]+", "$1***");
        return safe;
    }
}
