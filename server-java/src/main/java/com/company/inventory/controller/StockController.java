package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.query.StockQuery;
import com.company.inventory.model.query.TransactionQuery;
import com.company.inventory.service.StockQueryService;
import com.company.inventory.model.vo.stock.StockVO;
import com.company.inventory.model.vo.stock.TransactionVO;
import com.company.inventory.service.ExportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存与流水查询接口。
 *
 * @author inventory
 */
@Tag(name = "库存")
@RestController
@RequestMapping("/api/v1")
@Validated
public class StockController {

    /** 库存查询服务。 */
    private final StockQueryService stockQueryService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param stockQueryService 库存查询服务
     * @param exportService     导出服务
     */
    public StockController(StockQueryService stockQueryService, ExportService exportService) {
        this.stockQueryService = stockQueryService;
        this.exportService = exportService;
    }

    /**
     * 库存查询(分页)。
     *
     * @param query 查询条件(warehouseId/itemKeyword/batchNo/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "库存查询")
    @Tag(name = "库存")
    @GetMapping("/stock")
    public PageResult<StockVO> stock(@Valid StockQuery query) {
        return stockQueryService.queryStock(query);
    }

    /**
     * 流水分页查询(id 降序)。
     *
     * @param query 查询条件(warehouseId/itemId/from/to/bizCode/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "流水分页查询")
    @Tag(name = "流水")
    @GetMapping("/transactions")
    public PageResult<TransactionVO> transactions(@Valid TransactionQuery query) {
        return stockQueryService.queryTransactions(query);
    }

    /**
     * 库存导出(xlsx,不分页,沿用库存查询的数据权限)。
     *
     * @param query 查询条件(warehouseId/itemKeyword/batchNo,分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "库存导出")
    @Tag(name = "库存")
    @GetMapping("/stock/export")
    public void exportStock(@Valid StockQuery query, HttpServletResponse resp) {
        exportService.exportStock(query, resp);
    }
}
