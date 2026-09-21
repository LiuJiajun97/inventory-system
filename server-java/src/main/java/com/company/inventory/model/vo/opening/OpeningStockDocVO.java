package com.company.inventory.model.vo.opening;

import com.company.inventory.model.vo.warehouse.WarehouseVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 期初单出参(列表/详情共用,契约字段全量)。
 *
 * @param id          主键
 * @param docNo       单据号
 * @param docDate     单据日期
 * @param warehouseId 仓库 ID
 * @param totalQty    总数量(字符串)
 * @param remark      备注
 * @param status      单据状态
 * @param creator     创建人
 * @param createdAt   创建时间
 * @param warehouse   仓库对象
 * @param items       期初行(详情含,列表同样带行用于摘要展示)
 * @author inventory
 */
public record OpeningStockDocVO(Long id, String docNo, LocalDate docDate, Long warehouseId,
        String totalQty, String remark, String status, String creator, LocalDateTime createdAt,
        WarehouseVO warehouse, List<OpeningStockDocItemVO> items) {
}
