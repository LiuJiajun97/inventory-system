package com.company.inventory.mapper;

import com.company.inventory.model.entity.price.PriceListItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 价目表行 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper,不手写 SQL,V26)。
 *
 * @author inventory
 */
@Mapper
public interface PriceListItemMapper extends BaseMapper<PriceListItemDO> {
}
