package com.company.inventory.mapper;

import com.company.inventory.model.entity.purchase.PurchaseOrderItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 采购订单行 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItemDO> {

    /**
     * 采购到货回写(条件 UPDATE,防并发超收):仅未关闭行且累计+本次 ≤ 订单量×(1+超收比例)才成功。
     *
     * @param lineId  订单行 ID
     * @param orderId 订单 ID(取表头超收比例)
     * @param qty     本次到货数量
     * @return 影响行数(0=超收或行已关闭,1=成功;累计达标时同步置 closed=true)
     */
    @Update("UPDATE purchase_order_item "
            + "SET arrived_qty = arrived_qty + #{qty}, "
            + "closed = (arrived_qty + #{qty} >= ordered_qty) "
            + "WHERE id = #{lineId} AND order_id = #{orderId} AND NOT closed "
            + "AND arrived_qty + #{qty} <= ordered_qty * (1 + "
            + "(SELECT allow_over_receipt_rate FROM purchase_order WHERE id = #{orderId}))")
    int applyArrival(@Param("lineId") Long lineId, @Param("orderId") Long orderId,
            @Param("qty") BigDecimal qty);
}
