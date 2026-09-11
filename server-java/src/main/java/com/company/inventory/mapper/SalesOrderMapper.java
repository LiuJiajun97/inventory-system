package com.company.inventory.mapper;

import com.company.inventory.entity.sales.SalesOrderDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 销售订单表头 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrderDO> {
}
