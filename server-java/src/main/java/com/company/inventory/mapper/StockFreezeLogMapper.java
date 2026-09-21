package com.company.inventory.mapper;

import com.company.inventory.model.entity.stock.StockFreezeLogDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 库存冻结审计流水 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper,不手写 SQL)。
 *
 * @author inventory
 */
@Mapper
public interface StockFreezeLogMapper extends BaseMapper<StockFreezeLogDO> {
}
