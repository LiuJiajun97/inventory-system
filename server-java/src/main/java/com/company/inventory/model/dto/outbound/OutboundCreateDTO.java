package com.company.inventory.model.dto.outbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建出库单入参(手工出库;关联销售订单发货时 refType=sales + refDocId,行带 refLineId)。
 *
 * @param warehouseId 仓库 ID
 * @param remark      备注
 * @param items       出库行(至少 1 行)
 * @param refType     关联单据类型(可空:sales=销售发货)
 * @param refDocId    关联销售订单 ID(可空)
 * @param docDate     单据日期(可空)
 * @param carrier     承运商(可空)
 * @param vehicleNo   车牌(可空)
 * @param freight     运费(可空)
 * @author inventory
 */
public record OutboundCreateDTO(
        @NotNull(message = "仓库 ID 必填")
        @Positive(message = "仓库 ID 必须为正数") Long warehouseId,
        String remark,
        @Valid
        @NotEmpty(message = "至少 1 行") List<OutboundLineDTO> items,
        String refType,
        @Positive(message = "关联订单 ID 必须为正数") Long refDocId,
        LocalDate docDate,
        String carrier,
        String vehicleNo,
        java.math.BigDecimal freight) {

    /** 兼容旧签名构造器(V9 新增字段默认 null,既有调用/测试不受影响)。 */
    public OutboundCreateDTO(Long warehouseId, String remark,
            @Valid @NotEmpty(message = "至少 1 行") List<OutboundLineDTO> items, String refType,
            @Positive(message = "关联订单 ID 必须为正数") Long refDocId, LocalDate docDate) {
        this(warehouseId, remark, items, refType, refDocId, docDate, null, null, null);
    }
}
