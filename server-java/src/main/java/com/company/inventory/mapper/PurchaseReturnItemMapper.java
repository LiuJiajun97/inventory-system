package com.company.inventory.mapper;

import com.company.inventory.model.entity.returns.PurchaseReturnItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 采购退货单行 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseReturnItemMapper extends BaseMapper<PurchaseReturnItemDO> {
}
