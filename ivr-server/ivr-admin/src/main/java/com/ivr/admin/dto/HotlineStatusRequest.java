package com.ivr.admin.dto;

import jakarta.validation.constraints.NotNull;

public class HotlineStatusRequest {

    @NotNull(message = "请选择启用状态")
    private Integer enabled;

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
