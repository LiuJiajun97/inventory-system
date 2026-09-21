package com.company.inventory.mapper;

import com.company.inventory.model.entity.opening.OpeningStockDocDO;
import com.company.inventory.model.query.OpeningStockDocLineQuery;
import com.company.inventory.model.vo.opening.OpeningStockDocLineVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 期初单 Mapper 接口(简单 CRUD 走 MyBatis-Plus BaseMapper;
 * 明细行拍平查询见 resources/mapper/OpeningStockDocMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface OpeningStockDocMapper extends BaseMapper<OpeningStockDocDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/仓库,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<OpeningStockDocLineVO> selectDocLines(@Param("page") Page<OpeningStockDocLineVO> page,
            @Param("q") OpeningStockDocLineQuery q);
}
