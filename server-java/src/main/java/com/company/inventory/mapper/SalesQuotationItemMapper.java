package com.company.inventory.mapper;

import com.company.inventory.model.entity.sales.SalesQuotationItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 销售报价单行 Mapper(V25,表 sales_quotation_item)。
 *
 * @author inventory
 */
@Mapper
public interface SalesQuotationItemMapper extends BaseMapper<SalesQuotationItemDO> {
}