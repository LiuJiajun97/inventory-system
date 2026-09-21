package com.company.inventory.model.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
 * @param discountAmount       折扣额(可空,不参与合计计算)
 * @param currencyCode         币种(可空,如 CNY)
 * @param exchangeRate         汇率(可空)
 * @param remark               备注
 * @param items                订单行(至少 1 行)
 * @param refDocType           关联源单类型(V25 requisition 请购单,可空)
 * @param refDocNo             关联源单单号(可空)
 * @param refDocId             关联源单 ID(可空)
 * @author inventory
 */
public record PurchaseOrderCreateDTO(
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotNull(message = "供应商 ID 必填")
        @Positive(message = "供应商 ID 必须为正数") Long supplierId,
        @NotNull(message = "采购员 ID 必填")
        @Positive(message = "采购员 ID 必须为正数") Long buyerId,
        @DecimalMin(value = "0.0", message = "超收比例不能为负")
        @DecimalMax(value = "1.0", message = "超收比例必须≤1(小数口径,0.1=10%)")
        BigDecimal allowOverReceiptRate,
        String contractNo,
        BigDecimal freight,
        String shippingAddress,
        BigDecimal discountAmount,
        String currencyCode,
        BigDecimal exchangeRate,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<PurchaseOrderLineDTO> items,
        String refDocType,
        String refDocNo,
        Long refDocId) {

    /** 兼容旧签名构造器(V9/V10 新增字段默认 null,既有调用/测试不受影响)。 */
    public PurchaseOrderCreateDTO(LocalDate docDate, Long supplierId, Long buyerId,
            BigDecimal allowOverReceiptRate, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<PurchaseOrderLineDTO> items) {
        this(docDate, supplierId, buyerId, allowOverReceiptRate, null, null, null,
                null, null, null, remark, items, null, null, null);
    }

    /** 兼容 V9 签名构造器(V10 新增折扣额/币种/汇率默认 null)。 */
    public PurchaseOrderCreateDTO(LocalDate docDate, Long supplierId, Long buyerId,
            BigDecimal allowOverReceiptRate, String contractNo, BigDecimal freight,
            String shippingAddress, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<PurchaseOrderLineDTO> items) {
        this(docDate, supplierId, buyerId, allowOverReceiptRate, contractNo, freight,
                shippingAddress, null, null, null, remark, items, null, null, null);
    }

    /** 兼容 V10 签名构造器(增加折扣额/币种/汇率字段)。 */
    public PurchaseOrderCreateDTO(LocalDate docDate, Long supplierId, Long buyerId,
            BigDecimal allowOverReceiptRate, String contractNo, BigDecimal freight,
            String shippingAddress, BigDecimal discountAmount, String currencyCode,
            BigDecimal exchangeRate, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<PurchaseOrderLineDTO> items) {
        this(docDate, supplierId, buyerId, allowOverReceiptRate, contractNo, freight,
                shippingAddress, discountAmount, currencyCode, exchangeRate, remark, items,
                null, null, null);
    }
}
