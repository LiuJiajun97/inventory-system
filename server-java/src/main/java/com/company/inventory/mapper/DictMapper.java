package com.company.inventory.mapper;

import com.company.inventory.entity.dict.DictDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 字典 Mapper 接口(简单查询走 MyBatis-Plus BaseMapper,不手写 SQL)。
 *
 * @author inventory
 */
@Mapper
public interface DictMapper extends BaseMapper<DictDO> {
}
