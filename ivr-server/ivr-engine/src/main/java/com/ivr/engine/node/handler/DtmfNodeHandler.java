package com.ivr.engine.node.handler;

import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 按键收集节点。
 *
 * <p>handler 仅启动一次 collectDtmf 并把流程置 waiting；真正的按键事件由
 * GatewayCallService → FlowExecutor.resumeWithDtmf 异步喂回，再由 executor 决定下一节点。
 *
 * <p>配置字段：{@code maxDigits}（默认 1）、{@code timeoutSec}（默认 5）、{@code terminator}（默认 #）。
 */
@Component
public class DtmfNodeHandler implements NodeHandler {

    private final CallChannel channel;

    public DtmfNodeHandler(CallChannel channel) {
        this.channel = channel;
    }

    @Override
    public String type() {
        return "dtmf";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        int maxDigits = intProp(node, "maxDigits", 1);
        int timeoutSec = intProp(node, "timeoutSec", 5);
        String terminator = stringProp(node, "terminator", "#");
        channel.collectDtmf(ctx.getCallUuid(),
                new CallChannel.DtmfCollectRequest(maxDigits, timeoutSec, terminator));
        return NodeResult.waitForDtmf();
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
