package com.ivr.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.event.FlowEventListener;
import com.ivr.engine.graph.FlowGraph;
import com.ivr.engine.graph.FlowGraphParser;
import com.ivr.engine.graph.FlowGraphProvider;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import com.ivr.engine.node.handler.ConditionNodeHandler;
import com.ivr.engine.node.handler.DtmfNodeHandler;
import com.ivr.engine.node.handler.EndNodeHandler;
import com.ivr.engine.node.handler.HttpNodeHandler;
import com.ivr.engine.node.handler.PlayNodeHandler;
import com.ivr.engine.node.handler.StartNodeHandler;
import com.ivr.engine.node.handler.TransferNodeHandler;
import com.ivr.engine.node.handler.VarAssignNodeHandler;
import com.ivr.engine.node.handler.VoicemailNodeHandler;
import com.ivr.engine.session.FlowSession;
import com.ivr.engine.session.InMemorySessionStore;
import com.ivr.engine.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FlowExecutorTest {

    private static final String DEMO_GRAPH = """
            {
              "nodes": [
                {"id":"start","type":"circle","text":"Start","properties":{"bizType":"start","name":"Start"}},
                {"id":"welcome","type":"rect","text":"Welcome","properties":{"bizType":"play","name":"Welcome","ttsText":"Welcome to demo"}},
                {"id":"collect","type":"rect","text":"DTMF","properties":{"bizType":"dtmf","name":"DTMF","maxDigits":1,"timeoutSec":8}},
                {"id":"business","type":"rect","text":"Business","properties":{"bizType":"play","name":"Business","ttsText":"Business done"}},
                {"id":"transfer","type":"rect","text":"Transfer","properties":{"bizType":"transfer","name":"Transfer","target":"1000"}},
                {"id":"end","type":"circle","text":"End","properties":{"bizType":"end","name":"End"}}
              ],
              "edges": [
                {"id":"e1","sourceNodeId":"start","targetNodeId":"welcome"},
                {"id":"e2","sourceNodeId":"welcome","targetNodeId":"collect"},
                {"id":"e3","sourceNodeId":"collect","targetNodeId":"business","text":"1","properties":{"key":"1"}},
                {"id":"e4","sourceNodeId":"collect","targetNodeId":"transfer","text":"2","properties":{"key":"2"}},
                {"id":"e5","sourceNodeId":"business","targetNodeId":"end"}
              ]
            }
            """;

    private SessionStore sessionStore;
    private FlowEventListener listener;
    private CallChannel channel;
    private FlowExecutor executor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        FlowGraphParser parser = new FlowGraphParser(objectMapper);
        FlowGraph graph = parser.parse(DEMO_GRAPH);
        FlowGraphProvider graphProvider = (flowId, version) -> graph;

        sessionStore = new InMemorySessionStore();
        listener = mock(FlowEventListener.class);
        channel = mock(CallChannel.class);

        Map<String, NodeHandler> handlers = new HashMap<>();
        handlers.put("start", new StartNodeHandler());
        handlers.put("end", new EndNodeHandler(channel));
        handlers.put("play", new PlayNodeHandler(channel));
        handlers.put("dtmf", new DtmfNodeHandler(channel));
        handlers.put("condition", new ConditionNodeHandler());
        handlers.put("var_assign", new VarAssignNodeHandler());
        handlers.put("http", new HttpNodeHandler());
        handlers.put("transfer", new TransferNodeHandler(channel));
        handlers.put("voicemail", new VoicemailNodeHandler(channel));

        executor = new FlowExecutor(handlers, sessionStore, graphProvider, listener, channel);
    }

    private FlowContext newCtx(String callUuid) {
        FlowContext ctx = new FlowContext();
        ctx.setCallUuid(callUuid);
        ctx.setCaller("13800001234");
        ctx.setCallee("4001");
        ctx.setFlowId(1L);
        ctx.setFlowVersion(1);
        return ctx;
    }

    @Test
    void start_setsAnswerWaitingButDoesNotAdvance() {
        executor.start(newCtx("call-1"));

        verify(channel, times(1)).answer(eq("call-1"));
        verify(channel, never()).playback(anyString(), any());
        verify(channel, never()).collectDtmf(anyString(), any());
        verify(listener, never()).onNodeEnter(any(), any());

        FlowSession session = executor.sessionOf("call-1");
        assertThat(session).isNotNull();
        assertThat(session.isWaiting()).isTrue();
        assertThat(session.getWaitingFor()).isEqualTo("answer");
        assertThat(session.getCurrentNodeId()).isEqualTo("start");
    }

    @Test
    void resumeOnAnswer_advancesUntilDtmfWait() {
        executor.start(newCtx("call-1"));
        executor.resumeOnAnswer("call-1");

        verify(channel, times(1)).answer(eq("call-1"));
        verify(channel, times(1)).playback(eq("call-1"), any());
        verify(channel, times(1)).collectDtmf(eq("call-1"), any());
        verify(channel, never()).transfer(anyString(), anyString());
        verify(listener, atLeastOnce()).onNodeEnter(any(), any());
        verify(listener, times(1)).onWait(any(), any(), eq("dtmf"));

        FlowSession session = executor.sessionOf("call-1");
        assertThat(session).isNotNull();
        assertThat(session.isWaiting()).isTrue();
        assertThat(session.getWaitingFor()).isEqualTo("dtmf");
        assertThat(session.getCurrentNodeId()).isEqualTo("collect");
    }

    @Test
    void resumeWithDtmf_oneGoesBusinessBranch() {
        executor.start(newCtx("call-2"));
        executor.resumeOnAnswer("call-2");

        executor.resumeWithDtmf("call-2", "1");

        ArgumentCaptor<CallChannel.PlaybackRequest> captor = ArgumentCaptor.forClass(CallChannel.PlaybackRequest.class);
        verify(channel, times(2)).playback(eq("call-2"), captor.capture());
        assertThat(captor.getAllValues().get(0).text()).isEqualTo("Welcome to demo");
        assertThat(captor.getAllValues().get(1).text()).isEqualTo("Business done");

        verify(listener, times(1)).onTerminate(any(), eq("normal"), any());
        verify(channel, times(1)).hangup(eq("call-2"), eq("NORMAL_CLEARING"));
        assertThat(executor.sessionOf("call-2")).isNull();
    }

    @Test
    void resumeWithDtmf_twoGoesTransferBranch() {
        executor.start(newCtx("call-3"));
        executor.resumeOnAnswer("call-3");

        executor.resumeWithDtmf("call-3", "2");

        verify(channel, times(1)).transfer(eq("call-3"), eq("1000"));
        verify(listener, times(1)).onTerminate(any(), eq("transfer"), any());
        assertThat(executor.sessionOf("call-3")).isNull();
    }

    @Test
    void resumeWithDtmf_unmatchedDigitTerminates() {
        executor.start(newCtx("call-4"));
        executor.resumeOnAnswer("call-4");

        executor.resumeWithDtmf("call-4", "9");

        verify(listener, times(1)).onTerminate(any(), eq("dtmf-no-match"), any());
        verify(channel, never()).transfer(anyString(), anyString());
        assertThat(executor.sessionOf("call-4")).isNull();
    }

    @Test
    void abort_endsWaitingSession() {
        executor.start(newCtx("call-5"));
        executor.resumeOnAnswer("call-5");
        assertThat(executor.sessionOf("call-5")).isNotNull();

        executor.abort("call-5");

        verify(listener, times(1)).onTerminate(any(), eq("hangup"), any());
        assertThat(executor.sessionOf("call-5")).isNull();
    }

    @Test
    void resumeOnAnswer_whenNotWaitingForAnswer_isIgnored() {
        executor.resumeOnAnswer("ghost");
        verify(channel, never()).playback(anyString(), any());
        verify(listener, never()).onNodeEnter(any(), any());
    }

    @Test
    void abort_unknownSessionIsNoop() {
        executor.abort("never-started");
        verify(listener, never()).onTerminate(any(), anyString(), any());
    }

    @Test
    void resumeWithDtmf_whenNotWaiting_isIgnored() {
        executor.resumeWithDtmf("ghost", "1");
        verify(channel, never()).playback(anyString(), any());
        verify(listener, never()).onTerminate(any(), anyString(), any());
    }

    @Test
    void supportedNodeTypes_listsAllRegistered() {
        assertThat(executor.supportedNodeTypes())
                .contains("start", "end", "play", "dtmf", "condition", "var_assign",
                        "http", "transfer", "voicemail");
    }

    @Test
    void start_withMissingFlowId_isNoop() {
        FlowContext ctx = new FlowContext();
        ctx.setCallUuid("call-bad");
        executor.start(ctx);
        assertThat(executor.sessionOf("call-bad")).isNull();
    }

    @Test
    void conditionNode_evaluatesSpel() {
        // 直接用 ConditionNodeHandler 做最小验证：lastDtmf == '1' → "true"
        ConditionNodeHandler handler = new ConditionNodeHandler();
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.id = "cond";
        node.type = "condition";
        node.properties = Map.of("expression", "lastDtmf == '1' ? 'yes' : 'no'");

        FlowContext ctx = new FlowContext();
        ctx.setLastDtmf("1");
        NodeHandler.NodeResult r1 = handler.execute(node, ctx);
        assertThat(r1.branch).isEqualTo("yes");

        ctx.setLastDtmf("2");
        NodeHandler.NodeResult r2 = handler.execute(node, ctx);
        assertThat(r2.branch).isEqualTo("no");
    }

    @Test
    void playNode_rendersTemplateVars() {
        FlowContext ctx = newCtx("call-tpl");
        ctx.setVar("name", "Alice");

        PlayNodeHandler handler = new PlayNodeHandler(channel);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("ttsText", "Hello ${name}");
        handler.execute(node, ctx);

        ArgumentCaptor<CallChannel.PlaybackRequest> captor = ArgumentCaptor.forClass(CallChannel.PlaybackRequest.class);
        verify(channel).playback(eq("call-tpl"), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("Hello Alice");
    }

    @Test
    void varAssignNode_writesRenderedVariable() {
        FlowContext ctx = newCtx("call-var");
        ctx.setVar("level", "VIP");

        VarAssignNodeHandler handler = new VarAssignNodeHandler();
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("varName", "greeting", "value", "Hello ${level}");
        NodeHandler.NodeResult result = handler.execute(node, ctx);

        assertThat(result.branch).isEqualTo("default");
        assertThat(ctx.getVar("greeting")).isEqualTo("Hello VIP");
    }

    @Test
    void httpNode_emptyUrlGoesFallback() {
        FlowContext ctx = newCtx("call-http");

        HttpNodeHandler handler = new HttpNodeHandler();
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("fallbackBranch", "fallback");
        NodeHandler.NodeResult result = handler.execute(node, ctx);

        assertThat(result.branch).isEqualTo("fallback");
    }
}
