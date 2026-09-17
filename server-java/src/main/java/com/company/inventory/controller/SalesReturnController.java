package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.returns.SalesReturnCreateDTO;
import com.company.inventory.model.query.SalesReturnQuery;
import com.company.inventory.model.query.SalesReturnLinesQuery;
import com.company.inventory.service.SalesReturnService;
import com.company.inventory.service.DocLineService;
import com.company.inventory.model.vo.returns.SalesReturnCreatedVO;
import com.company.inventory.model.vo.returns.SalesReturnVO;
import com.company.inventory.model.vo.returns.SalesReturnLinesVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售退货单接口(create 即过账,无草稿/作废)。
 *
 * @author inventory
 */
@Tag(name = "销售退货")
@RestController
@RequestMapping("/api/v1/sales-returns")
@Validated
public class SalesReturnController {

    /** 销售退货单服务。 */
    private final SalesReturnService salesReturnService;

    /** 明细行查询服务(V17 主表/明细切换)。 */
    private final DocLineService docLineService;

    /**
     * 构造控制器。
     *
     * @param salesReturnService 销售退货单服务
     * @param docLineService 明细行查询服务
     */
    public SalesReturnController(SalesReturnService salesReturnService, DocLineService docLineService) {
        this.salesReturnService = salesReturnService;
        this.docLineService = docLineService;
    }

    /**
     * 新建销售退货单并过账(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 创建结果(含联动入库单号)
     */
    @Operation(summary = "新建销售退货单(过账)")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public SalesReturnCreatedVO create(@Valid @RequestBody SalesReturnCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return salesReturnService.create(dto, username);
    }

    /**
     * 销售退货单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "销售退货单列表")
    @GetMapping
    public PageResult<SalesReturnVO> list(@Valid SalesReturnQuery query) {
        return salesReturnService.list(query);
    }

    /**
     * 销售退货单详情。
     *
     * @param id 单据 ID
     * @return 单据
     */
    @Operation(summary = "销售退货单详情")
    @GetMapping("/{id}")
    public SalesReturnVO get(@PathVariable long id) {
        return salesReturnService.get(id);
    }
    /**
     * 销售退货单明细行列表(V17 主表/明细切换,纯只读)。
     *
     * @param query 明细行查询条件
     * @return 分页结果
     */
    @Operation(summary = "销售退货单明细行列表")
    @GetMapping("/lines")
    public PageResult<SalesReturnLinesVO> lines(@Valid SalesReturnLinesQuery query) {
        return docLineService.listSalesReturnLines(query);
    }

}
