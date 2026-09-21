package com.company.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.inventory.model.entity.settlement.PaymentLineDO;

/**
 * 核销行 Mapper(核销行 CRUD,聚合 SQL 见 PaymentDocMapper.xml)。
 *
 * @author inventory
 */
public interface PaymentLineMapper extends BaseMapper<PaymentLineDO> {
}
