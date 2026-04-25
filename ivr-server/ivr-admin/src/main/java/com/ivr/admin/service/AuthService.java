package com.ivr.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ivr.admin.dto.AuthResult;
import com.ivr.admin.dto.LoginRequest;
import com.ivr.admin.dto.RegisterRequest;
import com.ivr.admin.dto.UserInfoResponse;
import com.ivr.admin.entity.SysRole;
import com.ivr.admin.entity.SysUser;
import com.ivr.admin.entity.SysUserRole;
import com.ivr.admin.mapper.SysRoleMapper;
import com.ivr.admin.mapper.SysUserMapper;
import com.ivr.admin.mapper.SysUserRoleMapper;
import com.ivr.admin.security.JwtTokenProvider;
import com.ivr.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE_CODE = "operator";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(SysUserMapper userMapper,
                       SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper,
                       PermissionService permissionService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public AuthResult login(LoginRequest params) {
        SysUser user = findByUsername(params.getUsername());
        if (user == null || !passwordEncoder.matches(params.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        ensureEnabled(user);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return buildAuthResult(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthResult register(RegisterRequest params) {
        if (!Objects.equals(params.getPassword(), params.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的密码不一致");
        }
        if (findByUsername(params.getUsername()) != null) {
            throw new BusinessException(400, "账号已存在");
        }

        SysRole operatorRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, DEFAULT_ROLE_CODE)
                .eq(SysRole::getStatus, 1)
                .last("LIMIT 1"));
        if (operatorRole == null) {
            throw new BusinessException(500, "默认角色不存在，请初始化系统数据");
        }

        SysUser user = new SysUser();
        user.setUsername(params.getUsername());
        user.setPassword(passwordEncoder.encode(params.getPassword()));
        user.setNickname(params.getNickname());
        user.setEmail(params.getEmail());
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(operatorRole.getId());
        userRoleMapper.insert(userRole);

        return buildAuthResult(user);
    }

    public UserInfoResponse currentUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || Objects.equals(user.getDeleted(), 1)) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        ensureEnabled(user);
        return buildUserInfo(user);
    }

    private AuthResult buildAuthResult(SysUser user) {
        AuthResult result = new AuthResult();
        result.setToken(tokenProvider.createToken(user));
        result.setUserInfo(buildUserInfo(user));
        return result;
    }

    private UserInfoResponse buildUserInfo(SysUser user) {
        PermissionService.UserPermission permission = permissionService.getUserPermission(user.getId());

        UserInfoResponse userInfo = new UserInfoResponse();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        userInfo.setAvatar(user.getAvatar());
        userInfo.setRoles(permission.roles());
        userInfo.setPerms(permission.perms());
        return userInfo;
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }

    private void ensureEnabled(SysUser user) {
        if (!Objects.equals(user.getStatus(), 1)) {
            throw new BusinessException(403, "账号已停用，请联系管理员");
        }
    }
}
