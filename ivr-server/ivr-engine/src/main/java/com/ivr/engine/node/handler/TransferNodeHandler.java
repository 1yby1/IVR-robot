package com.ivr.engine.node.handler;

import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Component
public class TransferNodeHandler implements NodeHandler {

    private final CallChannel channel;

    public TransferNodeHandler(CallChannel channel) {
        this.channel = channel;
    }

    @Override
    public String type() {
        return "transfer";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        String target = stringProp(node, "target", "");
        if (!StringUtils.hasText(target)) {
            return NodeResult.error("transfer target is empty");
        }
        ctx.setVar("transferTo", target);
        channel.transfer(ctx.getCallUuid(), target);
        return NodeResult.end("transfer");
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
