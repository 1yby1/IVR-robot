package com.ivr.admin.dto;

import jakarta.validation.constraints.NotBlank;

public class FlowAiGenerateRequest {

    @NotBlank(message = "业务描述不能为空")
    private String requirement;
    private String flowName;

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public String getFlowName() {
        return flowName;
    }

    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }
}
