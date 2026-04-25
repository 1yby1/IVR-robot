package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ivr.admin.entity.SysRole;
import com.ivr.admin.entity.SysUserRole;
import com.ivr.admin.mapper.SysMenuMapper;
import com.ivr.admin.mapper.SysRoleMapper;
import com.ivr.admin.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class PermissionService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    public PermissionService(SysUserRoleMapper userRoleMapper,
                             SysRoleMapper roleMapper,
                             SysMenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
    }

    public UserPermission getUserPermission(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        List<SysRole> roles = roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> Objects.equals(role.getStatus(), 1))
                .toList();

        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        if (roleCodes.contains("admin")) {
            return new UserPermission(roleCodes, List.of("*"));
        }

        List<Long> enabledRoleIds = roles.stream().map(SysRole::getId).toList();
        Set<String> perms = new LinkedHashSet<>();
        if (!enabledRoleIds.isEmpty()) {
            perms.addAll(menuMapper.selectPermsByRoleIds(enabledRoleIds));
        }
        return new UserPermission(roleCodes, new ArrayList<>(perms));
    }

    public boolean hasPerm(Long userId, String perm) {
        UserPermission permission = getUserPermission(userId);
        return permission.perms().contains("*") || permission.perms().contains(perm);
    }

    public record UserPermission(List<String> roles, List<String> perms) {
    }
}
