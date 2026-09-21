package com.company.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.inventory.model.entity.settlement.PaymentDocDO;
import com.company.inventory.model.vo.settlement.SettledInvoiceVO;
import com.company.inventory.model.vo.settlement.UnsettledInvoiceVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 付款/收款单头 Mapper(含 V18 结算域只读 SQL:可核销票/已核销聚合)。
 *
 * @author inventory
 */
public interface PaymentDocMapper extends BaseMapper<PaymentDocDO> {

    /**
     * 查某对方 confirmed 正票的未核销额(新建付款/收款核销行选择区用)。
     *
     * @param invoiceType 发票类型(payment 付款对应 purchase | receipt 收款对应 sales)
     * @param partyId     对方 ID
     * @return 未核销票列表(含票额/已核销/剩余)
     */
    List<UnsettledInvoiceVO> selectUnsettledInvoices(@Param("type") String invoiceType,
            @Param("partyId") Long partyId);

    /**
     * 按发票聚合累计已核销额(未作废付款/收款单,防超核硬校验用)。
     *
     * @param invoiceIds 发票 ID 列表(非空)
     * @return 发票级已核销额列表
     */
    List<SettledInvoiceVO> selectSettledByInvoices(@Param("ids") List<Long> invoiceIds);
}
