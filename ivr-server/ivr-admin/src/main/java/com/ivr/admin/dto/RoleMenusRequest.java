package com.ivr.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class RoleMenusRequest {

    @NotNull(message = "请选择授权菜单")
    private List<Long> menuIds;

    public List<Long> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(List<Long> menuIds) {
        this.menuIds = menuIds;
    }
}
