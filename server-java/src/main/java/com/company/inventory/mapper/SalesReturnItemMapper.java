package com.company.inventory.mapper;

import com.company.inventory.model.entity.returns.SalesReturnItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 销售退货单行 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface SalesReturnItemMapper extends BaseMapper<SalesReturnItemDO> {
}
