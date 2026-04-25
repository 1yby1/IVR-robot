package com.ivr.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UserRolesRequest {

    @NotEmpty(message = "请选择一个角色")
    @Size(min = 1, max = 1, message = "一个用户只能分配一个角色")
    private List<Long> roleIds;

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
