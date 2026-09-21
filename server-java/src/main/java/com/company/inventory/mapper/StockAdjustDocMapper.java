package com.company.inventory.mapper;

import com.company.inventory.model.entity.adjust.StockAdjustDocDO;
import com.company.inventory.model.query.StockAdjustDocLineQuery;
import com.company.inventory.model.vo.adjust.StockAdjustDocLineVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存调整单表头 Mapper(明细行拍平查询见 resources/mapper/StockAdjustDocMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface StockAdjustDocMapper extends BaseMapper<StockAdjustDocDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/仓库,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<StockAdjustDocLineVO> selectDocLines(@Param("page") Page<StockAdjustDocLineVO> page,
            @Param("q") StockAdjustDocLineQuery q);
}
