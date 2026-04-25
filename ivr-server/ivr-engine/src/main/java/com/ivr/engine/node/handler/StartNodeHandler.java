package com.ivr.engine.node.handler;

import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.springframework.stereotype.Component;

@Component
public class StartNodeHandler implements NodeHandler {

    @Override
    public String type() {
        return "start";
    }

    @Override
    public NodeResult execute(FlowNode node, FlowContext ctx) {
        return NodeResult.next();
    }
}
