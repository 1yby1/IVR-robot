package com.ivr.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivr.admin.dto.FlowDebugInputRequest;
import com.ivr.admin.dto.FlowDebugResponse;
import com.ivr.admin.dto.FlowDebugStartRequest;
import com.ivr.ai.LlmService;
import com.ivr.ai.rag.KnowledgeService;
import com.ivr.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FlowDebugService {

    private static final Logger log = LoggerFactory.getLogger(FlowDebugService.class);
    private static final int MAX_STEPS = 50;
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String DEFAULT_RAG_TEMPLATE = """
            你是客服助手，必须严格依据下方资料回答客户问题。资料中没有答案时直接回答「抱歉，这个问题需要人工帮您处理，正在为您转接」。
            资料：
            {context}

            客户问题：{question}
            请用 80 字以内、口语化的中文回答，不要重复「资料」二字。
            """;

    private final FlowStore flowStore;
    private final ObjectMapper objectMapper;
    private final CallRecordService callRecordService;
    private final LlmService llmService;
    private final KnowledgeService knowledgeService;
    private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();

    public FlowDebugService(FlowStore flowStore,
                            ObjectMapper objectMapper,
                            CallRecordService callRecordService,
                            LlmService llmService,
                            KnowledgeService knowledgeService) {
        this.flowStore = flowStore;
        this.objectMapper = objectMapper;
        this.callRecordService = callRecordService;
        this.llmService = llmService;
        this.knowledgeService = knowledgeService;
    }

    public FlowDebugResponse start(Long flowId, FlowDebugStartRequest request) {
        Map<String, Object> detail = flowStore.detail(flowId);
        Graph graph = parseGraph(Objects.toString(detail.get("graphJson"), ""));
        String startNodeId = graph.nodes.values().stream()
                .filter(node -> "start".equals(node.bizType()))
                .map(node -> node.id)
                .findFirst()
                .orElseGet(() -> graph.nodes.keySet().stream().findFirst()
                        .orElseThrow(() -> new BusinessException(400, "流程没有可执行节点")));

        DebugSession session = new DebugSession();
        session.sessionId = UUID.randomUUID().toString();
        session.flowId = flowId;
        session.flowVersion = numberValue(detail.get("currentVersion"));
        session.flowName = Objects.toString(detail.get("flowName"), "未命名流程");
        session.graph = graph;
        session.variables.put("caller", defaultText(request == null ? null : request.getCaller(), "13800000000"));
        session.variables.put("callee", defaultText(request == null ? null : request.getCallee(), "4001"));
        sessions.put(session.sessionId, session);
        callRecordService.startCall(
                session.sessionId,
                session.variables.get("caller"),
                session.variables.get("callee"),
                session.flowId,
                session.flowVersion
        );

        List<String> prompts = new ArrayList<>();
        List<String> events = new ArrayList<>();
        events.add("模拟呼叫进入流程：" + session.flowName);
        return advance(session, startNodeId, prompts, events);
    }

    public FlowDebugResponse input(String sessionId, FlowDebugInputRequest request) {
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException(404, "模拟通话不存在或已过期");
        }
        if ("ended".equals(session.status)) {
            throw new BusinessException(400, "模拟通话已结束");
        }
        Node node = session.graph.nodes.get(session.currentNodeId);
        if (node == null) {
            throw new BusinessException(400, "当前节点不存在");
        }
        String bizType = node.bizType();
        String input = request.getInput().trim();
        List<String> prompts = new ArrayList<>();
        List<String> events = new ArrayList<>();

        if ("dtmf".equals(bizType)) {
            session.variables.put("lastInput", input);
            session.variables.put("lastDtmf", input);
            events.add("用户输入：" + input);
            record(session, node, "dtmf", Map.of("input", input));

            if (outgoing(session.graph, node.id).isEmpty()) {
                events.add("按键节点没有后续连线，流程结束");
                return finish(session, "当前按键节点没有配置后续分支", prompts, events);
            }
            String nextNodeId = resolveDtmfNext(node, session.graph, input);
            if (!StringUtils.hasText(nextNodeId)) {
                events.add("按键未匹配，流程结束");
                return finish(session, "未匹配到按键分支", prompts, events);
            }
            return advance(session, nextNodeId, prompts, events);
        }

        if ("asr".equals(bizType)) {
            session.variables.put("lastAsr", input);
            session.variables.put("lastInput", input);
            events.add("用户语音文本：" + input);
            record(session, node, "asr", Map.of("text", input));
            String nextNodeId = firstNext(session.graph, node.id);
            if (!StringUtils.hasText(nextNodeId)) {
                return finish(session, "ASR 节点没有后续节点", prompts, events);
            }
            return advance(session, nextNodeId, prompts, events);
        }

        throw new BusinessException(400, "当前节点不需要输入");
    }

    private FlowDebugResponse advance(DebugSession session,
                                      String nodeId,
                                      List<String> prompts,
                                      List<String> events) {
        String currentNodeId = nodeId;
        for (int i = 0; i < MAX_STEPS; i++) {
            Node node = session.graph.nodes.get(currentNodeId);
            if (node == null) {
                return finish(session, "节点不存在：" + currentNodeId, prompts, events);
            }

            session.currentNodeId = node.id;
            session.status = "running";
            session.waitingFor = null;
            rememberVisitedNode(session, node.id);
            events.add("进入节点：" + node.name());
            record(session, node, "enter", Map.of("name", node.name(), "bizType", node.bizType()));

            String bizType = node.bizType();
            if ("start".equals(bizType)) {
                currentNodeId = firstNext(session.graph, node.id);
                if (!StringUtils.hasText(currentNodeId)) {
                    return finish(session, "开始节点没有后续节点", prompts, events);
                }
                continue;
            }
            if ("play".equals(bizType)) {
                String prompt = renderVariables(promptText(node, "播放语音"), session.variables);
                prompts.add(prompt);
                record(session, node, "prompt", Map.of("text", prompt));
                currentNodeId = firstNext(session.graph, node.id);
                if (!StringUtils.hasText(currentNodeId)) {
                    return finish(session, "播放完成，流程结束", prompts, events);
                }
                continue;
            }
            if ("dtmf".equals(bizType)) {
                session.status = "waiting";
                session.waitingFor = "dtmf";
                String prompt = renderVariables(promptText(node, "请输入按键"), session.variables);
                prompts.add(prompt);
                record(session, node, "prompt", Map.of("text", prompt));
                events.add("等待用户按键");
                return buildResponse(session, prompts, events);
            }
            if ("asr".equals(bizType)) {
                session.status = "waiting";
                session.waitingFor = "asr";
                String prompt = node.stringProp("prompt", "");
                if (StringUtils.hasText(prompt)) {
                    String rendered = renderVariables(prompt, session.variables);
                    prompts.add(rendered);
                    record(session, node, "prompt", Map.of("text", rendered));
                }
                events.add("等待用户语音文本输入（请在输入框中输入要识别的文字）");
                return buildResponse(session, prompts, events);
            }
            if ("condition".equals(bizType)) {
                String nextId = handleCondition(node, session, events);
                if (!StringUtils.hasText(nextId)) {
                    return finish(session, "条件节点未匹配到后续分支", prompts, events);
                }
                currentNodeId = nextId;
                continue;
            }
            if ("var_assign".equals(bizType)) {
                currentNodeId = handleVarAssign(node, session, events);
                if (!StringUtils.hasText(currentNodeId)) {
                    return finish(session, "变量赋值节点没有后续节点", prompts, events);
                }
                continue;
            }
            if ("http".equals(bizType)) {
                String nextId = handleHttp(node, session, events);
                if (!StringUtils.hasText(nextId)) {
                    return finish(session, "HTTP 节点没有后续节点", prompts, events);
                }
                currentNodeId = nextId;
                continue;
            }
            if ("intent".equals(bizType)) {
                String nextId = handleIntent(node, session, events);
                if (!StringUtils.hasText(nextId)) {
                    return finish(session, "意图节点未配置匹配分支", prompts, events);
                }
                currentNodeId = nextId;
                continue;
            }
            if ("rag".equals(bizType)) {
                String nextId = handleRag(node, session, events);
                if (!StringUtils.hasText(nextId)) {
                    return finish(session, "知识库节点没有后续节点", prompts, events);
                }
                currentNodeId = nextId;
                continue;
            }
            if ("transfer".equals(bizType)) {
                return finish(session, "转人工：" + node.stringProp("target", "1000"), prompts, events);
            }
            if ("voicemail".equals(bizType)) {
                return finish(session, "进入留言流程", prompts, events);
            }
            if ("end".equals(bizType)) {
                return finish(session, "流程正常结束", prompts, events);
            }

            events.add("模拟执行：" + node.name());
            currentNodeId = firstNext(session.graph, node.id);
            if (!StringUtils.hasText(currentNodeId)) {
                return finish(session, node.name() + "执行完成，流程结束", prompts, events);
            }
        }
        return finish(session, "执行步数超过上限，请检查流程是否存在循环", prompts, events);
    }

    /**
     * 条件节点：计算表达式，表达式结果作为分支 key。
     */
    private String handleCondition(Node node, DebugSession session, List<String> events) {
        String expression = node.stringProp("expression", "");
        if (!StringUtils.hasText(expression)) {
            events.add("条件节点未配置表达式，走默认分支");
            record(session, node, "condition", Map.of("status", "skipped", "reason", "empty_expression"));
            return resolveBranch(session.graph, node.id, "default");
        }
        try {
            StandardEvaluationContext ec = new StandardEvaluationContext();
            ec.setVariable("vars", session.variables);
            ec.setVariable("caller", session.variables.get("caller"));
            ec.setVariable("callee", session.variables.get("callee"));
            ec.setVariable("lastDtmf", session.variables.get("lastDtmf"));
            ec.setVariable("lastAsr", session.variables.get("lastAsr"));
            ec.setVariable("lastInput", session.variables.get("lastInput"));
            ec.setRootObject(new ConditionRoot(session.variables));
            Expression expr = EXPRESSION_PARSER.parseExpression(expression);
            Object value = expr.getValue(ec);
            String branch = value == null ? "default" : value.toString();
            events.add("条件判断命中：" + branch);
            record(session, node, "condition", Map.of("expression", expression, "branch", branch));
            return resolveBranch(session.graph, node.id, branch);
        } catch (Exception e) {
            log.warn("[Debug] condition eval failed expr={} err={}", expression, e.toString());
            events.add("条件判断失败，流程结束");
            record(session, node, "condition", Map.of("status", "failed", "error", diagnostic(e)));
            return "";
        }
    }

    /**
     * 变量赋值节点：把渲染后的文本写入 session.variables。
     */
    private String handleVarAssign(Node node, DebugSession session, List<String> events) {
        String varName = node.stringProp("varName", "").trim();
        if (!StringUtils.hasText(varName)) {
            events.add("变量赋值失败：变量名为空");
            record(session, node, "var_assign", Map.of("status", "failed", "reason", "empty_var_name"));
            return "";
        }
        String value = renderVariables(node.stringProp("value", ""), session.variables);
        session.variables.put(varName, value);
        events.add("变量赋值：" + varName + " = " + abbreviate(value, 80));
        record(session, node, "var_assign", Map.of("varName", varName, "value", value));
        return firstNext(session.graph, node.id);
    }

    /**
     * HTTP 节点：调外部接口，2xx 走默认分支，失败或非 2xx 走 fallback 分支。
     */
    private String handleHttp(Node node, DebugSession session, List<String> events) {
        String fallback = node.stringProp("fallbackBranch", "fallback");
        String url = renderVariables(node.stringProp("url", ""), session.variables);
        if (!StringUtils.hasText(url)) {
            events.add("HTTP 调用跳过：URL 为空，走 fallback：" + fallback);
            record(session, node, "http", Map.of("status", "skipped", "reason", "empty_url"));
            return resolveBranch(session.graph, node.id, fallback);
        }

        String method = node.stringProp("method", "GET").trim().toUpperCase();
        String body = renderVariables(node.stringProp("bodyTemplate", ""), session.variables);
        String responseVar = node.stringProp("responseVar", "httpResponse");
        String statusVar = node.stringProp("statusVar", "httpStatus");
        int timeoutSec = Math.max(1, node.intProp("timeoutSec", 5));

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSec));
            if (StringUtils.hasText(body) && !hasHttpBody(method)) {
                method = "POST";
            }
            if (hasHttpBody(method)) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String responseBody = Objects.toString(response.body(), "");
            session.variables.put(responseVar, responseBody);
            session.variables.put(statusVar, String.valueOf(response.statusCode()));
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            events.add("HTTP 调用完成：" + response.statusCode() + "，响应写入 " + responseVar);
            record(session, node, "http", Map.of(
                    "status", ok ? "ok" : "non_2xx",
                    "httpStatus", response.statusCode(),
                    "responseVar", responseVar,
                    "responsePreview", abbreviate(responseBody, 200)
            ));
            return resolveBranch(session.graph, node.id, ok ? null : fallback);
        } catch (Exception e) {
            session.variables.put(statusVar, "error");
            session.variables.put(responseVar, diagnostic(e));
            events.add("HTTP 调用失败，走 fallback：" + fallback);
            record(session, node, "http", Map.of("status", "failed", "error", diagnostic(e)));
            return resolveBranch(session.graph, node.id, fallback);
        }
    }

    private boolean hasHttpBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /**
     * 意图节点：调 LLM 做 zero-shot 分类，命中 → 走对应分支；未命中 → fallback 分支。
     * 返回下一节点 id，找不到匹配的分支返回空字符串。
     */
    private String handleIntent(Node node, DebugSession session, List<String> events) {
        String inputVarName = node.stringProp("inputVar", "lastAsr");
        String text = resolveIntentInput(session, inputVarName);
        List<String> intents = readIntentsArray(node);
        String fallback = node.stringProp("fallbackBranch", "other");

        String hit;
        if (!StringUtils.hasText(text)) {
            events.add("意图识别跳过：输入变量 " + inputVarName + " 为空");
            hit = fallback;
        } else if (intents.isEmpty()) {
            events.add("意图识别跳过：未配置候选意图");
            hit = fallback;
        } else {
            try {
                hit = llmService.detectIntent(text, intents);
                if (!StringUtils.hasText(hit) || "other".equals(hit)) {
                    hit = fallback;
                }
                events.add("意图识别命中：" + hit + "（输入：" + text + "）");
            } catch (Exception e) {
                log.warn("[Debug] intent llm failed text=\"{}\" err={}", text, e.toString());
                events.add("意图识别失败，使用 fallback：" + fallback);
                hit = fallback;
            }
        }

        session.variables.put("lastIntent", hit);
        record(session, node, "intent", Map.of("input", text, "hit", hit));
        return resolveBranch(session.graph, node.id, hit);
    }

    /**
     * RAG 节点：检索 → LLM 生成 → 把答案塞进 variables[answerVar]；失败走 fallback 分支。
     */
    private String handleRag(Node node, DebugSession session, List<String> events) {
        String questionVar = node.stringProp("questionVar", "lastAsr");
        String answerVar = node.stringProp("answerVar", "ragAnswer");
        String fallback = node.stringProp("fallbackBranch", "fallback");
        int topK = node.intProp("topK", 3);
        Long kbId = node.longProp("kbId");
        String question = Objects.requireNonNullElse(session.variables.get(questionVar), "").trim();

        if (!StringUtils.hasText(question)) {
            events.add("RAG 跳过：问题变量 " + questionVar + " 为空");
            record(session, node, "rag", Map.of("status", "skipped", "reason", "empty_question"));
            return resolveBranch(session.graph, node.id, fallback);
        }
        List<KnowledgeService.KnowledgeChunk> chunks;
        try {
            chunks = knowledgeService.retrieve(kbId, question, topK);
        } catch (Exception e) {
            log.warn("[Debug] rag retrieve failed kb={} err={}", kbId, e.toString());
            events.add("知识库检索失败，走 fallback：" + fallback);
            record(session, node, "rag", Map.of("status", "retrieve_failed"));
            return resolveBranch(session.graph, node.id, fallback);
        }
        if (chunks == null || chunks.isEmpty()) {
            events.add("知识库未命中，走 fallback：" + fallback);
            record(session, node, "rag", Map.of("status", "no_hits"));
            return resolveBranch(session.graph, node.id, fallback);
        }

        String context = chunks.stream()
                .map(c -> "- " + c.title() + "：" + c.content())
                .collect(Collectors.joining("\n"));
        String tpl = node.stringProp("promptTemplate", DEFAULT_RAG_TEMPLATE);

        String answer;
        try {
            answer = llmService.chatTemplate(tpl, Map.of(
                    "context", context,
                    "question", question
            ));
        } catch (Exception e) {
            String error = diagnostic(e);
            log.warn("[Debug] rag llm failed {}", error, e);
            events.add("生成失败，走 fallback：" + fallback + "（详情见通话事件 payload.error）");
            record(session, node, "rag", Map.of("status", "llm_failed", "error", error));
            return resolveBranch(session.graph, node.id, fallback);
        }
        if (!StringUtils.hasText(answer)) {
            events.add("生成结果为空，走 fallback：" + fallback);
            return resolveBranch(session.graph, node.id, fallback);
        }

        String trimmed = answer.trim();
        session.variables.put(answerVar, trimmed);
        session.variables.put("ragHits", String.valueOf(chunks.size()));
        events.add("知识库命中 " + chunks.size() + " 段，已生成回答（" + answerVar + "）");
        record(session, node, "rag", Map.of(
                "status", "ok",
                "hits", chunks.size(),
                "answer", trimmed
        ));
        return resolveBranch(session.graph, node.id, null);
    }

    private String resolveIntentInput(DebugSession session, String varName) {
        String v = session.variables.get(varName);
        if (StringUtils.hasText(v)) {
            return v;
        }
        for (String fallback : List.of("lastAsr", "lastInput", "lastDtmf")) {
            String fv = session.variables.get(fallback);
            if (StringUtils.hasText(fv)) {
                return fv;
            }
        }
        return "";
    }

    private List<String> readIntentsArray(Node node) {
        Object value = node.properties.get("intents");
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                String s = item.toString().trim();
                if (StringUtils.hasText(s)) {
                    result.add(s);
                }
            }
            return result;
        }
        if (value instanceof String s) {
            List<String> result = new ArrayList<>();
            for (String part : s.split("[,，\\n]")) {
                String t = part.trim();
                if (StringUtils.hasText(t)) {
                    result.add(t);
                }
            }
            return result;
        }
        return List.of();
    }

    /**
     * 通用分支查找：按 branchKey 匹配出边的 key/text；找不到取无 key 的默认边。
     */
    private String resolveBranch(Graph graph, String nodeId, String branchKey) {
        List<Edge> outgoing = outgoing(graph, nodeId);
        if (outgoing.isEmpty()) {
            return "";
        }
        if (StringUtils.hasText(branchKey)) {
            for (Edge edge : outgoing) {
                if (branchKey.equals(edge.branchKey())) {
                    return edge.targetNodeId;
                }
            }
        }
        for (Edge edge : outgoing) {
            if (!StringUtils.hasText(edge.branchKey())) {
                return edge.targetNodeId;
            }
        }
        return outgoing.get(0).targetNodeId;
    }

    private FlowDebugResponse finish(DebugSession session,
                                     String result,
                                     List<String> prompts,
                                     List<String> events) {
        session.status = "ended";
        session.waitingFor = null;
        session.result = result;
        events.add(result);
        record(session, session.graph.nodes.get(session.currentNodeId), "exit", Map.of("result", result));
        callRecordService.finishCall(session.sessionId, endReason(result), transferTarget(result));
        return buildResponse(session, prompts, events);
    }

    private FlowDebugResponse buildResponse(DebugSession session,
                                            List<String> prompts,
                                            List<String> events) {
        Node current = session.graph.nodes.get(session.currentNodeId);
        FlowDebugResponse response = new FlowDebugResponse();
        response.setSessionId(session.sessionId);
        response.setFlowId(session.flowId);
        response.setFlowName(session.flowName);
        response.setCurrentNodeId(session.currentNodeId);
        response.setCurrentNodeName(current == null ? "" : current.name());
        response.setStatus(session.status);
        response.setWaitingFor(session.waitingFor);
        response.setResult(session.result);
        response.setPrompts(prompts);
        response.setEvents(events);
        response.setOptions("waiting".equals(session.status) && "dtmf".equals(session.waitingFor) && current != null
                ? options(current, session.graph)
                : List.of());
        response.setVariables(session.variables);
        response.setVisitedNodeIds(new ArrayList<>(session.visitedNodeIds));
        return response;
    }

    private void rememberVisitedNode(DebugSession session, String nodeId) {
        if (!StringUtils.hasText(nodeId)) {
            return;
        }
        if (session.visitedNodeIds.isEmpty()
                || !Objects.equals(session.visitedNodeIds.get(session.visitedNodeIds.size() - 1), nodeId)) {
            session.visitedNodeIds.add(nodeId);
        }
    }

    private Graph parseGraph(String graphJson) {
        try {
            Map<String, Object> raw = objectMapper.readValue(graphJson, new TypeReference<>() {
            });
            Graph graph = new Graph();
            for (Object item : listValue(raw.get("nodes"))) {
                Map<String, Object> nodeMap = mapValue(item);
                String id = Objects.toString(nodeMap.get("id"), "");
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                Node node = new Node();
                node.id = id;
                node.type = Objects.toString(nodeMap.get("type"), "");
                node.text = textValue(nodeMap.get("text"));
                node.properties = mapValue(nodeMap.get("properties"));
                graph.nodes.put(node.id, node);
            }
            for (Object item : listValue(raw.get("edges"))) {
                Map<String, Object> edgeMap = mapValue(item);
                Edge edge = new Edge();
                edge.sourceNodeId = Objects.toString(edgeMap.get("sourceNodeId"), "");
                edge.targetNodeId = Objects.toString(edgeMap.get("targetNodeId"), "");
                edge.text = textValue(edgeMap.get("text"));
                edge.properties = mapValue(edgeMap.get("properties"));
                if (StringUtils.hasText(edge.sourceNodeId) && StringUtils.hasText(edge.targetNodeId)) {
                    graph.edges.add(edge);
                }
            }
            return graph;
        } catch (Exception e) {
            throw new BusinessException(400, "流程图数据格式不正确");
        }
    }

    private String resolveDtmfNext(Node node, Graph graph, String input) {
        Object mappings = node.properties.get("mappings");
        for (Object item : listValue(mappings)) {
            Map<String, Object> mapping = mapValue(item);
            String key = Objects.toString(mapping.get("key"), "");
            String nextNode = Objects.toString(mapping.get("nextNode"), "");
            if (input.equals(key) && StringUtils.hasText(nextNode)) {
                return nextNode;
            }
        }

        List<Edge> outgoing = outgoing(graph, node.id);
        for (Edge edge : outgoing) {
            if (input.equals(edge.branchKey())) {
                return edge.targetNodeId;
            }
        }
        if (outgoing.size() == 1) {
            return outgoing.get(0).targetNodeId;
        }
        int optionIndex = parseOptionIndex(input);
        if (optionIndex >= 0 && optionIndex < outgoing.size()) {
            return outgoing.get(optionIndex).targetNodeId;
        }
        return "";
    }

    private List<Map<String, String>> options(Node node, Graph graph) {
        List<Edge> outgoing = outgoing(graph, node.id);
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < outgoing.size(); i++) {
            Edge edge = outgoing.get(i);
            String key = StringUtils.hasText(edge.branchKey()) ? edge.branchKey() : String.valueOf(i + 1);
            Node target = graph.nodes.get(edge.targetNodeId);
            result.add(Map.of(
                    "key", key,
                    "label", target == null ? edge.targetNodeId : target.name(),
                    "targetNodeId", edge.targetNodeId
            ));
        }
        return result;
    }

    private String firstNext(Graph graph, String nodeId) {
        return outgoing(graph, nodeId).stream()
                .map(edge -> edge.targetNodeId)
                .findFirst()
                .orElse("");
    }

    private List<Edge> outgoing(Graph graph, String nodeId) {
        return graph.edges.stream()
                .filter(edge -> nodeId.equals(edge.sourceNodeId))
                .sorted(Comparator.comparing(edge -> edge.targetNodeId))
                .toList();
    }

    private int parseOptionIndex(String input) {
        try {
            return Integer.parseInt(input) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String promptText(Node node, String fallback) {
        String text = node.stringProp("ttsText", "");
        if (!StringUtils.hasText(text)) {
            text = node.nestedStringProp("tts", "text");
        }
        if (!StringUtils.hasText(text)) {
            text = node.stringProp("prompt", "");
        }
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String renderVariables(String template, Map<String, String> vars) {
        if (!StringUtils.hasText(template) || vars == null || vars.isEmpty()) {
            return template;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vars.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Integer numberValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void record(DebugSession session, Node node, String eventType, Map<String, Object> payload) {
        callRecordService.recordEvent(
                session.sessionId,
                node == null ? "" : node.id,
                node == null ? "" : node.bizType(),
                eventType,
                payload
        );
    }

    private String diagnostic(Throwable e) {
        if (e == null) {
            return "";
        }
        String message = Objects.toString(e.getMessage(), "");
        String text = e.getClass().getSimpleName() + (StringUtils.hasText(message) ? ": " + message : "");
        text = text.replaceAll("(?i)(sk-[A-Za-z0-9_-]{6})[A-Za-z0-9_-]+", "$1***");
        text = text.replaceAll("(?i)(api[-_ ]?key\\s*[:=]\\s*)[^\\s,;}]+", "$1***");
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return Objects.toString(text, "");
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String endReason(String result) {
        if (result == null) {
            return "normal";
        }
        if (result.startsWith("转人工")) {
            return "transfer";
        }
        if (result.contains("超时") || result.contains("未匹配") || result.contains("没有")) {
            return "timeout";
        }
        if (result.contains("不存在") || result.contains("错误")) {
            return "error";
        }
        return "normal";
    }

    private String transferTarget(String result) {
        if (result != null && result.startsWith("转人工：")) {
            return result.substring("转人工：".length());
        }
        return "";
    }

    private String textValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("value");
            return text == null ? Objects.toString(map.get("text"), "") : Objects.toString(text, "");
        }
        return Objects.toString(value, "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private List<Object> listValue(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private static class DebugSession {
        private String sessionId;
        private Long flowId;
        private Integer flowVersion;
        private String flowName;
        private Graph graph;
        private String currentNodeId;
        private String status;
        private String waitingFor;
        private String result = "";
        private final Map<String, String> variables = new LinkedHashMap<>();
        private final List<String> visitedNodeIds = new ArrayList<>();
    }

    private static class Graph {
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final List<Edge> edges = new ArrayList<>();
    }

    private static class Node {
        private String id;
        private String type;
        private String text;
        private Map<String, Object> properties = new LinkedHashMap<>();

        private String bizType() {
            String value = stringProp("bizType", "");
            return StringUtils.hasText(value) ? value : type;
        }

        private String name() {
            String value = stringProp("name", "");
            if (StringUtils.hasText(value)) {
                return value;
            }
            return StringUtils.hasText(text) ? text : id;
        }

        private String stringProp(String key, String fallback) {
            Object value = properties.get(key);
            String textValue = value == null ? "" : Objects.toString(value, "");
            return StringUtils.hasText(textValue) ? textValue : fallback;
        }

        @SuppressWarnings("unchecked")
        private String nestedStringProp(String objectKey, String valueKey) {
            Object value = properties.get(objectKey);
            if (!(value instanceof Map<?, ?> map)) {
                return "";
            }
            Object nestedValue = ((Map<String, Object>) map).get(valueKey);
            return nestedValue == null ? "" : Objects.toString(nestedValue, "");
        }

        private int intProp(String key, int fallback) {
            Object value = properties.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(Objects.toString(value, "").trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private Long longProp(String key) {
            Object value = properties.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                String text = Objects.toString(value, "");
                return StringUtils.hasText(text) ? Long.parseLong(text.trim()) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static class Edge {
        private String sourceNodeId;
        private String targetNodeId;
        private String text;
        private Map<String, Object> properties = new LinkedHashMap<>();

        private String branchKey() {
            for (String key : List.of("key", "dtmf", "digit", "value")) {
                Object value = properties.get(key);
                if (value != null && StringUtils.hasText(Objects.toString(value, ""))) {
                    return Objects.toString(value, "");
                }
            }
            return StringUtils.hasText(text) ? text : "";
        }
    }

    private static class ConditionRoot {
        private final Map<String, String> vars;

        private ConditionRoot(Map<String, String> vars) {
            this.vars = vars == null ? Map.of() : vars;
        }

        public Map<String, String> getVars() {
            return vars;
        }

        public String getCaller() {
            return vars.get("caller");
        }

        public String getCallee() {
            return vars.get("callee");
        }

        public String getLastDtmf() {
            return vars.get("lastDtmf");
        }

        public String getLastAsr() {
            return vars.get("lastAsr");
        }

        public String getLastInput() {
            return vars.get("lastInput");
        }
    }
}
