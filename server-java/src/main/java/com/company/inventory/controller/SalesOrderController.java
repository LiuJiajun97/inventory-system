package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.sales.SalesActionDTO;
import com.company.inventory.model.dto.sales.SalesOrderCreateDTO;
import com.company.inventory.model.query.SalesOrderQuery;
import com.company.inventory.model.query.SalesOrderLinesQuery;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.DocLineService;
import com.company.inventory.service.ExportService;
import com.company.inventory.model.vo.sales.SalesOrderVO;
import com.company.inventory.model.vo.sales.SalesOrderLinesVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售订单接口(admin+operator 可建可编可提交可审批;审批人不得为制单人)。
 *
 * @author inventory
 */
@Tag(name = "销售订单")
@RestController
@RequestMapping("/api/v1/sales-orders")
@Validated
public class SalesOrderController {

    /** 销售订单服务。 */
    private final SalesOrderService salesOrderService;

    /** 明细行查询服务(V17 主表/明细切换)。 */
    private final DocLineService docLineService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param salesOrderService 销售订单服务
     * @param docLineService 明细行查询服务
     * @param exportService 导出服务
     */
    public SalesOrderController(SalesOrderService salesOrderService,
            ExportService exportService, DocLineService docLineService) {
        this.salesOrderService = salesOrderService;
        this.docLineService = docLineService;
        this.exportService = exportService;
    }

    /**
     * 销售订单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "销售订单列表")
    @GetMapping
    public PageResult<SalesOrderVO> list(@Valid SalesOrderQuery query) {
        return salesOrderService.list(query);
    }

    /**
     * 新建销售订单(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建订单
     */
    @Operation(summary = "新建销售订单")
    @PostMapping
    @RequirePerm("sales-order:create")
    public SalesOrderVO create(@Valid @RequestBody SalesOrderCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesOrderService.create(dto, username);
    }

    /**
     * 订单详情。
     *
     * @param id 订单 ID
     * @return 订单
     */
    @Operation(summary = "销售订单详情")
    @GetMapping("/{id}")
    public SalesOrderVO get(@PathVariable long id) {
        return salesOrderService.get(id);
    }

    /**
     * 编辑订单(仅 draft/rejected,admin/operator)。
     *
     * @param id      订单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "编辑销售订单")
    @PutMapping("/{id}")
    @RequirePerm("sales-order:edit")
    public SalesOrderVO update(@PathVariable long id, @Valid @RequestBody SalesOrderCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesOrderService.update(id, dto, username);
    }

    /**
     * 提交审批(admin/operator)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "提交销售订单")
    @PostMapping("/{id}/submit")
    @RequirePerm("sales-order:submit")
    public SalesOrderVO submit(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesOrderService.submit(id, username);
    }

    /**
     * 审批通过(admin/operator,审批人≠制单人)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "审批通过销售订单")
    @PostMapping("/{id}/approve")
    @RequirePerm("sales-order:approve")
    public SalesOrderVO approve(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesOrderService.approve(id, username);
    }

    /**
     * 驳回(admin/operator,驳回原因必填)。
     *
     * @param id      订单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "驳回销售订单")
    @PostMapping("/{id}/reject")
    @RequirePerm("sales-order:reject")
    public SalesOrderVO reject(@PathVariable long id, @Valid @RequestBody SalesActionDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesOrderService.reject(id, dto, username);
    }

    /**
     * 作废(admin/operator)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "作废销售订单")
    @PostMapping("/{id}/void")
    @RequirePerm("sales-order:void")
    public SalesOrderVO voidDoc(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesOrderService.voidDoc(id, username);
    }

    /**
     * 手工关闭(admin/operator,approved → closed)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "关闭销售订单")
    @PostMapping("/{id}/close")
    @RequirePerm("sales-order:close")
    public SalesOrderVO close(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesOrderService.close(id, username);
    }


    /**
     * 销售订单导出(xlsx,不分页,权限与列表读一致)。
     *
     * @param query 列表查询条件(分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "销售订单导出")
    @GetMapping("/export")
    public void export(@Valid SalesOrderQuery query, HttpServletResponse resp) {
        exportService.exportSalesOrders(query, resp);
    }
    /**
     * 销售订单明细行列表(V17 主表/明细切换,纯只读)。
     *
     * @param query 明细行查询条件
     * @return 分页结果
     */
    @Operation(summary = "销售订单明细行列表")
    @GetMapping("/lines")
    public PageResult<SalesOrderLinesVO> lines(@Valid SalesOrderLinesQuery query) {
        return docLineService.listSalesOrderLines(query);
    }

}
