package com.company.inventory.service;

import com.company.inventory.model.query.report.CostReportQuery;
import com.company.inventory.model.vo.report.CostReportVO;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 库存成本(移动均价)报表服务:零建表,按流水实时回放计算,纯只读。
 *
 * <p>成本单元 = 仓库 + 物品 + 批次(批次 0/null 为独立无批次单元);
 * 回放口径:opening/inbound 按单据行单价加权,出库/调拨出/盘亏按当前均价消耗,
 * 调拨入按源仓当时均价结转(成本随货走),盘盈按当前均价入账,预占等辅助流水不影响金额;
 * 数量一律以流水 afterQty 为权威。
 * 数据权限与报表中心一致(admin 豁免、未授权查空、授权仓过滤);导出与列表读权限一致。</p>
 *
 * @author inventory
 */
public interface CostReportService {

    /**
     * 库存成本(移动均价)报表(不分页,全量行 + 合计行)。
     *
     * @param query 查询条件(warehouseId/itemId/date 均可空)
     * @return 报表(行 + 合计)
     */
    CostReportVO costReport(CostReportQuery query);

    /**
     * 库存成本 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportCostReport(CostReportQuery query, HttpServletResponse resp);
}
