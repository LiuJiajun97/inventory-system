package com.company.inventory.mapper;

import com.company.inventory.model.entity.outbound.OutboundDocDO;
import com.company.inventory.model.query.OutboundDocLineQuery;
import com.company.inventory.model.vo.outbound.OutboundDocLineVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 出库单 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper;
 * 明细行拍平查询见 resources/mapper/OutboundDocMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface OutboundDocMapper extends BaseMapper<OutboundDocDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/仓库,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<OutboundDocLineVO> selectDocLines(@Param("page") Page<OutboundDocLineVO> page,
            @Param("q") OutboundDocLineQuery q);
}
