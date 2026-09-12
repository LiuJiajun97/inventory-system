package com.company.inventory.mapper;

import com.company.inventory.model.entity.returns.SalesReturnDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 销售退货单头 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface SalesReturnMapper extends BaseMapper<SalesReturnDO> {
}
