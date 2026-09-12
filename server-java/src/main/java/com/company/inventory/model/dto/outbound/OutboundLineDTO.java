package com.company.inventory.model.dto.outbound;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出库行入参。
 *
 * @param itemId    物品 ID
 * @param qty       数量(正数)
 * @param batchNo   批次号
 * @param locationId 库位 ID
 * @param serialNos 序列号列表
 * @param unitPrice 出库参考单价(销售发货由服务端按订单行覆盖,可空)
 * @param taxRate   税率(百分数,可空,空按 0 算)
 * @param refLineId 关联销售订单行 ID(销售发货必填)
 * @author inventory
 */
public record OutboundLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "数量必填")
        @Positive(message = "数量必须为正数") BigDecimal qty,
        String batchNo,
        @Positive(message = "库位 ID 必须为正数") Long locationId,
        List<String> serialNos,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        @Positive(message = "订单行 ID 必须为正数") Long refLineId) {

    /** 兼容旧签名构造器(V10 新增 taxRate 默认 null,既有调用/测试不受影响)。 */
    public OutboundLineDTO(Long itemId, BigDecimal qty, String batchNo, Long locationId,
            List<String> serialNos, BigDecimal unitPrice,
            @Positive(message = "订单行 ID 必须为正数") Long refLineId) {
        this(itemId, qty, batchNo, locationId, serialNos, unitPrice, null, refLineId);
    }
}
