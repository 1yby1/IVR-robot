package com.ivr.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_role_menu")
public class SysRoleMenu {

    private Long roleId;
    private Long menuId;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }
}
