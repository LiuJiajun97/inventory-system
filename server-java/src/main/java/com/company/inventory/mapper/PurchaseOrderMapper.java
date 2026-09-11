package com.company.inventory.mapper;

import com.company.inventory.entity.purchase.PurchaseOrderDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 采购订单表头 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrderDO> {
}
