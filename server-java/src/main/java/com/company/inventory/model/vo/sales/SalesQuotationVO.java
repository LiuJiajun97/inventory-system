package com.company.inventory.model.vo.sales;

import com.company.inventory.model.vo.customer.CustomerVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售报价单出参(V25):表头 + 行 + 客户对象。
 *
 * @param id                主键
 * @param docNo             单据编号
 * @param docDate           单据日期
 * @param customerId        客户 ID
 * @param customer          客户对象
 * @param salespersonId     销售员用户 ID(可空)
 * @param warehouseId       发货仓库 ID
 * @param totalAmount       整单不含税合计(字符串)
 * @param totalTaxAmount    整单税额合计(字符串)
 * @param totalTaxInclusive 整单价税合计(字符串)
 * @param quoteValidUntil   报价有效期
 * @param status            单据状态
 * @param expired           是否已过期(quote_valid_until < 今天 且 status 为 draft/sent,纯展示)
 * @param creator           制单人
 * @param createdAt         制单时间
 * @param updater           更新人
 * @param updatedAt         更新时间
 * @param remark            备注
 * @param items             报价单行
 * @author inventory
 */
public record SalesQuotationVO(Long id, String docNo, LocalDate docDate,
        Long customerId, CustomerVO customer, Long salespersonId, Long warehouseId,
        String totalAmount, String totalTaxAmount, String totalTaxInclusive,
        LocalDate quoteValidUntil, String status, Boolean expired,
        String creator, LocalDateTime createdAt,
        String updater, LocalDateTime updatedAt, String remark,
        List<SalesQuotationItemVO> items) {
}