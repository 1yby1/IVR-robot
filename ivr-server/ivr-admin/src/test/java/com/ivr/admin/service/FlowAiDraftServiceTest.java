package com.ivr.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.FlowAiGenerateRequest;
import com.ivr.admin.dto.FlowAiGenerateResponse;
import com.ivr.ai.flow.FlowAiGeneratorService;
import com.ivr.ai.flow.GeneratedFlow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowAiDraftServiceTest {

    private final FlowAiGeneratorService generatorService = mock(FlowAiGeneratorService.class);
    private final FlowStore flowStore = mock(FlowStore.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlowAiDraftService service = new FlowAiDraftService(generatorService, objectMapper, flowStore);

    @Test
    void generate_normalizesGraphAndReturnsValidationResult() throws Exception {
        String graphJson = """
                {
                  "nodes": [
                    {"id":"start","type":"circle","text":"开始","properties":{"bizType":"start"}},
                    {"id":"ask","type":"rect","text":"按键","properties":{"bizType":"dtmf"}},
                    {"id":"end","type":"circle","text":"结束","properties":{"bizType":"end"}}
                  ],
                  "edges": [
                    {"sourceNodeId":"start","targetNodeId":"ask"},
                    {"sourceNodeId":"ask","targetNodeId":"end","text":"1"}
                  ]
                }
                """;
        when(generatorService.generate(anyString())).thenReturn(new GeneratedFlow(
                graphJson,
                "生成了简单按键流程",
                List.of("请检查话术"),
                graphJson
        ));
        when(flowStore.validateGraphJson(anyString())).thenReturn(List.of());

        FlowAiGenerateRequest request = new FlowAiGenerateRequest();
        request.setRequirement("生成一个按 1 结束的流程");
        FlowAiGenerateResponse response = service.generate(request);

        Map<String, Object> graph = objectMapper.readValue(response.getGraphJson(), new TypeReference<>() {
        });
        List<?> nodes = (List<?>) graph.get("nodes");
        List<?> edges = (List<?>) graph.get("edges");
        Map<?, ?> dtmf = (Map<?, ?>) nodes.get(1);
        Map<?, ?> dtmfProps = (Map<?, ?>) dtmf.get("properties");
        Map<?, ?> branch = (Map<?, ?>) edges.get(1);
        Map<?, ?> branchProps = (Map<?, ?>) branch.get("properties");

        assertThat(response.getSummary()).isEqualTo("生成了简单按键流程");
        assertThat(response.getWarnings()).containsExactly("请检查话术");
        assertThat(response.getValidationErrors()).isEmpty();
        assertThat(dtmf.get("type")).isEqualTo("rect");
        assertThat(dtmfProps.get("maxDigits")).isEqualTo(1);
        assertThat(String.valueOf(branch.get("id"))).startsWith("edge-ask-end");
        assertThat(branchProps.get("key")).isEqualTo("1");
    }
}
