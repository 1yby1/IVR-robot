package com.ivr.admin.dto;

import jakarta.validation.constraints.NotNull;

public class RoleStatusRequest {

    @NotNull(message = "请选择角色状态")
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
