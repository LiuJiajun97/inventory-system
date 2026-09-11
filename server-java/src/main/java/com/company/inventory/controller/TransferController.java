package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.transfer.TransferActionDTO;
import com.company.inventory.model.dto.transfer.TransferCreateDTO;
import com.company.inventory.model.query.TransferDocQuery;
import com.company.inventory.service.TransferService;
import com.company.inventory.model.vo.transfer.TransferDocVO;

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
 * 调拨单接口(admin+operator 可建可编可提交可审批;审批人不得为制单人;审批即执行)。
 *
 * @author inventory
 */
@Tag(name = "调拨单")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    /** 调拨单服务。 */
    private final TransferService transferService;

    /**
     * 构造控制器。
     *
     * @param transferService 调拨单服务
     */
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * 调拨单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "调拨单列表")
    @GetMapping
    public PageResult<TransferDocVO> list(@Valid TransferDocQuery query) {
        return transferService.list(query);
    }

    /**
     * 新建调拨单(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建订单
     */
    @Operation(summary = "新建调拨单")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public TransferDocVO create(@Valid @RequestBody TransferCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return transferService.create(dto, username);
    }

    /**
     * 订单详情。
     *
     * @param id 订单 ID
     * @return 订单
     */
    @Operation(summary = "调拨单详情")
    @GetMapping("/{id}")
    public TransferDocVO get(@PathVariable long id) {
        return transferService.get(id);
    }

    /**
     * 编辑订单(仅 draft/rejected,admin/operator)。
     *
     * @param id      订单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "编辑调拨单")
    @PutMapping("/{id}")
    @RequireRole({"admin", "operator"})
    public TransferDocVO update(@PathVariable long id, @Valid @RequestBody TransferCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return transferService.update(id, dto, username);
    }

    /**
     * 提交审批(admin/operator)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "提交调拨单")
    @PostMapping("/{id}/submit")
    @RequireRole({"admin", "operator"})
    public TransferDocVO submit(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return transferService.submit(id, username);
    }

    /**
     * 审批通过(admin/operator,审批人≠制单人)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "审批通过调拨单")
    @PostMapping("/{id}/approve")
    @RequireRole({"admin", "operator"})
    public TransferDocVO approve(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return transferService.approve(id, username);
    }

    /**
     * 驳回(admin/operator,驳回原因必填)。
     *
     * @param id      订单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "驳回调拨单")
    @PostMapping("/{id}/reject")
    @RequireRole({"admin", "operator"})
    public TransferDocVO reject(@PathVariable long id, @Valid @RequestBody TransferActionDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return transferService.reject(id, dto, username);
    }

    /**
     * 作废(admin/operator)。
     *
     * @param id      订单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的订单
     */
    @Operation(summary = "作废调拨单")
    @PostMapping("/{id}/void")
    @RequireRole({"admin", "operator"})
    public TransferDocVO voidDoc(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return transferService.voidDoc(id, username);
    }


}
