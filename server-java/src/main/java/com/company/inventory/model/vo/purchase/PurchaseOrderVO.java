package com.company.inventory.model.vo.purchase;

import com.company.inventory.model.vo.supplier.SupplierVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购订单出参(表头 + 行)。
 *
 * @param id                     主键
 * @param docNo                  单据编号
 * @param docDate                单据日期
 * @param supplierId             供应商 ID
 * @param supplier               供应商对象
 * @param buyerId                采购员用户 ID
 * @param allowOverReceiptRate   超收比例
 * @param totalAmount            整单不含税合计(字符串)
 * @param totalTaxAmount         整单税额合计(字符串)
 * @param totalTaxInclusive      整单价税合计(字符串)
 * @param status                 单据状态
 * @param creator                制单人
 * @param createdAt              制单时间
 * @param updater                修改人
 * @param updatedAt              修改时间
 * @param approver               审批人
 * @param approvedAt             审批时间
 * @param rejectReason           驳回原因
 * @param remark                 备注
 * @param items                  订单行
 * @author inventory
 */
public record PurchaseOrderVO(Long id, String docNo, LocalDate docDate, Long supplierId,
        SupplierVO supplier, Long buyerId, BigDecimal allowOverReceiptRate,
        String totalAmount, String totalTaxAmount, String totalTaxInclusive,
        String status, String creator, LocalDateTime createdAt,
        String updater, LocalDateTime updatedAt, String approver, LocalDateTime approvedAt,
        String rejectReason, String remark, List<PurchaseOrderItemVO> items) {
}
