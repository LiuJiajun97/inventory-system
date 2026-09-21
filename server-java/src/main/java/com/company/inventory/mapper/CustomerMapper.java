package com.company.inventory.mapper;

import com.company.inventory.model.entity.customer.CustomerDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 客户主数据 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface CustomerMapper extends BaseMapper<CustomerDO> {
}
