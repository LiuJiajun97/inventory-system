package com.company.inventory.model.dto.returns;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售退货行入参(单价/税率由服务端按原销售订单行快照锁定,前端传值忽略)。
 *
 * @param salesOrderItemId 原销售订单行 ID
 * @param quantity         退货数量(正数,上限=原行已发货-已退累计)
 * @param batchNo          批次号(仓库启用批次时按入库规则处理,可空)
 * @param productionDate   生产日期(仓库启用保质期时建批次用,可空)
 * @param expiryDate       保质期到期日(仓库启用保质期时建批次用,可空)
 * @param locationId       库位 ID(仓库启用库位时必填)
 * @param serialNos        序列号列表(仓库启用序列号时必传且数量一致)
 * @author inventory
 */
public record SalesReturnLineDTO(
        @NotNull(message = "原销售订单行 ID 必填")
        @Positive(message = "原销售订单行 ID 必须为正数") Long salesOrderItemId,
        @NotNull(message = "退货数量必填")
        @Positive(message = "退货数量必须为正数") BigDecimal quantity,
        String batchNo,
        LocalDate productionDate,
        LocalDate expiryDate,
        @Positive(message = "库位 ID 必须为正数") Long locationId,
        List<String> serialNos) {
}
