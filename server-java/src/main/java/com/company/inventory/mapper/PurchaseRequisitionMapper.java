package com.company.inventory.mapper;

import com.company.inventory.model.entity.purchase.PurchaseRequisitionDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 请购单表头 Mapper(V25,表 purchase_requisition)。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseRequisitionMapper extends BaseMapper<PurchaseRequisitionDO> {
}