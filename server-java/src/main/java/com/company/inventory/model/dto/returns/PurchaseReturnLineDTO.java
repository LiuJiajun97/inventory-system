package com.company.inventory.model.dto.returns;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购退货行入参(单价/税率由服务端按原采购订单行快照锁定,前端传值忽略)。
 *
 * @param purchaseOrderItemId 原采购订单行 ID
 * @param quantity            退货数量(正数,上限=原行已到货-已退累计)
 * @param locationId          库位 ID(仓库启用库位时必填)
 * @param serialNos           序列号列表(仓库启用序列号时必传且数量一致)
 * @author inventory
 */
public record PurchaseReturnLineDTO(
        @NotNull(message = "原采购订单行 ID 必填")
        @Positive(message = "原采购订单行 ID 必须为正数") Long purchaseOrderItemId,
        @NotNull(message = "退货数量必填")
        @Positive(message = "退货数量必须为正数") BigDecimal quantity,
        @Positive(message = "库位 ID 必须为正数") Long locationId,
        List<String> serialNos) {
}
