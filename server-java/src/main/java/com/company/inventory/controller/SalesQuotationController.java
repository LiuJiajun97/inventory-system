package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.sales.SalesQuotationCreateDTO;
import com.company.inventory.model.query.SalesQuotationQuery;
import com.company.inventory.model.vo.sales.SalesQuotationVO;
import com.company.inventory.service.SalesQuotationService;

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
 * 销售报价单接口(V25,无审批:admin+operator 可建可编可发送/转订单/作废)。
 *
 * @author inventory
 */
@Tag(name = "销售报价单")
@RestController
@RequestMapping("/api/v1/sales-quotations")
@Validated
public class SalesQuotationController {

    /** 销售报价单服务。 */
    private final SalesQuotationService salesQuotationService;

    /**
     * 构造控制器。
     *
     * @param salesQuotationService 销售报价单服务
     */
    public SalesQuotationController(SalesQuotationService salesQuotationService) {
        this.salesQuotationService = salesQuotationService;
    }

    /**
     * 销售报价单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "销售报价单列表")
    @GetMapping
    public PageResult<SalesQuotationVO> list(@Valid SalesQuotationQuery query) {
        return salesQuotationService.list(query);
    }

    /**
     * 新建销售报价单(草稿)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建报价单
     */
    @Operation(summary = "新建销售报价单")
    @PostMapping
    @RequirePerm("quotation:create")
    public SalesQuotationVO create(@Valid @RequestBody SalesQuotationCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesQuotationService.create(dto, username);
    }

    /**
     * 报价单详情。
     *
     * @param id 报价单 ID
     * @return 报价单
     */
    @Operation(summary = "销售报价单详情")
    @GetMapping("/{id}")
    public SalesQuotationVO get(@PathVariable long id) {
        return salesQuotationService.get(id);
    }

    /**
     * 编辑销售报价单(仅 draft)。
     *
     * @param id      报价单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的报价单
     */
    @Operation(summary = "编辑销售报价单")
    @PutMapping("/{id}")
    @RequirePerm("quotation:edit")
    public SalesQuotationVO update(@PathVariable long id,
            @Valid @RequestBody SalesQuotationCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesQuotationService.update(id, dto, username);
    }

    /**
     * 标记已发送(draft → sent)。
     *
     * @param id      报价单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的报价单
     */
    @Operation(summary = "标记销售报价单已发送")
    @PostMapping("/{id}/send")
    @RequirePerm("quotation:send")
    public SalesQuotationVO send(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesQuotationService.markSent(id, username);
    }

    /**
     * 作废报价单(draft/sent → voided)。
     *
     * @param id      报价单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的报价单
     */
    @Operation(summary = "作废销售报价单")
    @PostMapping("/{id}/void")
    @RequirePerm("quotation:void")
    public SalesQuotationVO voidDoc(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesQuotationService.voidDoc(id, username);
    }
}