package com.ivr.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ivr.admin.entity.SysMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select({
            "<script>",
            "SELECT DISTINCT m.perms",
            "FROM sys_role_menu rm",
            "JOIN sys_menu m ON m.id = rm.menu_id",
            "WHERE rm.role_id IN",
            "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>",
            "#{roleId}",
            "</foreach>",
            "AND m.perms IS NOT NULL",
            "AND m.perms != ''",
            "</script>"
    })
    List<String> selectPermsByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}
