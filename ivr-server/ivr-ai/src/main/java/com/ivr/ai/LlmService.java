package com.ivr.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 大模型服务门面。封装 Spring AI，业务模块不直接依赖 Spring AI API，
 * 便于后续切换 provider（通义 / DeepSeek / 智谱 / 本地 Ollama）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final ChatClient.Builder chatClientBuilder;

    /** 简单一问一答 */
    public String chat(String userText) {
        return chatClientBuilder.build()
                .prompt()
                .user(userText)
                .call()
                .content();
    }

    /** 基于模板的调用 */
    public String chatTemplate(String tpl, Map<String, Object> vars) {
        PromptTemplate promptTemplate = new PromptTemplate(tpl);
        Prompt prompt = promptTemplate.create(vars);
        return chatClientBuilder.build().prompt(prompt).call().content();
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
        String result = chatTemplate(tpl, Map.of(
                "userText", userText,
                "intents", String.join(" / ", intents)
        ));
        String trimmed = result == null ? "other" : result.trim();
        return intents.contains(trimmed) ? trimmed : "other";
    }
}
