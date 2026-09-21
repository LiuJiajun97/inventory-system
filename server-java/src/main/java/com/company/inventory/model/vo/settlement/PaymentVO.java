package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 付款/收款单 VO(列表/详情,详情带核销行)。
 *
 * @param id         单据 ID
 * @param docNo      单号(付款 FK- / 收款 SK-)
 * @param payType    单据类型(payment | receipt)
 * @param partyId    对方 ID
 * @param partyName  对方名称(供应商/客户,联表展示)
 * @param payDate    付款/收款日期
 * @param totalAmount 总额(核销行合计)
 * @param status     状态(confirmed | voided)
 * @param remark     备注
 * @param creator    创建人
 * @param createdAt  创建时间
 * @param lines      核销行(详情时有值)
 * @author inventory
 */
public record PaymentVO(Long id, String docNo, String payType, Long partyId, String partyName,
        LocalDate payDate, BigDecimal totalAmount, String status, String remark, String creator,
        LocalDateTime createdAt, List<PaymentLineVO> lines) {
}
