package com.company.inventory.mapper.rbac;

import com.company.inventory.model.entity.rbac.UserTablePrefDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表格偏好 Mapper:BaseMapper 简单 CRUD(按 user_id + page_key 查询/更新)。
 *
 * @author inventory
 */
@Mapper
public interface UserTablePrefMapper extends BaseMapper<UserTablePrefDO> {
}
