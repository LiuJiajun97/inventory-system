package com.company.inventory.model.dto.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 调拨单行入参。
 *
 * @param itemId         物品 ID
 * @param qty            调拨数量(正数)
 * @param unitPrice      成本参考单价(必填)
 * @param fromLocationId 源库位 ID(源仓启用库位时必填)
 * @param toLocationId   目的库位 ID(目的仓启用库位时必填)
 * @param serialNos      序列号列表(源仓启用序列号时必填)
 * @param vehicleNo      车牌(可空)
 * @param lineRemark     行备注
 * @author inventory
 */
public record TransferLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "调拨数量必填")
        @Positive(message = "调拨数量必须为正数") BigDecimal qty,
        @NotNull(message = "成本参考单价必填") BigDecimal unitPrice,
        @Positive(message = "源库位 ID 必须为正数") Long fromLocationId,
        @Positive(message = "目的库位 ID 必须为正数") Long toLocationId,
        List<String> serialNos,
        String vehicleNo,
        String lineRemark) {

    /** 兼容旧签名构造器(V9 新增字段默认 null,既有调用/测试不受影响)。 */
    public TransferLineDTO(Long itemId, BigDecimal qty, BigDecimal unitPrice,
            Long fromLocationId, Long toLocationId, List<String> serialNos,
            String lineRemark) {
        this(itemId, qty, unitPrice, fromLocationId, toLocationId, serialNos, null, lineRemark);
    }
}
