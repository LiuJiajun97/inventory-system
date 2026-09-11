package com.company.inventory.mapper;

import com.company.inventory.model.entity.adjust.StockAdjustDocItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 库存调整单行 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface StockAdjustDocItemMapper extends BaseMapper<StockAdjustDocItemDO> {
}
