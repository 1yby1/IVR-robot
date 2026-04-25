package com.ivr.admin.dto;

public class FlowOptionItem {

    private Long id;
    private String flowCode;
    private String flowName;
    private Integer currentVersion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
}
