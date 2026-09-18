package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.purchase.PurchaseActionDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.query.PurchaseOrderQuery;
import com.company.inventory.model.query.PurchaseOrderLinesQuery;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.DocLineService;
import com.company.inventory.service.ExportService;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderLinesVO;

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
 * 采购订单接口(admin+operator 可建可编可提交可审批;审批人不得为制单人;主数据类操作仍 admin)。
 *
 * @author inventory
 */
@Tag(name = "采购订单")
@RestController
@RequestMapping("/api/v1/purchase-orders")
@Validated
public class PurchaseOrderController {

    /** 采购订单服务。 */
    private final PurchaseOrderService purchaseOrderService;

    /** 明细行查询服务(V17 主表/明细切换)。 */
    private final DocLineService docLineService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param purchaseOrderService 采购订单服务
     * @param docLineService 明细行查询服务
     * @param exportService 导出服务
     */
    public PurchaseOrderController(PurchaseOrderService purchaseOrderService,
            ExportService exportService, DocLineService docLineService) {
        this.purchaseOrderService = purchaseOrderService;
        this.docLineService = docLineService;
        this.exportService = exportService;
    }

    /**
     * 采购订单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "采购订单列表")
    @GetMapping
    public PageResult<PurchaseOrderVO> list(@Valid PurchaseOrderQuery query) {
        return purchaseOrderService.list(query);
    }

    /**
     * 新建采购订单(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建订单
     */
    @Operation(summary = "新建采购订单")
    @PostMapping
    @RequirePerm("purchase-order:create")
    public PurchaseOrderVO create(@Valid @RequestBody PurchaseOrderCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.create(dto, username);
    }

    /**
     * 订单详情。
     *
     * @param id 订单 ID
     * @return 订单
     */
    @Operation(summary = "采购订单详情")
    @GetMapping("/{id}")
    public PurchaseOrderVO get(@PathVariable long id) {
        return purchaseOrderService.get(id);
    }

    /**
     * 编辑订单(仅 draft/rejected,admin/operator)。
     *
     * @param id      订单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "编辑采购订单")
    @PutMapping("/{id}")
    @RequirePerm("purchase-order:edit")
    public PurchaseOrderVO update(@PathVariable long id, @Valid @RequestBody PurchaseOrderCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.update(id, dto, username);
    }

    /**
     * 提交审批(admin/operator)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "提交采购订单")
    @PostMapping("/{id}/submit")
    @RequirePerm("purchase-order:submit")
    public PurchaseOrderVO submit(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.submit(id, username);
    }

    /**
     * 审批通过(admin/operator,审批人≠制单人)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "审批通过采购订单")
    @PostMapping("/{id}/approve")
    @RequirePerm("purchase-order:approve")
    public PurchaseOrderVO approve(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.approve(id, username);
    }

    /**
     * 驳回(admin/operator,驳回原因必填)。
     *
     * @param id      订单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "驳回采购订单")
    @PostMapping("/{id}/reject")
    @RequirePerm("purchase-order:reject")
    public PurchaseOrderVO reject(@PathVariable long id, @Valid @RequestBody PurchaseActionDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.reject(id, dto, username);
    }

    /**
     * 作废(admin/operator)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "作废采购订单")
    @PostMapping("/{id}/void")
    @RequirePerm("purchase-order:void")
    public PurchaseOrderVO voidDoc(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.voidDoc(id, username);
    }

    /**
     * 手工关闭(admin/operator,approved → closed)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "关闭采购订单")
    @PostMapping("/{id}/close")
    @RequirePerm("purchase-order:close")
    public PurchaseOrderVO close(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.close(id, username);
    }


    /**
     * 采购订单导出(xlsx,不分页,权限与列表读一致)。
     *
     * @param query 列表查询条件(分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "采购订单导出")
    @GetMapping("/export")
    public void export(@Valid PurchaseOrderQuery query, HttpServletResponse resp) {
        exportService.exportPurchaseOrders(query, resp);
    }
    /**
     * 采购订单明细行列表(V17 主表/明细切换,纯只读)。
     *
     * @param query 明细行查询条件
     * @return 分页结果
     */
    @Operation(summary = "采购订单明细行列表")
    @GetMapping("/lines")
    public PageResult<PurchaseOrderLinesVO> lines(@Valid PurchaseOrderLinesQuery query) {
        return docLineService.listPurchaseOrderLines(query);
    }

}
