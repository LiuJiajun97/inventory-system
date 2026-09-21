package com.company.inventory.mapper.rbac;

import com.company.inventory.model.entity.rbac.RoleDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper(简单 CRUD 走 MyBatis-Plus BaseMapper,不手写 SQL)。
 *
 * @author inventory
 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {
}
