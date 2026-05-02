package com.ivr.engine;

import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.event.FlowEventListener;
import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.graph.FlowGraphProvider;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import com.ivr.engine.session.FlowSession;
import com.ivr.engine.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程执行器：按 LogicFlow 节点图同步推进，遇到 {@code waitFor} 节点立即暂停 + 返回。
 *
 * <p>四个入口：
 * <ul>
 *   <li>{@link #start(FlowContext)} —— 一通呼叫接通前调用：加载流程、建会话、发 answer，
 *       会话置为等待 {@code answer}（**不**立即推进）。</li>
 *   <li>{@link #resumeOnAnswer(String)} —— CHANNEL_ANSWER 事件回喂，从 start 节点开始推进。
 *       与异步 {@code uuid_answer} 解耦，避免向未应答的通道下发媒体命令。</li>
 *   <li>{@link #resumeWithDtmf(String, String)} —— DTMF 事件喂回，继续推进</li>
 *   <li>{@link #resumeWithAsr(String, String)} —— ASR 文本喂回，继续推进</li>
 *   <li>{@link #abort(String)} —— 挂机或异常清理</li>
 * </ul>
 *
 * <p>会话状态由 {@link SessionStore} 持有，executor 自身无状态、线程安全。
 */
@Service
public class FlowExecutor {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutor.class);
    /** 单次推进最大节点步数，防止图中环路打死 CPU。 */
    private static final int MAX_STEPS_PER_ADVANCE = 50;

    private final Map<String, NodeHandler> handlers;
    private final SessionStore sessionStore;
    private final FlowGraphProvider graphProvider;
    private final FlowEventListener listener;
    private final CallChannel channel;

    public FlowExecutor(Map<String, NodeHandler> handlers,
                        SessionStore sessionStore,
                        FlowGraphProvider graphProvider,
                        FlowEventListener listener,
                        CallChannel channel) {
        this.handlers = handlers.values().stream()
                .collect(Collectors.toMap(
                        NodeHandler::type,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("Duplicate NodeHandler type: " + left.type());
                        },
                        LinkedHashMap::new));
        this.sessionStore = sessionStore;
        this.graphProvider = graphProvider;
        this.listener = listener;
        this.channel = channel;
    }

    public List<String> supportedNodeTypes() {
        return handlers.keySet().stream().sorted().toList();
    }

    /**
     * 启动一通呼叫的流程执行。会话以 callUuid 为 sessionId 索引。
     *
     * <p>**不立即推进流程**：发 {@code uuid_answer} 后把会话置成 waitingFor=="answer"，
     * 等 ESL 的 CHANNEL_ANSWER 事件触发 {@link #resumeOnAnswer(String)} 后再开始播报。
     * 这样把"应答 + 播报"的次序固化下来，避免给未应答的通道发 uuid_broadcast。
     */
    public void start(FlowContext ctx) {
        if (ctx == null || !StringUtils.hasText(ctx.getCallUuid())) {
            throw new IllegalArgumentException("callUuid is required");
        }
        if (ctx.getFlowId() == null) {
            log.warn("[FlowExecutor] start aborted: flowId missing call={}", ctx.getCallUuid());
            return;
        }
        if (sessionStore.find(ctx.getCallUuid()) != null) {
            log.warn("[FlowExecutor] session already exists call={}", ctx.getCallUuid());
            return;
        }

        FlowGraph graph;
        try {
            graph = graphProvider.load(ctx.getFlowId(), ctx.getFlowVersion());
        } catch (Exception e) {
            log.error("[FlowExecutor] load graph failed flow={} version={}",
                    ctx.getFlowId(), ctx.getFlowVersion(), e);
            return;
        }

        FlowGraph.Node startNode = graph.findStart();
        if (startNode == null) {
            startNode = graph.getNodes().values().stream().findFirst().orElse(null);
        }
        if (startNode == null) {
            log.warn("[FlowExecutor] graph has no nodes flow={}", ctx.getFlowId());
            return;
        }

        ctx.setSessionId(ctx.getCallUuid());
        ctx.setVar("callUuid", ctx.getCallUuid());
        ctx.setVar("caller", ctx.getCaller());
        ctx.setVar("callee", ctx.getCallee());
        FlowSession session = new FlowSession();
        session.setSessionId(ctx.getCallUuid());
        session.setFlowId(ctx.getFlowId());
        session.setFlowVersion(ctx.getFlowVersion());
        session.setGraph(graph);
        session.setContext(ctx);
        session.setCurrentNodeId(startNode.getId());
        session.setStatus(FlowSession.STATUS_WAITING);
        session.setWaitingFor("answer");
        sessionStore.save(session);

        log.info("[FlowExecutor] start session={} flow={} version={} from={} (waiting for answer)",
                session.getSessionId(), ctx.getFlowId(), ctx.getFlowVersion(), startNode.getId());
        channel.answer(ctx.getCallUuid());
    }

    /** CHANNEL_ANSWER 回喂，从 start 节点开始推进。LoggingCallChannel 模式下由调用方紧跟 start 调用。 */
    public void resumeOnAnswer(String callUuid) {
        FlowSession session = requireWaitingSession(callUuid, "answer");
        if (session == null) {
            return;
        }
        String startNodeId = session.getCurrentNodeId();
        session.setStatus(FlowSession.STATUS_RUNNING);
        session.setWaitingFor(null);
        advance(session, startNodeId);
    }

    /** DTMF 事件回喂，仅当会话处于 dtmf 等待时生效。 */
    public void resumeWithDtmf(String callUuid, String digit) {
        FlowSession session = requireWaitingSession(callUuid, "dtmf");
        if (session == null) {
            return;
        }
        FlowGraph.Node currentNode = session.getGraph().node(session.getCurrentNodeId());
        session.getContext().setLastDtmf(digit);
        session.getContext().setVar("lastDtmf", digit);
        session.setStatus(FlowSession.STATUS_RUNNING);
        session.setWaitingFor(null);

        String nextNodeId = resolveDtmfNext(currentNode, session.getGraph(), digit);
        if (!StringUtils.hasText(nextNodeId)) {
            terminate(session, "dtmf-no-match", "no branch matched digit=" + digit);
            return;
        }
        advance(session, nextNodeId);
    }

    /** ASR 文本回喂，仅当会话处于 asr 等待时生效。 */
    public void resumeWithAsr(String callUuid, String text) {
        FlowSession session = requireWaitingSession(callUuid, "asr");
        if (session == null) {
            return;
        }
        session.getContext().setLastAsr(text);
        session.getContext().setVar("lastAsr", text);
        session.setStatus(FlowSession.STATUS_RUNNING);
        session.setWaitingFor(null);

        FlowGraph.Node currentNode = session.getGraph().node(session.getCurrentNodeId());
        String nextNodeId = firstOutgoing(session.getGraph(), currentNode.getId());
        if (!StringUtils.hasText(nextNodeId)) {
            terminate(session, "normal", "asr node has no outgoing edge");
            return;
        }
        advance(session, nextNodeId);
    }

    /** 通话挂机或异常清理。可重入：会话不存在直接 no-op。 */
    public void abort(String callUuid) {
        FlowSession session = sessionStore.find(callUuid);
        if (session == null) {
            return;
        }
        if (!session.isEnded()) {
            terminate(session, "hangup", "channel hangup");
        } else {
            sessionStore.remove(callUuid);
        }
    }

    public FlowSession sessionOf(String callUuid) {
        return sessionStore.find(callUuid);
    }

    // ----- internals -----

    private FlowSession requireWaitingSession(String callUuid, String expectedWaitFor) {
        FlowSession session = sessionStore.find(callUuid);
        if (session == null) {
            log.warn("[FlowExecutor] session not found call={} expect={}", callUuid, expectedWaitFor);
            return null;
        }
        if (!session.isWaiting() || !expectedWaitFor.equals(session.getWaitingFor())) {
            log.warn("[FlowExecutor] session not waiting for {} call={} status={} waitingFor={}",
                    expectedWaitFor, callUuid, session.getStatus(), session.getWaitingFor());
            return null;
        }
        return session;
    }

    /** 推进节点循环。直到遇到 wait / terminate / 无后续节点为止。 */
    private void advance(FlowSession session, String startNodeId) {
        String currentNodeId = startNodeId;
        FlowGraph graph = session.getGraph();
        FlowContext ctx = session.getContext();

        for (int i = 0; i < MAX_STEPS_PER_ADVANCE; i++) {
            FlowGraph.Node node = graph.node(currentNodeId);
            if (node == null) {
                terminate(session, "error", "node not found: " + currentNodeId);
                return;
            }
            session.setCurrentNodeId(node.getId());
            session.getVisitedNodes().add(node.getId());
            session.incrementStepCount();

            NodeHandler handler = handlers.get(node.bizType());
            if (handler == null) {
                terminate(session, "error", "no handler for type: " + node.bizType());
                return;
            }

            listener.onNodeEnter(session, node);
            NodeHandler.NodeResult result;
            try {
                NodeHandler.FlowNode flowNode = toFlowNode(node);
                result = handler.execute(flowNode, ctx);
            } catch (Exception e) {
                listener.onError(session, node, e);
                terminate(session, "error", e.getMessage());
                return;
            }
            listener.onNodeExit(session, node, exitPayload(result));

            if (result.terminate) {
                terminate(session, result.terminateReason == null ? "normal" : result.terminateReason, result.errorMsg);
                return;
            }
            if (StringUtils.hasText(result.waitFor)) {
                session.setStatus(FlowSession.STATUS_WAITING);
                session.setWaitingFor(result.waitFor);
                sessionStore.save(session);
                listener.onWait(session, node, result.waitFor);
                return;
            }

            String nextNodeId = resolveBranch(graph, node.getId(), result.branch);
            if (!StringUtils.hasText(nextNodeId)) {
                terminate(session, "normal", "no outgoing edge from " + node.getId());
                return;
            }
            currentNodeId = nextNodeId;
        }
        terminate(session, "error", "max steps exceeded, possible cycle");
    }

    private void terminate(FlowSession session, String reason, String message) {
        session.setStatus(FlowSession.STATUS_ENDED);
        session.setWaitingFor(null);
        session.setTerminateReason(reason);
        session.setErrorMsg(message);
        session.setEndedAt(LocalDateTime.now());
        sessionStore.save(session);
        listener.onTerminate(session, reason, message);
        sessionStore.remove(session.getSessionId());
    }

    private NodeHandler.FlowNode toFlowNode(FlowGraph.Node node) {
        NodeHandler.FlowNode fn = new NodeHandler.FlowNode();
        fn.id = node.getId();
        fn.type = node.bizType();
        fn.text = node.getText();
        fn.properties = node.getProperties();
        return fn;
    }

    private Map<String, Object> exitPayload(NodeHandler.NodeResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result.terminate) {
            payload.put("terminate", true);
            payload.put("reason", result.terminateReason);
            if (result.errorMsg != null) {
                payload.put("error", result.errorMsg);
            }
        } else if (StringUtils.hasText(result.waitFor)) {
            payload.put("waitFor", result.waitFor);
        } else {
            payload.put("branch", result.branch);
        }
        return payload;
    }

    /**
     * 通用分支查找：按 branchKey 精确匹配 edge.branchKey()；找不到则取第一条无 branchKey 的边作为 default。
     */
    private String resolveBranch(FlowGraph graph, String nodeId, String branch) {
        List<FlowGraph.Edge> outgoing = graph.outgoing(nodeId);
        if (outgoing.isEmpty()) {
            return "";
        }
        if (branch != null && !"default".equals(branch)) {
            for (FlowGraph.Edge edge : outgoing) {
                if (branch.equals(edge.branchKey())) {
                    return edge.getTargetNodeId();
                }
            }
        }
        for (FlowGraph.Edge edge : outgoing) {
            if (!StringUtils.hasText(edge.branchKey())) {
                return edge.getTargetNodeId();
            }
        }
        // 没有 default 边时退化到第一条
        return outgoing.get(0).getTargetNodeId();
    }

    /**
     * DTMF 节点专用分支查找，规则比通用版多一层 mappings 兜底：
     * <ol>
     *   <li>先按 edge.branchKey() == digit 精确匹配</li>
     *   <li>再按 node.properties.mappings[].key == digit → mappings[].nextNode</li>
     *   <li>最后取无 key 的默认边</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private String resolveDtmfNext(FlowGraph.Node node, FlowGraph graph, String digit) {
        List<FlowGraph.Edge> outgoing = graph.outgoing(node.getId());
        for (FlowGraph.Edge edge : outgoing) {
            if (digit.equals(edge.branchKey())) {
                return edge.getTargetNodeId();
            }
        }
        Object mappings = node.getProperties().get("mappings");
        if (mappings instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object key = map.get("key");
                Object nextNode = map.get("nextNode");
                if (key != null && digit.equals(key.toString())
                        && nextNode != null && StringUtils.hasText(nextNode.toString())) {
                    return nextNode.toString();
                }
            }
        }
        for (FlowGraph.Edge edge : outgoing) {
            if (!StringUtils.hasText(edge.branchKey())) {
                return edge.getTargetNodeId();
            }
        }
        return "";
    }

    private String firstOutgoing(FlowGraph graph, String nodeId) {
        return graph.outgoing(nodeId).stream()
                .map(FlowGraph.Edge::getTargetNodeId)
                .findFirst()
                .orElse("");
    }
}
