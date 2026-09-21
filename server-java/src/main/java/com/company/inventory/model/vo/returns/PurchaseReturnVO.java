package com.company.inventory.model.vo.returns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.company.inventory.model.vo.warehouse.WarehouseVO;

/**
 * 采购退货单出参(头 + 行 + 原单号 + 仓库)。
 *
 * @param id                单据 ID
 * @param docNo             单据号(CT-YYYYMMDD-NNNN)
 * @param docDate           单据日期
 * @param purchaseOrderId   原采购订单 ID
 * @param purchaseOrderNo   原采购订单单号
 * @param warehouseId       退货仓库 ID
 * @param warehouse         仓库 VO
 * @param totalAmount       单据总金额(字符串)
 * @param remark            备注
 * @param status            单据状态(finished)
 * @param creator           创建人
 * @param createdAt         创建时间
 * @param items             退货行
 * @author inventory
 */
public record PurchaseReturnVO(Long id, String docNo, LocalDate docDate,
        Long purchaseOrderId, String purchaseOrderNo, Long warehouseId, WarehouseVO warehouse,
        BigDecimal totalAmount, String remark, String status, String creator,
        LocalDateTime createdAt, List<PurchaseReturnItemVO> items) {
}
