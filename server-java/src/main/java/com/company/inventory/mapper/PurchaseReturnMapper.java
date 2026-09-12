package com.company.inventory.mapper;

import com.company.inventory.model.entity.returns.PurchaseReturnDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 采购退货单头 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseReturnMapper extends BaseMapper<PurchaseReturnDO> {
}
