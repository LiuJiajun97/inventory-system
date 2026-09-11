package com.company.inventory.mapper;

import com.company.inventory.entity.sales.SalesOrderItemDO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 销售订单行 Mapper。
 *
 * @author inventory
 */
@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItemDO> {

    /**
     * 销售发货回写(条件 UPDATE,防并发超发):仅未关闭行且累计+本次 ≤ 订单量才成功。
     *
     * @param lineId  订单行 ID
     * @param orderId 订单 ID
     * @param qty     本次发货数量
     * @return 影响行数(0=超发或行已关闭,1=成功;累计达标时同步置 closed=true)
     */
    @Update("UPDATE \"SalesOrderItem\" "
            + "SET \"shippedQty\" = \"shippedQty\" + #{qty}, "
            + "\"closed\" = (\"shippedQty\" + #{qty} >= \"orderedQty\") "
            + "WHERE \"id\" = #{lineId} AND \"orderId\" = #{orderId} AND NOT \"closed\" "
            + "AND \"shippedQty\" + #{qty} <= \"orderedQty\"")
    int applyShipment(@Param("lineId") Long lineId, @Param("orderId") Long orderId,
            @Param("qty") BigDecimal qty);
}
