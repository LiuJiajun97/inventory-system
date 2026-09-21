package com.company.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.inventory.model.entity.settlement.InvoiceItemDO;

/**
 * 发票行 Mapper(行级 CRUD,聚合 SQL 见 InvoiceMapper.xml)。
 *
 * @author inventory
 */
public interface InvoiceItemMapper extends BaseMapper<InvoiceItemDO> {
}
