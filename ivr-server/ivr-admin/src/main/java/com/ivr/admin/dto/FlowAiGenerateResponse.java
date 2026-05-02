package com.ivr.admin.dto;

import java.util.List;

public class FlowAiGenerateResponse {

    private String graphJson;
    private String summary;
    private List<String> warnings;
    private List<String> validationErrors;

    public String getGraphJson() {
        return graphJson;
    }

    public void setGraphJson(String graphJson) {
        this.graphJson = graphJson;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
