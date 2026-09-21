package com.company.inventory.mapper.log;

import com.company.inventory.model.entity.log.SysMessageDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 站内消息 Mapper(V25,表 sys_message)。
 *
 * @author inventory
 */
@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessageDO> {
}