package com.company.inventory.mapper.log;

import com.company.inventory.model.entity.log.OperationLogDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper,不手写 SQL)。
 *
 * @author inventory
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogDO> {
}
