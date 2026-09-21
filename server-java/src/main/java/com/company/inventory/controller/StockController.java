package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.stock.StockFreezeDTO;
import com.company.inventory.model.dto.stock.StockUnfreezeDTO;
import com.company.inventory.model.query.FreezeLogQuery;
import com.company.inventory.model.query.StockQuery;
import com.company.inventory.model.query.TransactionQuery;
import com.company.inventory.service.StockFreezeService;
import com.company.inventory.service.StockQueryService;
import com.company.inventory.model.vo.stock.FreezeLogVO;
import com.company.inventory.model.vo.stock.StockVO;
import com.company.inventory.model.vo.stock.TransactionVO;
import com.company.inventory.service.ExportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 库存冻结服务(V26)。 */
    private final StockFreezeService stockFreezeService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param stockQueryService  库存查询服务
     * @param stockFreezeService 库存冻结服务(V26)
     * @param exportService      导出服务
     */
    public StockController(StockQueryService stockQueryService,
            StockFreezeService stockFreezeService, ExportService exportService) {
        this.stockQueryService = stockQueryService;
        this.stockFreezeService = stockFreezeService;
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

    /**
     * 冻结批次(V26:仓+批次粒度,只拦出不拦入,原因必填)。
     *
     * @param dto     入参(仓库/批次号/原因)
     * @param request 请求(取当前用户名)
     */
    @Operation(summary = "冻结批次")
    @Tag(name = "库存")
    @PostMapping("/stock/freeze")
    @RequirePerm("stock:freeze")
    public void freeze(@Valid @RequestBody StockFreezeDTO dto, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        stockFreezeService.freeze(dto, username);
    }

    /**
     * 解冻批次(V26:仓+批次粒度)。
     *
     * @param dto     入参(仓库/批次号)
     * @param request 请求(取当前用户名)
     */
    @Operation(summary = "解冻批次")
    @Tag(name = "库存")
    @PostMapping("/stock/unfreeze")
    @RequirePerm("stock:freeze")
    public void unfreeze(@Valid @RequestBody StockUnfreezeDTO dto, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        stockFreezeService.unfreeze(dto, username);
    }

    /**
     * 冻结记录分页查询(登录即可,时间倒序,带物品编码/名称回填)。
     *
     * @param query 查询条件(仓库/批次号模糊,分页)
     * @return 分页结果
     */
    @Operation(summary = "冻结记录")
    @Tag(name = "库存")
    @GetMapping("/stock/freeze-logs")
    public PageResult<FreezeLogVO> freezeLogs(@Valid FreezeLogQuery query) {
        return stockFreezeService.logs(query);
    }
}
