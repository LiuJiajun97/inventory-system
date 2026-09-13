package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.query.report.CostReportQuery;
import com.company.inventory.model.query.report.PurchaseReconQuery;
import com.company.inventory.model.query.report.SalesReconQuery;
import com.company.inventory.model.query.report.StockAgeingQuery;
import com.company.inventory.model.query.report.StockMonthlyQuery;
import com.company.inventory.model.vo.report.CostReportVO;
import com.company.inventory.model.vo.report.PurchaseReconVO;
import com.company.inventory.model.vo.report.SalesReconVO;
import com.company.inventory.model.vo.report.StockAgeingVO;
import com.company.inventory.model.vo.report.StockMonthlyVO;
import com.company.inventory.service.CostReportService;
import com.company.inventory.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表中心接口:进销存月报/库龄呆滞/采购对账/销售对账/库存成本,各带 xlsx 导出。
 *
 * <p>全部只读;菜单权限控制页面可见性,导出与列表读权限一致(不另设权限码);
 * 数据权限与 7 列表同口径(admin 豁免、未授权查空、授权仓过滤)。</p>
 *
 * @author inventory
 */
@Tag(name = "报表中心")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    /** 报表服务。 */
    private final ReportService reportService;

    /** 库存成本报表服务。 */
    private final CostReportService costReportService;

    /**
     * 构造控制器。
     *
     * @param reportService     报表服务
     * @param costReportService 库存成本报表服务
     */
    public ReportController(ReportService reportService, CostReportService costReportService) {
        this.reportService = reportService;
        this.costReportService = costReportService;
    }

    /**
     * 进销存月报(item 维度,跨仓汇总,分页)。
     *
     * @param query 查询条件(warehouseId/itemKeyword/from/to/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "进销存月报")
    @GetMapping("/stock-monthly")
    public PageResult<StockMonthlyVO> stockMonthly(@Valid StockMonthlyQuery query) {
        return reportService.monthlyReport(query);
    }

    /**
     * 库龄/呆滞报表(批次维度,分页)。
     *
     * @param query 查询条件(warehouseId/itemKeyword/ageFrom/ageTo/stagnantDays)
     * @return 分页结果
     */
    @Operation(summary = "库龄/呆滞报表")
    @GetMapping("/stock-ageing")
    public PageResult<StockAgeingVO> stockAgeing(@Valid StockAgeingQuery query) {
        return reportService.ageingReport(query);
    }

    /**
     * 采购对账(供应商维度,分页)。
     *
     * @param query 查询条件(supplierId/from/to/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "采购对账")
    @GetMapping("/purchase-recon")
    public PageResult<PurchaseReconVO> purchaseRecon(@Valid PurchaseReconQuery query) {
        return reportService.purchaseRecon(query);
    }

    /**
     * 销售对账(客户维度,分页)。
     *
     * @param query 查询条件(customerId/from/to/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "销售对账")
    @GetMapping("/sales-recon")
    public PageResult<SalesReconVO> salesRecon(@Valid SalesReconQuery query) {
        return reportService.salesRecon(query);
    }

    /**
     * 进销存月报 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "进销存月报导出")
    @GetMapping("/stock-monthly/export")
    public void exportStockMonthly(@Valid StockMonthlyQuery query, HttpServletResponse resp) {
        reportService.exportMonthlyReport(query, resp);
    }

    /**
     * 库龄/呆滞 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "库龄/呆滞导出")
    @GetMapping("/stock-ageing/export")
    public void exportStockAgeing(@Valid StockAgeingQuery query, HttpServletResponse resp) {
        reportService.exportAgeingReport(query, resp);
    }

    /**
     * 采购对账 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "采购对账导出")
    @GetMapping("/purchase-recon/export")
    public void exportPurchaseRecon(@Valid PurchaseReconQuery query, HttpServletResponse resp) {
        reportService.exportPurchaseRecon(query, resp);
    }

    /**
     * 销售对账 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "销售对账导出")
    @GetMapping("/sales-recon/export")
    public void exportSalesRecon(@Valid SalesReconQuery query, HttpServletResponse resp) {
        reportService.exportSalesRecon(query, resp);
    }

    /**
     * 库存成本(移动均价)报表(成本单元 = 仓库+物品+批次,不分页,全量行 + 合计行)。
     *
     * @param query 查询条件(warehouseId/itemId/date 均可空)
     * @return 报表(行 + 合计)
     */
    @Operation(summary = "库存成本(移动均价)报表")
    @GetMapping("/cost")
    public CostReportVO costReport(@Valid CostReportQuery query) {
        return costReportService.costReport(query);
    }

    /**
     * 库存成本 xlsx 导出(不分页,与列表筛选一致)。
     *
     * @param query 查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "库存成本导出")
    @GetMapping("/cost/export")
    public void exportCostReport(@Valid CostReportQuery query, HttpServletResponse resp) {
        costReportService.exportCostReport(query, resp);
    }
}
