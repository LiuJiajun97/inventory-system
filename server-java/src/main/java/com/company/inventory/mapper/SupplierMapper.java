package com.company.inventory.mapper;

import com.company.inventory.entity.supplier.SupplierDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商主数据 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface SupplierMapper extends BaseMapper<SupplierDO> {
}
