package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发票 VO(列表/详情,详情带行)。
 *
 * @param id           发票 ID
 * @param docNo        发票号(FP-YYYYMMDD-NNNN)
 * @param invoiceType  发票类型(purchase | sales)
 * @param partyId      对方 ID
 * @param partyName    对方名称(供应商/客户,联表展示)
 * @param invoiceDate  发票日期
 * @param totalAmount  发票总额(负票为负)
 * @param status       状态(draft | mismatch | confirmed | voided)
 * @param sign         正负号(positive | negative)
 * @param sourceType   来源(manual | return_gen)
 * @param refReturnId  退货单 ID(return_gen 时有值)
 * @param refReturnNo  退货单号(return_gen 时有值)
 * @param remark       备注
 * @param creator      创建人
 * @param createdAt    创建时间
 * @param updater      修改人
 * @param updatedAt    修改时间
 * @param items        发票行(详情时有值)
 * @author inventory
 */
public record InvoiceVO(Long id, String docNo, String invoiceType, Long partyId,
        String partyName, LocalDate invoiceDate, BigDecimal totalAmount, String status,
        String sign, String sourceType, Long refReturnId, String refReturnNo, String remark,
        String creator, LocalDateTime createdAt, String updater, LocalDateTime updatedAt,
        List<InvoiceItemVO> items) {
}
