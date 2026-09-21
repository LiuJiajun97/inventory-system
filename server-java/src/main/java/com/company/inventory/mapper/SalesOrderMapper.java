package com.company.inventory.mapper;

import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.query.SalesOrderLinesQuery;
import com.company.inventory.model.vo.sales.SalesOrderLinesVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 销售订单表头 Mapper(明细行拍平查询见 resources/mapper/SalesOrderMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrderDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/客户,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<SalesOrderLinesVO> selectDocLines(@Param("page") Page<SalesOrderLinesVO> page,
            @Param("q") SalesOrderLinesQuery q);
}
