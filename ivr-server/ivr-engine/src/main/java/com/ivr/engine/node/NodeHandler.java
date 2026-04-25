package com.ivr.engine.node;

import java.util.Map;

/**
 * IVR 节点执行器 SPI。每种 LogicFlow 节点类型对应一个 Handler 实现。
 *
 * <p>Spring 启动时扫描所有 {@link NodeHandler} Bean，按 {@link #type()} 注入到
 * {@code Map<String, NodeHandler>}，流程引擎按节点 type 路由。
 */
public interface NodeHandler {

    /** LogicFlow 节点 type（start / play / dtmf / condition / intent / rag ...） */
    String type();

    /**
     * 执行节点逻辑。
     *
     * @param node 节点定义（包含配置 JSON）
     * @param ctx  运行上下文（通话 channel、变量、当前 flow）
     * @return 执行结果，包含下一分支标识或暂停意图
     */
    NodeResult execute(FlowNode node, FlowContext ctx);

    /** 节点定义（对应 LogicFlow node） */
    class FlowNode {
        public String id;
        public String type;
        public String text;
        public Map<String, Object> properties;
    }

    /** 节点执行结果 */
    class NodeResult {
        /** 命中分支标签，对应 edge 的 properties.key 或 text；未匹配时取 default 出边 */
        public String branch;
        /** 是否终止流程（结束节点、出错、转人工、留言） */
        public boolean terminate;
        /** 终止原因，写入会话状态用于落库 endReason */
        public String terminateReason;
        /** 错误信息（terminate=true 时可选） */
        public String errorMsg;
        /** 暂停等待的事件类型："dtmf" / "asr" / null。非空时 executor 立即挂起会话。 */
        public String waitFor;

        public static NodeResult next() {
            NodeResult r = new NodeResult();
            r.branch = "default";
            return r;
        }

        public static NodeResult branch(String branch) {
            NodeResult r = new NodeResult();
            r.branch = branch;
            return r;
        }

        public static NodeResult end() {
            NodeResult r = new NodeResult();
            r.terminate = true;
            r.terminateReason = "normal";
            return r;
        }

        public static NodeResult end(String reason) {
            NodeResult r = new NodeResult();
            r.terminate = true;
            r.terminateReason = reason;
            return r;
        }

        public static NodeResult error(String msg) {
            NodeResult r = new NodeResult();
            r.terminate = true;
            r.terminateReason = "error";
            r.errorMsg = msg;
            return r;
        }

        public static NodeResult waitForDtmf() {
            NodeResult r = new NodeResult();
            r.waitFor = "dtmf";
            return r;
        }

        public static NodeResult waitForAsr() {
            NodeResult r = new NodeResult();
            r.waitFor = "asr";
            return r;
        }
    }
}
