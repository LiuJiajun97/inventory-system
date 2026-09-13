package com.company.inventory.mapper;

import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.query.PurchaseOrderLinesQuery;
import com.company.inventory.model.vo.purchase.PurchaseOrderLinesVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 采购订单表头 Mapper(明细行拍平查询见 resources/mapper/PurchaseOrderMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrderDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/供应商,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<PurchaseOrderLinesVO> selectDocLines(@Param("page") Page<PurchaseOrderLinesVO> page,
            @Param("q") PurchaseOrderLinesQuery q);
}
