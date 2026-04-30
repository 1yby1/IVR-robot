package com.ivr.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.FlowDebugInputRequest;
import com.ivr.admin.dto.FlowDebugResponse;
import com.ivr.admin.dto.FlowDebugStartRequest;
import com.ivr.ai.LlmService;
import com.ivr.ai.rag.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowDebugServiceTest {

    private FlowStore flowStore;
    private CallRecordService callRecordService;
    private LlmService llmService;
    private KnowledgeService knowledgeService;
    private FlowDebugService service;

    @BeforeEach
    void setUp() {
        flowStore = mock(FlowStore.class);
        callRecordService = mock(CallRecordService.class);
        llmService = mock(LlmService.class);
        knowledgeService = mock(KnowledgeService.class);
        service = new FlowDebugService(
                flowStore,
                new ObjectMapper(),
                callRecordService,
                llmService,
                knowledgeService
        );
    }

    private void stubFlow(Long flowId, String graphJson) {
        when(flowStore.detail(flowId)).thenReturn(Map.of(
                "graphJson", graphJson,
                "currentVersion", 1,
                "flowName", "test flow"
        ));
    }

    private static final String INTENT_GRAPH = """
            {
              "nodes": [
                {"id":"start","type":"circle","properties":{"bizType":"start","name":"start"}},
                {"id":"ask","type":"rect","properties":{"bizType":"play","name":"ask","ttsText":"请问需要什么服务"}},
                {"id":"intent","type":"rect","properties":{"bizType":"intent","name":"intent","inputVar":"lastAsr","intents":["查询账单","转人工"],"fallbackBranch":"other"}},
                {"id":"bill","type":"rect","properties":{"bizType":"play","name":"bill","ttsText":"正在为您查询账单"}},
                {"id":"transfer","type":"rect","properties":{"bizType":"transfer","name":"transfer","target":"1000"}},
                {"id":"fallback","type":"rect","properties":{"bizType":"play","name":"fallback","ttsText":"未识别您的意图"}},
                {"id":"end1","type":"circle","properties":{"bizType":"end","name":"end1"}},
                {"id":"end2","type":"circle","properties":{"bizType":"end","name":"end2"}}
              ],
              "edges": [
                {"sourceNodeId":"start","targetNodeId":"ask"},
                {"sourceNodeId":"ask","targetNodeId":"intent"},
                {"sourceNodeId":"intent","targetNodeId":"bill","properties":{"key":"查询账单"}},
                {"sourceNodeId":"intent","targetNodeId":"transfer","properties":{"key":"转人工"}},
                {"sourceNodeId":"intent","targetNodeId":"fallback","properties":{"key":"other"}},
                {"sourceNodeId":"bill","targetNodeId":"end1"},
                {"sourceNodeId":"fallback","targetNodeId":"end2"}
              ]
            }
            """;

    @Test
    void intent_skips_llm_when_input_empty() {
        // 没有 asr 节点，lastAsr 为空 → intent 节点直接走 fallback，不调 LLM
        stubFlow(1L, INTENT_GRAPH);

        FlowDebugResponse response = service.start(1L, new FlowDebugStartRequest());

        assertThat(response.getStatus()).isEqualTo("ended");
        assertThat(response.getPrompts()).contains("未识别您的意图");
        assertThat(response.getVariables()).containsEntry("lastIntent", "other");
    }

    @Test
    void intent_with_asr_input_hits_branch() {
        // 改用包含 asr 节点的图
        String graphWithAsr = """
                {
                  "nodes": [
                    {"id":"start","type":"circle","properties":{"bizType":"start","name":"start"}},
                    {"id":"ask","type":"rect","properties":{"bizType":"asr","name":"ask","prompt":"请问需要什么服务"}},
                    {"id":"intent","type":"rect","properties":{"bizType":"intent","name":"intent","intents":["查询账单","转人工"],"fallbackBranch":"other"}},
                    {"id":"bill","type":"rect","properties":{"bizType":"play","name":"bill","ttsText":"正在为您查询账单"}},
                    {"id":"transfer","type":"rect","properties":{"bizType":"transfer","name":"transfer","target":"1000"}},
                    {"id":"fallback","type":"rect","properties":{"bizType":"play","name":"fallback","ttsText":"未识别"}},
                    {"id":"end","type":"circle","properties":{"bizType":"end","name":"end"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"ask"},
                    {"sourceNodeId":"ask","targetNodeId":"intent"},
                    {"sourceNodeId":"intent","targetNodeId":"bill","properties":{"key":"查询账单"}},
                    {"sourceNodeId":"intent","targetNodeId":"transfer","properties":{"key":"转人工"}},
                    {"sourceNodeId":"intent","targetNodeId":"fallback","properties":{"key":"other"}},
                    {"sourceNodeId":"bill","targetNodeId":"end"},
                    {"sourceNodeId":"fallback","targetNodeId":"end"}
                  ]
                }
                """;
        stubFlow(2L, graphWithAsr);
        when(llmService.detectIntent(eq("我想查账单"), ArgumentMatchers.<List<String>>any()))
                .thenReturn("查询账单");

        FlowDebugResponse start = service.start(2L, new FlowDebugStartRequest());
        assertThat(start.getStatus()).isEqualTo("waiting");
        assertThat(start.getWaitingFor()).isEqualTo("asr");

        FlowDebugInputRequest input = new FlowDebugInputRequest();
        input.setInput("我想查账单");
        FlowDebugResponse response = service.input(start.getSessionId(), input);

        assertThat(response.getStatus()).isEqualTo("ended");
        assertThat(response.getPrompts()).contains("正在为您查询账单");
        assertThat(response.getVariables()).containsEntry("lastIntent", "查询账单");
        assertThat(response.getEvents()).anyMatch(e -> e.contains("命中") && e.contains("查询账单"));
    }

    @Test
    void intent_unmatched_falls_back() {
        String graphWithAsr = """
                {
                  "nodes": [
                    {"id":"start","type":"circle","properties":{"bizType":"start","name":"start"}},
                    {"id":"ask","type":"rect","properties":{"bizType":"asr","name":"ask"}},
                    {"id":"intent","type":"rect","properties":{"bizType":"intent","name":"intent","intents":["A","B"],"fallbackBranch":"other"}},
                    {"id":"a","type":"rect","properties":{"bizType":"play","name":"a","ttsText":"A 分支"}},
                    {"id":"fallback","type":"rect","properties":{"bizType":"play","name":"fallback","ttsText":"FALLBACK"}},
                    {"id":"end","type":"circle","properties":{"bizType":"end","name":"end"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"ask"},
                    {"sourceNodeId":"ask","targetNodeId":"intent"},
                    {"sourceNodeId":"intent","targetNodeId":"a","properties":{"key":"A"}},
                    {"sourceNodeId":"intent","targetNodeId":"fallback","properties":{"key":"other"}},
                    {"sourceNodeId":"a","targetNodeId":"end"},
                    {"sourceNodeId":"fallback","targetNodeId":"end"}
                  ]
                }
                """;
        stubFlow(3L, graphWithAsr);
        when(llmService.detectIntent(anyString(), ArgumentMatchers.<List<String>>any()))
                .thenReturn("other");

        FlowDebugResponse start = service.start(3L, new FlowDebugStartRequest());
        FlowDebugInputRequest input = new FlowDebugInputRequest();
        input.setInput("胡言乱语");
        FlowDebugResponse response = service.input(start.getSessionId(), input);

        assertThat(response.getVariables()).containsEntry("lastIntent", "other");
        assertThat(response.getPrompts()).contains("FALLBACK");
    }

    @Test
    void rag_hit_fills_answer_var() {
        String graph = """
                {
                  "nodes": [
                    {"id":"start","type":"circle","properties":{"bizType":"start","name":"start"}},
                    {"id":"ask","type":"rect","properties":{"bizType":"asr","name":"ask"}},
                    {"id":"rag","type":"rect","properties":{"bizType":"rag","name":"rag","topK":3}},
                    {"id":"reply","type":"rect","properties":{"bizType":"play","name":"reply","ttsText":"${ragAnswer}"}},
                    {"id":"fallback","type":"rect","properties":{"bizType":"play","name":"fallback","ttsText":"无答案"}},
                    {"id":"end","type":"circle","properties":{"bizType":"end","name":"end"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"ask"},
                    {"sourceNodeId":"ask","targetNodeId":"rag"},
                    {"sourceNodeId":"rag","targetNodeId":"reply"},
                    {"sourceNodeId":"rag","targetNodeId":"fallback","properties":{"key":"fallback"}},
                    {"sourceNodeId":"reply","targetNodeId":"end"},
                    {"sourceNodeId":"fallback","targetNodeId":"end"}
                  ]
                }
                """;
        stubFlow(4L, graph);
        when(knowledgeService.retrieve(any(), eq("营业时间"), anyInt())).thenReturn(List.of(
                new KnowledgeService.KnowledgeChunk("d1", "营业时间", "工作日 9 点到 18 点", 0.9)
        ));
        when(llmService.chatTemplate(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn("工作日 9 点到 18 点开门");

        FlowDebugResponse start = service.start(4L, new FlowDebugStartRequest());
        FlowDebugInputRequest input = new FlowDebugInputRequest();
        input.setInput("营业时间");
        FlowDebugResponse response = service.input(start.getSessionId(), input);

        assertThat(response.getStatus()).isEqualTo("ended");
        assertThat(response.getVariables()).containsEntry("ragAnswer", "工作日 9 点到 18 点开门");
        assertThat(response.getPrompts()).contains("工作日 9 点到 18 点开门");
    }

    @Test
    void rag_no_hits_goes_fallback() {
        String graph = """
                {
                  "nodes": [
                    {"id":"start","type":"circle","properties":{"bizType":"start","name":"start"}},
                    {"id":"ask","type":"rect","properties":{"bizType":"asr","name":"ask"}},
                    {"id":"rag","type":"rect","properties":{"bizType":"rag","name":"rag","fallbackBranch":"fallback"}},
                    {"id":"reply","type":"rect","properties":{"bizType":"play","name":"reply","ttsText":"${ragAnswer}"}},
                    {"id":"fallback","type":"rect","properties":{"bizType":"play","name":"fallback","ttsText":"暂无相关信息"}},
                    {"id":"end","type":"circle","properties":{"bizType":"end","name":"end"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"ask"},
                    {"sourceNodeId":"ask","targetNodeId":"rag"},
                    {"sourceNodeId":"rag","targetNodeId":"reply"},
                    {"sourceNodeId":"rag","targetNodeId":"fallback","properties":{"key":"fallback"}},
                    {"sourceNodeId":"reply","targetNodeId":"end"},
                    {"sourceNodeId":"fallback","targetNodeId":"end"}
                  ]
                }
                """;
        stubFlow(5L, graph);
        when(knowledgeService.retrieve(any(), anyString(), anyInt())).thenReturn(List.of());

        FlowDebugResponse start = service.start(5L, new FlowDebugStartRequest());
        FlowDebugInputRequest input = new FlowDebugInputRequest();
        input.setInput("奇怪的问题");
        FlowDebugResponse response = service.input(start.getSessionId(), input);

        assertThat(response.getPrompts()).contains("暂无相关信息");
        assertThat(response.getVariables()).doesNotContainKey("ragAnswer");
    }

    @Test
    void asr_node_enters_waiting_then_input_advances() {
        String graph = """
                {
                  "nodes": [
                    {"id":"start","type":"circle","properties":{"bizType":"start","name":"start"}},
                    {"id":"ask","type":"rect","properties":{"bizType":"asr","name":"ask","prompt":"请说出您的问题"}},
                    {"id":"echo","type":"rect","properties":{"bizType":"play","name":"echo","ttsText":"您说的是 ${lastAsr}"}},
                    {"id":"end","type":"circle","properties":{"bizType":"end","name":"end"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"ask"},
                    {"sourceNodeId":"ask","targetNodeId":"echo"},
                    {"sourceNodeId":"echo","targetNodeId":"end"}
                  ]
                }
                """;
        stubFlow(6L, graph);

        FlowDebugResponse start = service.start(6L, new FlowDebugStartRequest());
        assertThat(start.getStatus()).isEqualTo("waiting");
        assertThat(start.getWaitingFor()).isEqualTo("asr");
        assertThat(start.getPrompts()).contains("请说出您的问题");

        FlowDebugInputRequest input = new FlowDebugInputRequest();
        input.setInput("你好");
        FlowDebugResponse response = service.input(start.getSessionId(), input);

        assertThat(response.getStatus()).isEqualTo("ended");
        assertThat(response.getVariables()).containsEntry("lastAsr", "你好");
        assertThat(response.getPrompts()).contains("您说的是 你好");
    }
}
