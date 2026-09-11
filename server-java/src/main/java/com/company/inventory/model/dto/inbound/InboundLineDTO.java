package com.company.inventory.model.dto.inbound;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 入库行入参。
 *
 * @param itemId         物品 ID
 * @param qty            数量(正数)
 * @param batchNo        批次号
 * @param productionDate 生产日期
 * @param expiryDate     保质期到期日
 * @param supplier       供应商
 * @param locationId     库位 ID
 * @param serialNos      序列号列表
 * @param unitPrice      入库不含税单价(采购到货由服务端按订单行覆盖,可空)
 * @param taxRate        税率(采购到货由服务端按订单行覆盖,可空)
 * @param refLineId      关联采购订单行 ID(采购到货必填)
 * @author inventory
 */
public record InboundLineDTO(
        @NotNull(message = "物品 ID 必填")
        @Positive(message = "物品 ID 必须为正数") Long itemId,
        @NotNull(message = "数量必填")
        @Positive(message = "数量必须为正数") BigDecimal qty,
        String batchNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        String supplier,
        @Positive(message = "库位 ID 必须为正数") Long locationId,
        List<String> serialNos,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        @Positive(message = "订单行 ID 必须为正数") Long refLineId) {
}
