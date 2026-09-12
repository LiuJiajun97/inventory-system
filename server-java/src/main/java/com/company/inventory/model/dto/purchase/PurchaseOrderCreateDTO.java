package com.company.inventory.model.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新建/编辑采购订单入参(服务端重算行金额与表头三合计,不信前端传值)。
 *
 * @param docDate              单据日期
 * @param supplierId           供应商 ID
 * @param buyerId              采购员用户 ID
 * @param allowOverReceiptRate 超收比例(0.10=允超 10%,可空默认 0)
 * @param contractNo           合同号(可空)
 * @param freight              运费(可空)
 * @param shippingAddress      交货地址(可空)
 * @param remark               备注
 * @param items                订单行(至少 1 行)
 * @author inventory
 */
public record PurchaseOrderCreateDTO(
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotNull(message = "供应商 ID 必填")
        @Positive(message = "供应商 ID 必须为正数") Long supplierId,
        @NotNull(message = "采购员 ID 必填")
        @Positive(message = "采购员 ID 必须为正数") Long buyerId,
        BigDecimal allowOverReceiptRate,
        String contractNo,
        BigDecimal freight,
        String shippingAddress,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<PurchaseOrderLineDTO> items) {

    /** 兼容旧签名构造器(V9 新增字段默认 null,既有调用/测试不受影响)。 */
    public PurchaseOrderCreateDTO(LocalDate docDate, Long supplierId, Long buyerId,
            BigDecimal allowOverReceiptRate, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<PurchaseOrderLineDTO> items) {
        this(docDate, supplierId, buyerId, allowOverReceiptRate, null, null, null, remark, items);
    }
}
