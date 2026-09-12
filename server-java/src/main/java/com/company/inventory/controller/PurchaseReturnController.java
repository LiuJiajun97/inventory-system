package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.returns.PurchaseReturnCreateDTO;
import com.company.inventory.model.query.PurchaseReturnQuery;
import com.company.inventory.service.PurchaseReturnService;
import com.company.inventory.model.vo.returns.PurchaseReturnCreatedVO;
import com.company.inventory.model.vo.returns.PurchaseReturnVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购退货单接口(create 即过账,无草稿/作废)。
 *
 * @author inventory
 */
@Tag(name = "采购退货")
@RestController
@RequestMapping("/api/v1/purchase-returns")
public class PurchaseReturnController {

    /** 采购退货单服务。 */
    private final PurchaseReturnService purchaseReturnService;

    /**
     * 构造控制器。
     *
     * @param purchaseReturnService 采购退货单服务
     */
    public PurchaseReturnController(PurchaseReturnService purchaseReturnService) {
        this.purchaseReturnService = purchaseReturnService;
    }

    /**
     * 新建采购退货单并过账(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 创建结果(含联动出库单号)
     */
    @Operation(summary = "新建采购退货单(过账)")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public PurchaseReturnCreatedVO create(@Valid @RequestBody PurchaseReturnCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return purchaseReturnService.create(dto, username);
    }

    /**
     * 采购退货单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "采购退货单列表")
    @GetMapping
    public PageResult<PurchaseReturnVO> list(@Valid PurchaseReturnQuery query) {
        return purchaseReturnService.list(query);
    }

    /**
     * 采购退货单详情。
     *
     * @param id 单据 ID
     * @return 单据
     */
    @Operation(summary = "采购退货单详情")
    @GetMapping("/{id}")
    public PurchaseReturnVO get(@PathVariable long id) {
        return purchaseReturnService.get(id);
    }
}
