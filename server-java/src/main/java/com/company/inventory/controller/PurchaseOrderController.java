package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.dto.purchase.PurchaseActionDTO;
import com.company.inventory.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.query.PurchaseOrderQuery;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.vo.purchase.PurchaseOrderVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
public class PurchaseOrderController {

    /** 采购订单服务。 */
    private final PurchaseOrderService purchaseOrderService;

    /**
     * 构造控制器。
     *
     * @param purchaseOrderService 采购订单服务
     */
    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
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
    @RequireRole({"admin", "operator"})
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
    @RequireRole({"admin", "operator"})
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
    @RequireRole({"admin", "operator"})
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
    @RequireRole({"admin", "operator"})
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
    @RequireRole({"admin", "operator"})
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
    @RequireRole({"admin", "operator"})
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
    @RequireRole({"admin", "operator"})
    public PurchaseOrderVO close(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseOrderService.close(id, username);
    }
}
