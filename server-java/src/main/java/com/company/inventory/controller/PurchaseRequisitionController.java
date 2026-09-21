package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.purchase.PurchaseRequisitionCreateDTO;
import com.company.inventory.model.query.PurchaseRequisitionQuery;
import com.company.inventory.model.vo.purchase.PurchaseRequisitionVO;
import com.company.inventory.service.PurchaseRequisitionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * 请购单接口(V25,无审批:admin+operator 可建可编可提交/转订单/取消)。
 *
 * @author inventory
 */
@Tag(name = "请购单")
@RestController
@RequestMapping("/api/v1/purchase-requisitions")
@Validated
public class PurchaseRequisitionController {

    /** 请购单服务。 */
    private final PurchaseRequisitionService purchaseRequisitionService;

    /**
     * 构造控制器。
     *
     * @param purchaseRequisitionService 请购单服务
     */
    public PurchaseRequisitionController(PurchaseRequisitionService purchaseRequisitionService) {
        this.purchaseRequisitionService = purchaseRequisitionService;
    }

    /**
     * 请购单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "请购单列表")
    @GetMapping
    public PageResult<PurchaseRequisitionVO> list(@Valid PurchaseRequisitionQuery query) {
        return purchaseRequisitionService.list(query);
    }

    /**
     * 新建请购单(草稿)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建请购单
     */
    @Operation(summary = "新建请购单")
    @PostMapping
    @RequirePerm("requisition:create")
    public PurchaseRequisitionVO create(@Valid @RequestBody PurchaseRequisitionCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseRequisitionService.create(dto, username);
    }

    /**
     * 请购单详情。
     *
     * @param id 请购单 ID
     * @return 请购单
     */
    @Operation(summary = "请购单详情")
    @GetMapping("/{id}")
    public PurchaseRequisitionVO get(@PathVariable long id) {
        return purchaseRequisitionService.get(id);
    }

    /**
     * 编辑请购单(仅 draft)。
     *
     * @param id      请购单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的请购单
     */
    @Operation(summary = "编辑请购单")
    @PutMapping("/{id}")
    @RequirePerm("requisition:edit")
    public PurchaseRequisitionVO update(@PathVariable long id,
            @Valid @RequestBody PurchaseRequisitionCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseRequisitionService.update(id, dto, username);
    }

    /**
     * 提交请购单(draft → submitted)。
     *
     * @param id      请购单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的请购单
     */
    @Operation(summary = "提交请购单")
    @PostMapping("/{id}/submit")
    @RequirePerm("requisition:submit")
    public PurchaseRequisitionVO submit(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseRequisitionService.submit(id, username);
    }

    /**
     * 取消请购单(draft/submitted → cancelled)。
     *
     * @param id      请购单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的请购单
     */
    @Operation(summary = "取消请购单")
    @PostMapping("/{id}/cancel")
    @RequirePerm("requisition:cancel")
    public PurchaseRequisitionVO cancel(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseRequisitionService.cancel(id, username);
    }
}