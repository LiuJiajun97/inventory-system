package com.company.inventory.model.dto.inbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建入库单入参(手工入库;关联采购订单到货时 refType=purchase + refDocId,行带 refLineId)。
 *
 * @param warehouseId 仓库 ID
 * @param remark      备注
 * @param items       入库行(至少 1 行)
 * @param refType     关联单据类型(可空:purchase=采购到货)
 * @param refDocId    关联采购订单 ID(可空)
 * @param docDate     单据日期(可空)
 * @param carrier     承运商(可空)
 * @param vehicleNo   车牌(可空)
 * @param freight     运费(可空)
 * @param docType     单据类型(可空,如 采购入库/退货入库)
 * @param handler     经办人(可空)
 * @author inventory
 */
public record InboundCreateDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<InboundLineDTO> items,
        String refType,
        @Positive(message = "关联订单 ID 必须为正数") Long refDocId,
        LocalDate docDate,
        String carrier,
        String vehicleNo,
        java.math.BigDecimal freight,
        String docType,
        String handler) {

    /** 兼容旧签名构造器(V9/V10 新增字段默认 null,既有调用/测试不受影响)。 */
    public InboundCreateDTO(Long warehouseId, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<InboundLineDTO> items, String refType,
            @Positive(message = "关联订单 ID 必须为正数") Long refDocId, LocalDate docDate) {
        this(warehouseId, remark, items, refType, refDocId, docDate, null, null, null, null, null);
    }

    /** 兼容 V9 签名构造器(V10 新增单据类型/经办人默认 null)。 */
    public InboundCreateDTO(Long warehouseId, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<InboundLineDTO> items, String refType,
            @Positive(message = "关联订单 ID 必须为正数") Long refDocId, LocalDate docDate,
            String carrier, String vehicleNo, java.math.BigDecimal freight) {
        this(warehouseId, remark, items, refType, refDocId, docDate, carrier, vehicleNo,
                freight, null, null);
    }
}
