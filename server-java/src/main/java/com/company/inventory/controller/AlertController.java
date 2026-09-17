package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.query.ExpiryAlertQuery;
import com.company.inventory.model.query.LowStockQuery;
import com.company.inventory.service.AlertService;
import com.company.inventory.model.vo.alert.ExpiryAlertVO;
import com.company.inventory.model.vo.alert.LowStockVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预警接口(全员可见,仅查询聚合不做推送)。
 *
 * @author inventory
 */
@Tag(name = "预警")
@RestController
@RequestMapping("/api/v1/alerts")
@Validated
public class AlertController {

    /** 预警服务。 */
    private final AlertService alertService;

    /**
     * 构造控制器。
     *
     * @param alertService 预警服务
     */
    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * 临期预警分页列表。
     *
     * @param query 查询条件(warehouseId/itemId/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "临期预警列表")
    @GetMapping("/expiry")
    public PageResult<ExpiryAlertVO> expiry(@Valid ExpiryAlertQuery query) {
        return alertService.expiry(query);
    }

    /**
     * 低库存预警分页列表。
     *
     * @param query 查询条件(itemId/itemKeyword/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "低库存预警列表")
    @GetMapping("/low-stock")
    public PageResult<LowStockVO> lowStock(@Valid LowStockQuery query) {
        return alertService.lowStock(query);
    }
}
