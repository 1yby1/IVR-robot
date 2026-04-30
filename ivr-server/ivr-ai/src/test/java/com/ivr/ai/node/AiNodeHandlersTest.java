package com.ivr.ai.node;

import com.ivr.ai.LlmService;
import com.ivr.ai.rag.InMemoryKnowledgeService;
import com.ivr.ai.rag.KnowledgeService;
import com.ivr.engine.channel.CallChannel;
import com.ivr.engine.node.FlowContext;
import com.ivr.engine.node.NodeHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiNodeHandlersTest {

    // ---------- IntentNodeHandler ----------

    @Test
    void intent_hit_returnsBranch() {
        LlmService llm = mock(LlmService.class);
        when(llm.detectIntent(eq("我想查账单"), anyList())).thenReturn("查询账单");

        IntentNodeHandler handler = new IntentNodeHandler(llm);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of(
                "intents", List.of("查询账单", "投诉建议"),
                "fallbackBranch", "other"
        );

        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("我想查账单");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.branch).isEqualTo("查询账单");
        assertThat(ctx.getVar("lastIntent")).isEqualTo("查询账单");
    }

    @Test
    void intent_miss_returnsFallback() {
        LlmService llm = mock(LlmService.class);
        when(llm.detectIntent(anyString(), anyList())).thenReturn("other");

        IntentNodeHandler handler = new IntentNodeHandler(llm);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("intents", List.of("A", "B"), "fallbackBranch", "fallback");

        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("一段无关文本");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.branch).isEqualTo("fallback");
        assertThat(ctx.getVar("lastIntent")).isEqualTo("fallback");
    }

    @Test
    void intent_llmThrows_returnsFallback() {
        LlmService llm = mock(LlmService.class);
        when(llm.detectIntent(anyString(), anyList())).thenThrow(new RuntimeException("timeout"));

        IntentNodeHandler handler = new IntentNodeHandler(llm);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("intents", List.of("A"));

        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("hello");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.branch).isEqualTo("other");
    }

    @Test
    void intent_emptyInput_skipsLlm() {
        LlmService llm = mock(LlmService.class);
        IntentNodeHandler handler = new IntentNodeHandler(llm);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("intents", List.of("A", "B"));

        NodeHandler.NodeResult result = handler.execute(node, new FlowContext());
        assertThat(result.branch).isEqualTo("other");
        verifyNoInteractions(llm);
    }

    @Test
    void intent_intentsAsCommaString_isParsed() {
        LlmService llm = mock(LlmService.class);
        when(llm.detectIntent(anyString(), anyList())).thenReturn("B");

        IntentNodeHandler handler = new IntentNodeHandler(llm);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("intents", "A，B,C");
        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("xxx");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.branch).isEqualTo("B");
    }

    // ---------- RagNodeHandler ----------

    @Test
    void rag_hit_writesAnswerVar() {
        KnowledgeService kb = mock(KnowledgeService.class);
        when(kb.retrieve(any(), eq("营业时间是几点"), eq(3)))
                .thenReturn(List.of(new KnowledgeService.KnowledgeChunk(
                        "faq-1", "营业时间", "9:00 至 21:00", 1.0)));
        LlmService llm = mock(LlmService.class);
        when(llm.chatTemplate(anyString(), any())).thenReturn("我们的营业时间是 9:00 至 21:00。");

        RagNodeHandler handler = new RagNodeHandler(llm, kb);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of();

        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("营业时间是几点");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.terminate).isFalse();
        assertThat(result.waitFor).isNull();
        assertThat(ctx.getVar("ragAnswer")).isEqualTo("我们的营业时间是 9:00 至 21:00。");
        assertThat(ctx.getVar("ragHits")).isEqualTo(1);
    }

    @Test
    void rag_noChunks_goesFallback() {
        KnowledgeService kb = mock(KnowledgeService.class);
        when(kb.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());
        LlmService llm = mock(LlmService.class);

        RagNodeHandler handler = new RagNodeHandler(llm, kb);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("fallbackBranch", "transfer");

        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("某个偏门问题");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.branch).isEqualTo("transfer");
        verifyNoInteractions(llm);
    }

    @Test
    void rag_llmThrows_goesFallback() {
        KnowledgeService kb = mock(KnowledgeService.class);
        when(kb.retrieve(any(), anyString(), anyInt()))
                .thenReturn(List.of(new KnowledgeService.KnowledgeChunk("d", "t", "c", 1.0)));
        LlmService llm = mock(LlmService.class);
        when(llm.chatTemplate(anyString(), any())).thenThrow(new RuntimeException("rate-limit"));

        RagNodeHandler handler = new RagNodeHandler(llm, kb);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of();

        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("hello");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.branch).isEqualTo("fallback");
    }

    @Test
    void rag_endToEndWithInMemoryKb_returnsAnswer() {
        InMemoryKnowledgeService kb = new InMemoryKnowledgeService();
        LlmService llm = mock(LlmService.class);
        when(llm.chatTemplate(anyString(), any())).thenReturn("营业时间答复");

        RagNodeHandler handler = new RagNodeHandler(llm, kb);
        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of();

        FlowContext ctx = new FlowContext();
        ctx.setLastAsr("营业时间");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.branch).isEqualTo("default");
        assertThat(ctx.getVar("ragAnswer")).isEqualTo("营业时间答复");
        assertThat((int) ctx.getVar("ragHits")).isGreaterThan(0);
    }

    // ---------- AsrNodeHandler ----------

    @Test
    void asr_dispatchesCollectAndWaits() {
        CallChannel channel = mock(CallChannel.class);
        AsrNodeHandler handler = new AsrNodeHandler(channel);

        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        node.properties = Map.of("maxSeconds", 12, "language", "zh-CN", "prompt", "请说出您的问题");

        FlowContext ctx = new FlowContext();
        ctx.setCallUuid("call-asr-1");

        NodeHandler.NodeResult result = handler.execute(node, ctx);
        assertThat(result.waitFor).isEqualTo("asr");

        ArgumentCaptor<CallChannel.AsrCollectRequest> captor = ArgumentCaptor.forClass(CallChannel.AsrCollectRequest.class);
        verify(channel, times(1)).collectAsr(eq("call-asr-1"), captor.capture());
        CallChannel.AsrCollectRequest req = captor.getValue();
        assertThat(req.maxSeconds()).isEqualTo(12);
        assertThat(req.language()).isEqualTo("zh-CN");
        assertThat(req.prompt()).isEqualTo("请说出您的问题");
    }

    @Test
    void asr_usesDefaults_whenPropsMissing() {
        CallChannel channel = mock(CallChannel.class);
        AsrNodeHandler handler = new AsrNodeHandler(channel);

        NodeHandler.FlowNode node = new NodeHandler.FlowNode();
        FlowContext ctx = new FlowContext();
        ctx.setCallUuid("call-asr-2");

        handler.execute(node, ctx);
        ArgumentCaptor<CallChannel.AsrCollectRequest> captor = ArgumentCaptor.forClass(CallChannel.AsrCollectRequest.class);
        verify(channel).collectAsr(eq("call-asr-2"), captor.capture());
        assertThat(captor.getValue().maxSeconds()).isEqualTo(8);
        assertThat(captor.getValue().language()).isEqualTo("zh-CN");
    }
}
