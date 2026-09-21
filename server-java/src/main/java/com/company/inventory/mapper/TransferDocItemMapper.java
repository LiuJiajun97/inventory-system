package com.company.inventory.mapper;

import com.company.inventory.model.entity.transfer.TransferDocItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 调拨单行 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface TransferDocItemMapper extends BaseMapper<TransferDocItemDO> {
}
