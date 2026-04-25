package com.ivr.engine.node.handler;

import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class VoicemailNodeHandler implements NodeHandler {

    private final CallChannel channel;

    public VoicemailNodeHandler(CallChannel channel) {
        this.channel = channel;
    }

    @Override
    public String type() {
        return "voicemail";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        int maxSeconds = intProp(node, "maxSeconds", 60);
        String filePath = stringProp(node, "filePath", "");
        ctx.setVar("voicemailMaxSec", maxSeconds);
        channel.record(ctx.getCallUuid(), new CallChannel.RecordRequest(maxSeconds, filePath));
        return NodeResult.end("voicemail");
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
