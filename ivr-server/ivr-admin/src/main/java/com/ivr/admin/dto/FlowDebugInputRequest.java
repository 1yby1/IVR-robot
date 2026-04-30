package com.ivr.admin.dto;

import jakarta.validation.constraints.NotBlank;

public class FlowDebugInputRequest {

    @NotBlank(message = "请输入按键或语音文本")
    private String input;

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }
}
