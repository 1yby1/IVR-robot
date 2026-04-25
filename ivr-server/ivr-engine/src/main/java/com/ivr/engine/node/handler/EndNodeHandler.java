package com.ivr.engine.node.handler;

import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;

@Component
public class EndNodeHandler implements NodeHandler {

    @Override
    public String type() {
        return "end";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        return NodeResult.end("normal");
    }
}
