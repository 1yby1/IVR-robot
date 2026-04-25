package com.ivr.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ivr.admin.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT r.*
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.sort ASC, r.id ASC
            """)
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);
}
