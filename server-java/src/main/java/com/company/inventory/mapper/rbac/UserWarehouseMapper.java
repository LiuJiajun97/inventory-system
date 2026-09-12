package com.company.inventory.mapper.rbac;

import com.company.inventory.model.entity.rbac.UserWarehouseDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-仓库授权 Mapper:BaseMapper 简单 CRUD + 按用户取授权仓库 ID。
 *
 * @author inventory
 */
@Mapper
public interface UserWarehouseMapper extends BaseMapper<UserWarehouseDO> {

    /**
     * 查询指定用户的授权仓库 ID 列表。
     *
     * @param userId 用户 ID
     * @return 仓库 ID 列表(可能为空)
     */
    @Select("SELECT warehouse_id FROM sys_user_warehouse WHERE user_id = #{userId}")
    List<Long> selectWarehouseIdsByUserId(@Param("userId") long userId);
}
