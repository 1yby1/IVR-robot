package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ivr.admin.dto.PageResult;
import com.ivr.admin.dto.RoleListItem;
import com.ivr.admin.entity.SysMenu;
import com.ivr.admin.entity.SysRole;
import com.ivr.admin.entity.SysRoleMenu;
import com.ivr.admin.mapper.SysMenuMapper;
import com.ivr.admin.mapper.SysRoleMapper;
import com.ivr.admin.mapper.SysRoleMenuMapper;
import com.ivr.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class RoleAdminService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public RoleAdminService(SysRoleMapper roleMapper,
                            SysMenuMapper menuMapper,
                            SysRoleMenuMapper roleMenuMapper) {
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    public PageResult<RoleListItem> page(int current, int size, String keyword) {
        int safeCurrent = Math.max(current, 1);
        int safeSize = Math.max(size, 1);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getSort)
                .orderByAsc(SysRole::getId);
        if (StringUtils.hasText(normalizedKeyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, normalizedKeyword)
                    .or()
                    .like(SysRole::getRoleName, normalizedKeyword)
                    .or()
                    .like(SysRole::getRemark, normalizedKeyword));
        }

        Page<SysRole> page = roleMapper.selectPage(Page.of(safeCurrent, safeSize), wrapper);
        PageResult<RoleListItem> result = new PageResult<>();
        result.setRecords(page.getRecords().stream().map(this::toListItem).toList());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    public List<RoleListItem> listEnabled() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getSort)
                        .orderByAsc(SysRole::getId))
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long roleId, Integer status) {
        if (!Objects.equals(status, 0) && !Objects.equals(status, 1)) {
            throw new BusinessException(400, "角色状态不正确");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || Objects.equals(role.getDeleted(), 1)) {
            throw new BusinessException(404, "角色不存在");
        }
        if ("admin".equals(role.getRoleCode()) && Objects.equals(status, 0)) {
            throw new BusinessException(400, "不能停用超级管理员角色");
        }
        role.setStatus(status);
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);
    }

    public List<Long> getMenuIds(Long roleId) {
        getRequired(roleId);
        return menuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        SysRole role = getRequired(roleId);
        if ("admin".equals(role.getRoleCode())) {
            throw new BusinessException(400, "超级管理员角色默认拥有所有权限，无需授权");
        }
        List<Long> distinctMenuIds = menuIds == null ? List.of() : menuIds.stream().distinct().toList();
        if (!distinctMenuIds.isEmpty()) {
            List<SysMenu> menus = menuMapper.selectBatchIds(distinctMenuIds);
            if (menus.size() != distinctMenuIds.size()) {
                throw new BusinessException(400, "包含不存在的菜单");
            }
        }

        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : distinctMenuIds) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
    }

    private SysRole getRequired(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || Objects.equals(role.getDeleted(), 1)) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
    }

    private RoleListItem toListItem(SysRole role) {
        RoleListItem item = new RoleListItem();
        item.setId(role.getId());
        item.setRoleCode(role.getRoleCode());
        item.setRoleName(role.getRoleName());
        item.setDataScope(role.getDataScope());
        item.setSort(role.getSort());
        item.setStatus(role.getStatus());
        item.setRemark(role.getRemark());
        item.setCreatedAt(role.getCreatedAt() == null ? "" : TIME_FMT.format(role.getCreatedAt()));
        return item;
    }
}
