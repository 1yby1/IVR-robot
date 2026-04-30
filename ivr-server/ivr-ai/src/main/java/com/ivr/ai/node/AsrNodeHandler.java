package com.ivr.ai.node;

import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 语音识别节点。仅发起一次 ASR 录音指令并将流程置 waiting；真正的识别文本由
 * {@code FreeSwitchCallChannel} 在录音完成后调 {@link com.ivr.ai.TtsAsrService#recognize}
 * 拿到，再通过 {@code FlowExecutor.resumeWithAsr(callUuid, text)} 喂回。
 *
 * <p>当前 channel 实现是 {@code LoggingCallChannel}（仅打日志），所以 ASR 节点处于
 * waiting 后需要由测试或 admin 手工调 {@code resumeWithAsr} 推进；接入 FreeSWITCH
 * 后这一步会自动完成。
 *
 * <p>配置字段：
 * <ul>
 *   <li>{@code maxSeconds} —— 最大录音时长，默认 8 秒</li>
 *   <li>{@code language} —— 识别语种，默认 {@code zh-CN}</li>
 *   <li>{@code prompt} —— 录音前的提示语，FreeSwitchCallChannel 会先 playback 再启动录音</li>
 * </ul>
 */
@Component
public class AsrNodeHandler implements NodeHandler {

    private final CallChannel channel;

    public AsrNodeHandler(CallChannel channel) {
        this.channel = channel;
    }

    @Override
    public String type() {
        return "asr";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        int maxSeconds = intProp(node, "maxSeconds", 8);
        String language = stringProp(node, "language", "zh-CN");
        String prompt = stringProp(node, "prompt", "");
        channel.collectAsr(ctx.getCallUuid(),
                new CallChannel.AsrCollectRequest(maxSeconds, language, prompt));
        return NodeResult.waitForAsr();
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
        if (value == null) {
            return fallback;
        }
        String text = Objects.toString(value, "");
        return text.isEmpty() ? fallback : text;
    }
}
