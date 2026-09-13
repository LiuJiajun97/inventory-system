package com.company.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.inventory.model.entity.settlement.InvoiceDO;
import com.company.inventory.model.vo.settlement.InvoiceableLineVO;
import com.company.inventory.model.vo.settlement.InvoicedSrcVO;
import com.company.inventory.model.vo.settlement.LedgerInvoiceVO;
import com.company.inventory.model.vo.settlement.OrderProgressVO;
import com.company.inventory.model.vo.settlement.PartyLedgerVO;
import com.company.inventory.model.vo.settlement.SettlementDashboardVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发票头 Mapper(含 V18 结算域只读聚合 SQL:台账/订单执行/仪表盘/可挂票行)。
 *
 * @author inventory
 */
public interface InvoiceMapper extends BaseMapper<InvoiceDO> {

    /**
     * 查某对方可挂票的源单据行(未开票/部分开票):采购=采购关联入库行,销售=销售关联出库行。
     *
     * @param invoiceType 发票类型(purchase | sales)
     * @param partyId     对方 ID(供应商/客户)
     * @return 可挂票行列表(含含税额/累计已开票/剩余可开)
     */
    List<InvoiceableLineVO> selectInvoiceableLines(@Param("type") String invoiceType,
            @Param("partyId") Long partyId);

    /**
     * 按源单据行聚合累计已开票额(未作废发票的全部行,防超开硬校验用)。
     *
     * @return 源行聚合列表(按 src_doc_type/src_doc_id/src_doc_item_id)
     */
    List<InvoicedSrcVO> selectInvoicedSumBySrc();

    /**
     * 应付/应收台账主行:按对方聚合入库(出库)额/已开票净额/已付(收)额。
     *
     * @param invoiceType 发票类型(purchase 应付 | sales 应收)
     * @return 台账行列表(每个对方一行,无数据对方三额为 0)
     */
    List<PartyLedgerVO> selectPartyLedger(@Param("type") String invoiceType);

    /**
     * 台账行展开:某对方 confirmed 发票列表(含累计已核销额)。
     *
     * @param invoiceType 发票类型
     * @param partyId     对方 ID
     * @return 发票列表
     */
    List<LedgerInvoiceVO> selectPartyInvoices(@Param("type") String invoiceType,
            @Param("partyId") Long partyId);

    /**
     * 订单执行跟踪(金蝶执行明细表口径):某对方的订单行展开
     * 下单额/已入库(出库)额/已开票额/已付(收)额(核销额按该订单行开票占比分摊)。
     *
     * @param invoiceType 发票类型
     * @param partyId     对方 ID
     * @return 订单执行列表
     */
    List<OrderProgressVO> selectOrderProgress(@Param("type") String invoiceType,
            @Param("partyId") Long partyId);

    /**
     * 仪表盘余额:应付余额=确认采购票净额-确认付款额;应收余额=确认销售票净额-确认收款额。
     *
     * @return 单行结果(apBalance/arBalance)
     */
    SettlementDashboardVO selectDashboardBalance();
}
