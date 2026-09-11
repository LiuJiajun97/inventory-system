package com.company.inventory.mapper;

import com.company.inventory.entity.item.ItemDO;





import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 物品 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper,不手写 SQL)。
 *
 * @author inventory
 */
@Mapper
public interface ItemMapper extends BaseMapper<ItemDO> {
}
