package com.ivr.engine.node;

import java.util.HashMap;
import java.util.Map;

public class FlowContext {

    private String sessionId;
    private String callUuid;
    private String caller;
    private String callee;
    private Long flowId;
    private Integer flowVersion;
    private Map<String, Object> vars = new HashMap<>();
    private String lastDtmf;
    private String lastAsr;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getCallUuid() { return callUuid; }
    public void setCallUuid(String callUuid) { this.callUuid = callUuid; }
    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }
    public String getCallee() { return callee; }
    public void setCallee(String callee) { this.callee = callee; }
    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public Integer getFlowVersion() { return flowVersion; }
    public void setFlowVersion(Integer flowVersion) { this.flowVersion = flowVersion; }
    public Map<String, Object> getVars() { return vars; }
    public void setVars(Map<String, Object> vars) { this.vars = vars; }
    public String getLastDtmf() { return lastDtmf; }
    public void setLastDtmf(String lastDtmf) { this.lastDtmf = lastDtmf; }
    public String getLastAsr() { return lastAsr; }
    public void setLastAsr(String lastAsr) { this.lastAsr = lastAsr; }

    public void setVar(String key, Object value) {
        vars.put(key, value);
    }

    public Object getVar(String key) {
        return vars.get(key);
    }
}
