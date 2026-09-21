package com.company.inventory.mapper.rbac;

import com.company.inventory.model.entity.rbac.RoleMenuDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-菜单绑定 Mapper(联合主键,简单操作走 BaseMapper 条件构造器)。
 *
 * @author inventory
 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenuDO> {
}
