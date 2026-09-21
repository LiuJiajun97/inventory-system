package com.company.inventory.service;

import com.company.inventory.model.vo.settlement.LedgerRowVO;
import com.company.inventory.model.vo.settlement.SettlementDashboardVO;

import java.util.List;

/**
 * 结算台账服务(V18 结算域):只读聚合,零建表。
 *
 * <p>应付余额 = 已确认发票净额(含负票) − 已付款额;待开票暂估 = 采购关联入库额 − 已开票额
 * (单列,不计入余额);应收侧对称。订单执行跟踪按金蝶执行明细表口径四列展开。</p>
 *
 * @author inventory
 */
public interface SettlementService {

    /**
     * 应付台账:按供应商聚合(入库额/已开票/待开票暂估/已付/应付余额 + 发票展开 + 订单执行子表)。
     *
     * @return 台账行列表
     */
    List<LedgerRowVO> apLedger();

    /**
     * 应收台账:按客户聚合(对称应付)。
     *
     * @return 台账行列表
     */
    List<LedgerRowVO> arLedger();

    /**
     * 仪表盘余额(应付余额/应收余额)。
     *
     * @return 余额
     */
    SettlementDashboardVO dashboard();
}
