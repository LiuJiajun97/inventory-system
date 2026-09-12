package com.company.inventory.model.vo.sales;

import com.company.inventory.model.vo.customer.CustomerVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售订单出参(表头 + 行)。
 *
 * @param id              主键
 * @param docNo           单据编号
 * @param docDate         单据日期
 * @param customerId      客户 ID
 * @param customer        客户对象
 * @param salespersonId   销售员用户 ID
 * @param warehouseId     发货仓库 ID
 * @param totalAmount     整单不含税合计(字符串)
 * @param totalTaxAmount  整单税额合计(字符串)
 * @param totalTaxInclusive 整单价税合计(字符串)
 * @param contractNo      合同号(V9 增量)
 * @param freight         运费(V9 增量,字符串)
 * @param shippingAddress 交货地址(V9 增量)
 * @param discountAmount  折扣额(V10 增量,字符串)
 * @param currencyCode    币种(V10 增量)
 * @param exchangeRate    汇率(V10 增量,字符串)
 * @param status          单据状态
 * @param creator         制单人
 * @param createdAt       制单时间
 * @param updater         修改人
 * @param updatedAt       修改时间
 * @param approver        审批人
 * @param approvedAt      审批时间
 * @param rejectReason    驳回原因
 * @param remark          备注
 * @param items           订单行
 * @author inventory
 */
public record SalesOrderVO(Long id, String docNo, LocalDate docDate, Long customerId,
        CustomerVO customer, Long salespersonId, Long warehouseId,
        String totalAmount, String totalTaxAmount, String totalTaxInclusive,
        String contractNo, String freight, String shippingAddress,
        String discountAmount, String currencyCode, String exchangeRate,
        String status, String creator, LocalDateTime createdAt,
        String updater, LocalDateTime updatedAt, String approver, LocalDateTime approvedAt,
        String rejectReason, String remark, List<SalesOrderItemVO> items) {
}
