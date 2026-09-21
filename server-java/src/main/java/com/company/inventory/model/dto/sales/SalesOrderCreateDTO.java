package com.company.inventory.model.dto.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新建/编辑销售订单入参(服务端重算行金额与表头三合计)。
 *
 * @param docDate       单据日期
 * @param customerId    客户 ID
 * @param salespersonId 销售员用户 ID
 * @param warehouseId   发货仓库 ID(审批时按此仓 FEFO 预占)
 * @param contractNo    合同号(可空)
 * @param freight       运费(可空)
 * @param shippingAddress 交货地址(可空)
 * @param discountAmount  折扣额(可空,不参与合计计算)
 * @param currencyCode    币种(可空,如 CNY)
 * @param exchangeRate    汇率(可空)
 * @param remark        备注
 * @param items         订单行(至少 1 行)
 * @param refDocType    关联源单类型(V25 quotation 销售报价单,可空)
 * @param refDocNo      关联源单单号(可空)
 * @param refDocId      关联源单 ID(可空)
 * @author inventory
 */
public record SalesOrderCreateDTO(
        @NotNull(message = "单据日期必填") LocalDate docDate,
        @NotNull(message = "客户 ID 必填")
        @Positive(message = "客户 ID 必须为正数") Long customerId,
        @NotNull(message = "销售员 ID 必填")
        @Positive(message = "销售员 ID 必须为正数") Long salespersonId,
        @NotNull(message = "发货仓库 ID 必填")
        @Positive(message = "发货仓库 ID 必须为正数") Long warehouseId,
        String contractNo,
        BigDecimal freight,
        String shippingAddress,
        BigDecimal discountAmount,
        String currencyCode,
        BigDecimal exchangeRate,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<SalesOrderLineDTO> items,
        String refDocType,
        String refDocNo,
        Long refDocId) {

    /** 兼容旧签名构造器(V9/V10 新增字段默认 null,既有调用/测试不受影响)。 */
    public SalesOrderCreateDTO(LocalDate docDate, Long customerId, Long salespersonId,
            Long warehouseId, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<SalesOrderLineDTO> items) {
        this(docDate, customerId, salespersonId, warehouseId, null, null, null,
                null, null, null, remark, items, null, null, null);
    }

    /** 兼容 V9 签名构造器(V10 新增折扣额/币种/汇率默认 null)。 */
    public SalesOrderCreateDTO(LocalDate docDate, Long customerId, Long salespersonId,
            Long warehouseId, String contractNo, BigDecimal freight, String shippingAddress,
            String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<SalesOrderLineDTO> items) {
        this(docDate, customerId, salespersonId, warehouseId, contractNo, freight,
                shippingAddress, null, null, null, remark, items, null, null, null);
    }

    /** 兼容 V10 签名构造器(增加折扣额/币种/汇率字段)。 */
    public SalesOrderCreateDTO(LocalDate docDate, Long customerId, Long salespersonId,
            Long warehouseId, String contractNo, BigDecimal freight, String shippingAddress,
            BigDecimal discountAmount, String currencyCode, BigDecimal exchangeRate,
            String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<SalesOrderLineDTO> items) {
        this(docDate, customerId, salespersonId, warehouseId, contractNo, freight,
                shippingAddress, discountAmount, currencyCode, exchangeRate, remark, items,
                null, null, null);
    }
}
