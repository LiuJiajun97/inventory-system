package com.company.inventory.mapper;

import com.company.inventory.model.entity.returns.PurchaseReturnDO;
import com.company.inventory.model.query.PurchaseReturnLinesQuery;
import com.company.inventory.model.vo.returns.PurchaseReturnLinesVO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 采购退货单头 Mapper(明细行拍平查询见 resources/mapper/PurchaseReturnMapper.xml)。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseReturnMapper extends BaseMapper<PurchaseReturnDO> {

    /**
     * 明细行拍平分页查询(单据行 JOIN 主表/物品/源订单供应商,数据库层分页)。
     *
     * @param page 分页对象
     * @param q    查询条件
     * @return 分页结果
     */
    IPage<PurchaseReturnLinesVO> selectDocLines(@Param("page") Page<PurchaseReturnLinesVO> page,
            @Param("q") PurchaseReturnLinesQuery q);
}
