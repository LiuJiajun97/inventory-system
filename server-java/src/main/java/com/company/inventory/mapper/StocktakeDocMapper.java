package com.company.inventory.mapper;

import com.company.inventory.entity.stocktake.StocktakeDocDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 盘点单表头 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface StocktakeDocMapper extends BaseMapper<StocktakeDocDO> {
}
