package com.ivr.admin.dto;

import jakarta.validation.constraints.NotNull;

public class UserStatusRequest {

    @NotNull(message = "请选择账号状态")
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
