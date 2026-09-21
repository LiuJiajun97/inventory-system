package com.company.inventory.mapper;

import com.company.inventory.model.entity.inbound.InboundDocDO;
import com.company.inventory.model.query.InboundDocLineQuery;
import com.company.inventory.model.vo.inbound.InboundDocLineVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 入库单 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper;
 * 明细行拍平查询见 resources/mapper/InboundDocMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface InboundDocMapper extends BaseMapper<InboundDocDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/仓库,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<InboundDocLineVO> selectDocLines(@Param("page") Page<InboundDocLineVO> page,
            @Param("q") InboundDocLineQuery q);
}
