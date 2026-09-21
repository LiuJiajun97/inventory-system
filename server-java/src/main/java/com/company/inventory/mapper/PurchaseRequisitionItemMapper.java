package com.company.inventory.mapper;

import com.company.inventory.model.entity.purchase.PurchaseRequisitionItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 请购单行 Mapper(V25,表 purchase_requisition_item)。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseRequisitionItemMapper extends BaseMapper<PurchaseRequisitionItemDO> {
}