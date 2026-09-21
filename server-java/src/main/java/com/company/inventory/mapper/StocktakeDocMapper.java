package com.company.inventory.mapper;

import com.company.inventory.model.entity.stocktake.StocktakeDocDO;
import com.company.inventory.model.query.StocktakeDocLineQuery;
import com.company.inventory.model.vo.stocktake.StocktakeDocLineVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 盘点单表头 Mapper(明细行拍平查询见 resources/mapper/StocktakeDocMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface StocktakeDocMapper extends BaseMapper<StocktakeDocDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/仓库,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<StocktakeDocLineVO> selectDocLines(@Param("page") Page<StocktakeDocLineVO> page,
            @Param("q") StocktakeDocLineQuery q);
}
