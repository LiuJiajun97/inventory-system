package com.company.inventory.vo.adjust;

import com.company.inventory.vo.warehouse.WarehouseVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存调整单出参(表头 + 行)。
 *
 * @param id          主键
 * @param docNo       单据编号
 * @param docDate     单据日期
 * @param warehouseId 仓库 ID
 * @param warehouse   仓库对象
 * @param adjustType  调整类型
 * @param refDocNo    来源盘点单号
 * @param status      单据状态
 * @param creator     制单人
 * @param createdAt   制单时间
 * @param updater     修改人
 * @param updatedAt   修改时间
 * @param approver    审批人
 * @param approvedAt  审批时间
 * @param rejectReason 驳回原因
 * @param remark      备注
 * @param items       调整行
 * @author inventory
 */
public record StockAdjustDocVO(Long id, String docNo, LocalDate docDate, Long warehouseId,
        WarehouseVO warehouse, String adjustType, String refDocNo, String status,
        String creator, LocalDateTime createdAt, String updater, LocalDateTime updatedAt,
        String approver, LocalDateTime approvedAt, String rejectReason, String remark,
        List<StockAdjustDocItemVO> items) {
}
