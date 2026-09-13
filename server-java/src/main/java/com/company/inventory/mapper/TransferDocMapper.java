package com.company.inventory.mapper;

import com.company.inventory.model.entity.transfer.TransferDocDO;
import com.company.inventory.model.query.TransferDocLineQuery;
import com.company.inventory.model.vo.transfer.TransferDocLineVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 调拨单表头 Mapper(明细行拍平查询见 resources/mapper/TransferDocMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface TransferDocMapper extends BaseMapper<TransferDocDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/源仓/目的仓,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<TransferDocLineVO> selectDocLines(@Param("page") Page<TransferDocLineVO> page,
            @Param("q") TransferDocLineQuery q);
}
