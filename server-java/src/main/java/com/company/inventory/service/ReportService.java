package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.query.report.PurchaseReconQuery;
import com.company.inventory.model.query.report.SalesReconQuery;
import com.company.inventory.model.query.report.StockAgeingQuery;
import com.company.inventory.model.query.report.StockMonthlyQuery;
import com.company.inventory.model.vo.report.PurchaseReconVO;
import com.company.inventory.model.vo.report.SalesReconVO;
import com.company.inventory.model.vo.report.StockAgeingVO;
import com.company.inventory.model.vo.report.StockMonthlyVO;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 报表中心服务:进销存月报/库龄呆滞/采购对账/销售对账,全部只读聚合查询。
 *
 * <p>数据权限:月报/库龄与 7 列表一致(applyDataScope 口径:admin 豁免、
 * 未授权查空、授权仓过滤);对账按单据所在仓的授权仓过滤(采购单经
 * 关联入库单仓库,销售/退货单按自有仓库)。
 * 导出与列表读权限一致,不另设权限码;时间口径东八区、日期 yyyy-MM-dd。</p>
 *
 * @author inventory
 */
public interface ReportService {

    /**
     * 进销存月报(item 维度,跨仓汇总,分页)。
     *
     * @param query 查询条件(warehouseId/itemKeyword/from/to/page/pageSize)
     * @return 分页结果
     */
    PageResult<StockMonthlyVO> monthlyReport(StockMonthlyQuery query);

    /**
     * 库龄/呆滞报表(批次维度,无批次仓按 item+仓库 汇总,分页)。
     *
     * @param query 查询条件(warehouseId/itemKeyword/ageFrom/ageTo/stagnantDays)
     * @return 分页结果
     */
    PageResult<StockAgeingVO> ageingReport(StockAgeingQuery query);

    /**
     * 采购对账(供应商维度,分页)。
     *
     * @param query 查询条件(supplierId/from/to/page/pageSize)
     * @return 分页结果
     */
    PageResult<PurchaseReconVO> purchaseRecon(PurchaseReconQuery query);

    /**
     * 销售对账(客户维度,分页)。
     *
     * @param query 查询条件(customerId/from/to/page/pageSize)
     * @return 分页结果
     */
    PageResult<SalesReconVO> salesRecon(SalesReconQuery query);

    /**
     * 进销存月报 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportMonthlyReport(StockMonthlyQuery query, HttpServletResponse resp);

    /**
     * 库龄/呆滞 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportAgeingReport(StockAgeingQuery query, HttpServletResponse resp);

    /**
     * 采购对账 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportPurchaseRecon(PurchaseReconQuery query, HttpServletResponse resp);

    /**
     * 销售对账 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportSalesRecon(SalesReconQuery query, HttpServletResponse resp);
}
