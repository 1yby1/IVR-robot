package com.ivr.admin.dto;

import java.util.List;

public class HotlineImpactResponse {

    private Long flowId;
    private String flowCode;
    private String flowName;
    private Integer flowStatus;
    private Integer currentVersion;
    private Integer nextVersion;
    private Integer hotlineCount;
    private Integer enabledHotlineCount;
    private List<HotlineRef> hotlines;

    public Long getFlowId() { return flowId; }
    public void setFlowId(Long flowId) { this.flowId = flowId; }
    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getFlowName() { return flowName; }
    public void setFlowName(String flowName) { this.flowName = flowName; }
    public Integer getFlowStatus() { return flowStatus; }
    public void setFlowStatus(Integer flowStatus) { this.flowStatus = flowStatus; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    public Integer getNextVersion() { return nextVersion; }
    public void setNextVersion(Integer nextVersion) { this.nextVersion = nextVersion; }
    public Integer getHotlineCount() { return hotlineCount; }
    public void setHotlineCount(Integer hotlineCount) { this.hotlineCount = hotlineCount; }
    public Integer getEnabledHotlineCount() { return enabledHotlineCount; }
    public void setEnabledHotlineCount(Integer enabledHotlineCount) { this.enabledHotlineCount = enabledHotlineCount; }
    public List<HotlineRef> getHotlines() { return hotlines; }
    public void setHotlines(List<HotlineRef> hotlines) { this.hotlines = hotlines; }

    public static class HotlineRef {
        private Long id;
        private String hotline;
        private Integer enabled;
        private String remark;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getHotline() { return hotline; }
        public void setHotline(String hotline) { this.hotline = hotline; }
        public Integer getEnabled() { return enabled; }
        public void setEnabled(Integer enabled) { this.enabled = enabled; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
