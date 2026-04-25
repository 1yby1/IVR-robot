package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ivr.admin.dto.PageResult;
import com.ivr.admin.dto.UserListItem;
import com.ivr.admin.entity.SysRole;
import com.ivr.admin.entity.SysUser;
import com.ivr.admin.entity.SysUserRole;
import com.ivr.admin.mapper.SysRoleMapper;
import com.ivr.admin.mapper.SysUserMapper;
import com.ivr.admin.mapper.SysUserRoleMapper;
import com.ivr.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class UserAdminService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(SysUserMapper userMapper,
                            SysRoleMapper roleMapper,
                            SysUserRoleMapper userRoleMapper,
                            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<UserListItem> page(int current, int size, String keyword) {
        int safeCurrent = Math.max(current, 1);
        int safeSize = Math.max(size, 1);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreatedAt)
                .orderByDesc(SysUser::getId);
        if (StringUtils.hasText(normalizedKeyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, normalizedKeyword)
                    .or()
                    .like(SysUser::getNickname, normalizedKeyword)
                    .or()
                    .like(SysUser::getEmail, normalizedKeyword));
        }

        Page<SysUser> page = userMapper.selectPage(Page.of(safeCurrent, safeSize), wrapper);
        PageResult<UserListItem> result = new PageResult<>();
        result.setRecords(page.getRecords().stream().map(this::toListItem).toList());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status, Long currentUserId) {
        if (!Objects.equals(status, 0) && !Objects.equals(status, 1)) {
            throw new BusinessException(400, "账号状态不正确");
        }
        if (Objects.equals(userId, currentUserId) && Objects.equals(status, 0)) {
            throw new BusinessException(400, "不能停用当前登录账号");
        }

        SysUser user = getRequired(userId);
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String password) {
        SysUser user = getRequired(userId);
        user.setPassword(passwordEncoder.encode(password));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds, Long currentUserId) {
        if (Objects.equals(userId, currentUserId)) {
            throw new BusinessException(400, "不能修改当前登录账号的角色");
        }
        getRequired(userId);
        List<Long> singleRoleIds = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        if (singleRoleIds.size() != 1) {
            throw new BusinessException(400, "一个用户只能分配一个角色");
        }

        List<SysRole> roles = roleMapper.selectBatchIds(singleRoleIds);
        if (roles.size() != 1) {
            throw new BusinessException(400, "包含不存在的角色");
        }
        boolean hasDisabledRole = roles.stream().anyMatch(role -> !Objects.equals(role.getStatus(), 1));
        if (hasDisabledRole) {
            throw new BusinessException(400, "不能分配已停用角色");
        }

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(singleRoleIds.get(0));
        userRoleMapper.insert(userRole);
    }

    private SysUser getRequired(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || Objects.equals(user.getDeleted(), 1)) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private UserListItem toListItem(SysUser user) {
        List<String> roles = roleMapper.selectRolesByUserId(user.getId())
                .stream()
                .map(SysRole::getRoleCode)
                .toList();

        UserListItem item = new UserListItem();
        item.setId(user.getId());
        item.setUsername(user.getUsername());
        item.setNickname(user.getNickname());
        item.setEmail(user.getEmail());
        item.setAvatar(user.getAvatar());
        item.setStatus(user.getStatus());
        item.setRoles(roles);
        item.setCreatedAt(formatTime(user.getCreatedAt()));
        item.setLastLoginAt(formatTime(user.getLastLoginAt()));
        return item;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : TIME_FMT.format(time);
    }
}
