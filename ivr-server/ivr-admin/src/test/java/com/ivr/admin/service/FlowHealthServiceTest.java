package com.ivr.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.FlowHealthResponse;
import com.ivr.admin.entity.CallEvent;
import com.ivr.admin.entity.CallLog;
import com.ivr.admin.mapper.CallEventMapper;
import com.ivr.admin.mapper.CallLogMapper;
import com.ivr.engine.graph.FlowGraphParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowHealthServiceTest {

    private final FlowStore flowStore = mock(FlowStore.class);
    private final CallLogMapper callLogMapper = mock(CallLogMapper.class);
    private final CallEventMapper callEventMapper = mock(CallEventMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlowHealthService service = new FlowHealthService(
            flowStore,
            new FlowGraphParser(objectMapper),
            callLogMapper,
            callEventMapper,
            objectMapper
    );

    @Test
    void check_combinesStructureAndNodeRuntimeStats() {
        String graphJson = """
                {
                  "nodes": [
                    {"id":"start","type":"circle","text":"开始","properties":{"bizType":"start","name":"开始"}},
                    {"id":"ask","type":"rect","text":"提问","properties":{"bizType":"asr","name":"提问","prompt":"请说出您的问题"}},
                    {"id":"rag","type":"rect","text":"知识库问答","properties":{"bizType":"rag","name":"知识库问答","topK":3,"fallbackBranch":"fallback"}},
                    {"id":"end","type":"circle","text":"结束","properties":{"bizType":"end","name":"结束"}},
                    {"id":"transfer","type":"rect","text":"转人工","properties":{"bizType":"transfer","name":"转人工","target":"1000"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"ask"},
                    {"sourceNodeId":"ask","targetNodeId":"rag"},
                    {"sourceNodeId":"rag","targetNodeId":"end"},
                    {"sourceNodeId":"rag","targetNodeId":"transfer","text":"fallback","properties":{"key":"fallback"}}
                  ]
                }
                """;
        when(flowStore.detail(7L)).thenReturn(Map.of(
                "id", 7L,
                "flowName", "售后问答",
                "graphJson", graphJson
        ));
        when(flowStore.validateGraphJson(graphJson)).thenReturn(List.of());
        when(callLogMapper.selectList(any())).thenReturn(List.of(
                callLog("call-1", "normal", 12),
                callLog("call-2", "transfer", 18)
        ));
        when(callEventMapper.selectList(any())).thenReturn(List.of(
                event("call-1", "rag", "rag", "enter", "{\"name\":\"知识库问答\"}"),
                event("call-1", "rag", "rag", "rag", "{\"status\":\"ok\",\"hits\":2}"),
                event("call-2", "rag", "rag", "enter", "{\"name\":\"知识库问答\"}"),
                event("call-2", "rag", "rag", "rag", "{\"status\":\"no_hits\"}"),
                event("call-2", "transfer", "transfer", "enter", "{\"name\":\"转人工\"}")
        ));

        FlowHealthResponse response = service.check(7L);

        FlowHealthResponse.NodeStat rag = response.getNodes().stream()
                .filter(node -> "rag".equals(node.getNodeId()))
                .findFirst()
                .orElseThrow();
        FlowHealthResponse.NodeStat transfer = response.getNodes().stream()
                .filter(node -> "transfer".equals(node.getNodeId()))
                .findFirst()
                .orElseThrow();

        assertThat(response.getFlowName()).isEqualTo("售后问答");
        assertThat(response.getRuntimeStats().getSampleCalls()).isEqualTo(2);
        assertThat(response.getRuntimeStats().getTransferCalls()).isEqualTo(1);
        assertThat(rag.getEnterCount()).isEqualTo(2);
        assertThat(rag.getAiHitCount()).isEqualTo(1);
        assertThat(rag.getFallbackCount()).isEqualTo(1);
        assertThat(rag.getSuccessRate()).isEqualTo(0.5);
        assertThat(rag.getStatusCounts()).containsEntry("ok", 1).containsEntry("no_hits", 1);
        assertThat(transfer.getTransferCount()).isEqualTo(1);
        assertThat(response.getIssues()).anyMatch(issue -> issue.getMessage().contains("通话样本较少"));
        assertThat(response.getDiagnoses()).anyMatch(item -> "rag_no_hits".equals(item.getRootCause()));
        assertThat(response.getPaths()).anyMatch(path -> path.getPathText().contains("知识库问答"));
    }

    private CallLog callLog(String callUuid, String endReason, int duration) {
        CallLog log = new CallLog();
        log.setCallUuid(callUuid);
        log.setFlowId(7L);
        log.setStartTime(LocalDateTime.now());
        log.setEndTime(LocalDateTime.now());
        log.setEndReason(endReason);
        log.setDuration(duration);
        return log;
    }

    private CallEvent event(String callUuid, String nodeKey, String nodeType, String eventType, String payload) {
        CallEvent event = new CallEvent();
        event.setCallUuid(callUuid);
        event.setNodeKey(nodeKey);
        event.setNodeType(nodeType);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
