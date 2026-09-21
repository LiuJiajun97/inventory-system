package com.company.inventory.service.impl;

import com.company.inventory.common.constant.SettlementConstants;
import com.company.inventory.mapper.InvoiceMapper;
import com.company.inventory.model.vo.settlement.LedgerInvoiceVO;
import com.company.inventory.model.vo.settlement.LedgerRowVO;
import com.company.inventory.model.vo.settlement.OrderProgressVO;
import com.company.inventory.model.vo.settlement.PartyLedgerVO;
import com.company.inventory.model.vo.settlement.SettlementDashboardVO;
import com.company.inventory.service.SettlementService;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 结算台账服务实现(V18 结算域):只读聚合,零建表。
 *
 * <p>台账 = 发票(confirmed 净额)+ 核销行(未作废) + 入库/出库关联额,单条 SQL 聚合 +
 * service 层组装;余额 = 已开票净额 - 已付(收)额,待开票暂估 = 关联额 - 已开票(单列)。</p>
 *
 * @author inventory
 */
@Service
public class SettlementServiceImpl implements SettlementService {

    /** 发票 Mapper(台账聚合 SQL 宿主)。 */
    private final InvoiceMapper invoiceMapper;

    /**
     * 构造服务。
     *
     * @param invoiceMapper 发票 Mapper
     */
    public SettlementServiceImpl(InvoiceMapper invoiceMapper) {
        this.invoiceMapper = invoiceMapper;
    }

    /**
     * 应付台账(按供应商)。
     *
     * @return 台账行列表
     */
    @Override
    public List<LedgerRowVO> apLedger() {
        return buildLedger(SettlementConstants.INVOICE_TYPE_PURCHASE);
    }

    /**
     * 应收台账(按客户)。
     *
     * @return 台账行列表
     */
    @Override
    public List<LedgerRowVO> arLedger() {
        return buildLedger(SettlementConstants.INVOICE_TYPE_SALES);
    }

    /**
     * 仪表盘余额。
     *
     * @return 应付/应收余额
     */
    @Override
    public SettlementDashboardVO dashboard() {
        return invoiceMapper.selectDashboardBalance();
    }

    /**
     * 组装台账(应付/应收共用:主行 + 发票展开 + 订单执行子表)。
     *
     * @param type 发票类型(purchase 应付 | sales 应收)
     * @return 台账行列表
     */
    private List<LedgerRowVO> buildLedger(String type) {
        List<PartyLedgerVO> rows = invoiceMapper.selectPartyLedger(type);
        List<LedgerRowVO> result = new ArrayList<>();
        for (PartyLedgerVO row : rows) {
            BigDecimal received = row.receivedAmount() == null
                    ? BigDecimal.ZERO : row.receivedAmount();
            BigDecimal invoiced = row.invoicedAmount() == null
                    ? BigDecimal.ZERO : row.invoicedAmount();
            BigDecimal settled = row.settledAmount() == null
                    ? BigDecimal.ZERO : row.settledAmount();
            List<LedgerInvoiceVO> invoices = invoiceMapper
                    .selectPartyInvoices(type, row.partyId());
            List<OrderProgressVO> orders = invoiceMapper
                    .selectOrderProgress(type, row.partyId());
            result.add(new LedgerRowVO(row.partyId(), row.partyName(), received, invoiced,
                    received.subtract(invoiced), settled, invoiced.subtract(settled),
                    invoices, orders));
        }
        return result;
    }
}
