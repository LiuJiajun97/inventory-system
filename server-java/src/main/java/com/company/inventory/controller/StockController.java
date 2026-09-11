package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.query.StockQuery;
import com.company.inventory.query.TransactionQuery;
import com.company.inventory.service.StockQueryService;
import com.company.inventory.vo.stock.StockVO;
import com.company.inventory.vo.stock.TransactionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
public class StockController {

    /** 库存查询服务。 */
    private final StockQueryService stockQueryService;

    /**
     * 构造控制器。
     *
     * @param stockQueryService 库存查询服务
     */
    public StockController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
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
}
