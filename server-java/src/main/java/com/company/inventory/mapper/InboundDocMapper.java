package com.company.inventory.mapper;

import com.company.inventory.model.entity.inbound.InboundDocDO;





import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 入库单 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper,不手写 SQL)。
 *
 * @author inventory
 */
@Mapper
public interface InboundDocMapper extends BaseMapper<InboundDocDO> {
}
