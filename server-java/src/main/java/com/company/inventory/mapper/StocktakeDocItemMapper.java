package com.company.inventory.mapper;

import com.company.inventory.entity.stocktake.StocktakeDocItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 盘点单行 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface StocktakeDocItemMapper extends BaseMapper<StocktakeDocItemDO> {
}
