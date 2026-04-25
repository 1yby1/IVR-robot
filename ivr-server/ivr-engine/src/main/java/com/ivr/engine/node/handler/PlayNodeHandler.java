package com.ivr.engine.node.handler;

import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * 播放语音节点。配置字段：
 * <ul>
 *   <li>{@code ttsText} 或嵌套 {@code tts.text} —— TTS 文本</li>
 *   <li>{@code audioUrl} —— 已有音频文件路径（与 ttsText 二选一）</li>
 *   <li>{@code voice} —— 发音人（可选）</li>
 * </ul>
 * <p>支持简单变量替换：{@code ${varName}} 取自 {@link FlowContext#getVars()}。
 */
@Component
public class PlayNodeHandler implements NodeHandler {

    private final CallChannel channel;

    public PlayNodeHandler(CallChannel channel) {
        this.channel = channel;
    }

    @Override
    public String type() {
        return "play";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        String text = resolveText(node);
        String audio = stringProp(node, "audioUrl", "");
        String voice = stringProp(node, "voice", "");
        String rendered = renderVars(text, ctx.getVars());
        channel.playback(ctx.getCallUuid(), new CallChannel.PlaybackRequest(rendered, audio, voice));
        return NodeResult.next();
    }

    private String resolveText(FlowNode node) {
        String text = stringProp(node, "ttsText", "");
        if (StringUtils.hasText(text)) {
            return text;
        }
        Object tts = node.properties == null ? null : node.properties.get("tts");
        if (tts instanceof Map<?, ?> map) {
            Object value = map.get("text");
            if (value != null) {
                return value.toString();
            }
        }
        return stringProp(node, "prompt", "");
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

    private String renderVars(String template, Map<String, Object> vars) {
        if (!StringUtils.hasText(template) || vars == null || vars.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, Objects.toString(entry.getValue(), ""));
            }
        }
        return result;
    }
}
