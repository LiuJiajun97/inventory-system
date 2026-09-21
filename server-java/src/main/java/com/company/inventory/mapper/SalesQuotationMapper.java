package com.company.inventory.mapper;

import com.company.inventory.model.entity.sales.SalesQuotationDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 销售报价单表头 Mapper(V25,表 sales_quotation)。
 *
 * @author inventory
 */
@Mapper
public interface SalesQuotationMapper extends BaseMapper<SalesQuotationDO> {
}