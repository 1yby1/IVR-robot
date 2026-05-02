package com.ivr.ai.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.ai.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FlowAiGeneratorService {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public GeneratedFlow generate(String requirement) {
        if (!StringUtils.hasText(requirement)) {
            throw new IllegalArgumentException("requirement is empty");
        }
        String rawText = llmService.chat(buildPrompt(requirement.trim()));
        Map<String, Object> payload = parseJsonPayload(rawText);
        Map<String, Object> graph = graphPayload(payload);
        return new GeneratedFlow(
                writeJson(graph),
                Objects.toString(payload.get("summary"), "已根据业务描述生成流程草稿"),
                stringList(payload.get("warnings")),
                rawText
        );
    }

    private String buildPrompt(String requirement) {
        return """
                你是 IVR 智能语音流程设计助手。请根据用户的业务描述，生成符合 LogicFlow 的 IVR 流程图 JSON。

                系统仅支持这些业务节点：
                start 开始节点；play 播放语音；dtmf 按键收集；asr 语音识别；intent AI 意图识别；
                rag AI 问答；condition 条件判断；var_assign 变量赋值；http HTTP 调用；
                transfer 转人工；voicemail 留言；end 结束节点。

                必须遵守：
                1. 只返回一个 JSON 对象，不要 Markdown，不要代码块。
                2. 返回格式固定为：
                   {
                     "summary": "一句话说明生成的流程",
                     "warnings": ["可选风险提示"],
                     "graph": { "nodes": [], "edges": [] }
                   }
                3. 每个节点必须包含 id、type、x、y、text、properties。
                4. properties.bizType 必须是上面支持的业务节点之一。
                5. start 和 end 节点 type 用 circle，其余节点 type 用 rect。
                6. 连线必须包含 id、type、sourceNodeId、targetNodeId、properties；连线 type 用 polyline。
                7. 分支标识写入 edge.properties.key，同时 edge.text 也写同样的值；默认成功分支可以不写 key。
                8. 流程必须且只能有一个 start，必须至少有 end、transfer 或 voicemail 之一。
                9. dtmf 的每条出边都必须有 key，例如 1、2、0。
                10. intent、rag、http 节点必须有 fallback/other 等失败兜底分支。
                11. 不要生成系统不支持的节点类型，不要生成解释性文字。

                用户业务描述：
                %s
                """.formatted(requirement);
    }

    private Map<String, Object> parseJsonPayload(String rawText) {
        String json = extractJsonObject(rawText);
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 返回内容不是合法 JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphPayload(Map<String, Object> payload) {
        Object graph = payload.get("graph");
        if (graph instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (payload.containsKey("nodes") && payload.containsKey("edges")) {
            return payload;
        }
        throw new IllegalArgumentException("AI 返回内容缺少 graph.nodes / graph.edges");
    }

    private String extractJsonObject(String rawText) {
        String text = Objects.toString(rawText, "").trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("AI 返回内容不包含 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private String writeJson(Map<String, Object> graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        } catch (Exception e) {
            throw new IllegalArgumentException("流程图 JSON 序列化失败", e);
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = Objects.toString(item, "").trim();
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }
}
