package com.company.inventory.mapper;

import com.company.inventory.entity.transfer.TransferDocDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 调拨单表头 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface TransferDocMapper extends BaseMapper<TransferDocDO> {
}
