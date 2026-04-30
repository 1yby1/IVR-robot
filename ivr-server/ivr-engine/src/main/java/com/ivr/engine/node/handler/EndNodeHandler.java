package com.ivr.engine.node.handler;

import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;

@Component
public class EndNodeHandler implements NodeHandler {

    private final CallChannel channel;

    public EndNodeHandler(CallChannel channel) {
        this.channel = channel;
    }

    @Override
    public String type() {
        return "end";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        channel.hangup(ctx.getCallUuid(), "NORMAL_CLEARING");
        return NodeResult.end("normal");
    }
}
