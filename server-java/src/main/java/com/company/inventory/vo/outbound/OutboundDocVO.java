package com.company.inventory.vo.outbound;

import com.company.inventory.vo.warehouse.WarehouseVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出库单出参(列表/详情共用,契约字段全量 + 一期增量字段)。
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
 * @param refType     关联单据类型(增量:sales/空)
 * @param refDocId    关联销售订单 ID(增量)
 * @param refDocNo    关联销售订单号(增量)
 * @param customerName 关联客户名称(增量)
 * @param docDate     单据日期(增量)
 * @author inventory
 */
public record OutboundDocVO(Long id, String docNo, Long warehouseId, String status,
        String remark, String creator, LocalDateTime createdAt,
        WarehouseVO warehouse, List<OutboundDocItemVO> items,
        String refType, Long refDocId, String refDocNo, String customerName, LocalDate docDate) {
}
