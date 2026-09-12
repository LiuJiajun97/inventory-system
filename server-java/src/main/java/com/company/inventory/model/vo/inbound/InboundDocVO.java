package com.company.inventory.model.vo.inbound;

import com.company.inventory.model.vo.warehouse.WarehouseVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单出参(列表/详情共用,契约字段全量 + 一期增量字段)。
 *
 * @param id          主键
 * @param docNo       单据号
 * @param warehouseId 仓库 ID
 * @param status      单据状态
 * @param remark      备注
 * @param creator     创建人
 * @param createdAt   创建时间
 * @param warehouse   仓库对象
 * @param items       单据行
 * @param refType     关联单据类型(增量:purchase/空)
 * @param refDocId    关联采购订单 ID(增量)
 * @param refDocNo    关联采购订单号(增量)
 * @param supplierName 关联供应商名称(增量)
 * @param docDate     单据日期(增量)
 * @param carrier     承运商(V9 增量)
 * @param vehicleNo   车牌(V9 增量)
 * @param freight     运费(V9 增量,字符串)
 * @param totalAmount 单据总金额(V10 增量,字符串)
 * @param docType     单据类型(V10 增量)
 * @param handler     经办人(V10 增量)
 * @author inventory
 */
public record InboundDocVO(Long id, String docNo, Long warehouseId, String status,
        String remark, String creator, LocalDateTime createdAt,
        WarehouseVO warehouse, List<InboundDocItemVO> items,
        String refType, Long refDocId, String refDocNo, String supplierName, LocalDate docDate,
        String carrier, String vehicleNo, String freight,
        String totalAmount, String docType, String handler) {
}
