package com.company.inventory.mapper;

import com.company.inventory.entity.adjust.StockAdjustDocDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 库存调整单表头 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface StockAdjustDocMapper extends BaseMapper<StockAdjustDocDO> {
}
