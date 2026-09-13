package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.opening.OpeningStockCreateDTO;
import com.company.inventory.model.query.OpeningStockQuery;
import com.company.inventory.model.vo.opening.OpeningStockCreatedVO;
import com.company.inventory.model.vo.opening.OpeningStockDocVO;
import com.company.inventory.service.OpeningStockService;

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
 * 期初库存接口(create 即过账,权限与出入库 create 一致)。
 *
 * @author inventory
 */
@Tag(name = "期初库存")
@RestController
@RequestMapping("/api/v1/opening")
public class OpeningStockController {

    /** 期初单服务。 */
    private final OpeningStockService openingStockService;

    /**
     * 构造控制器。
     *
     * @param openingStockService 期初单服务
     */
    public OpeningStockController(OpeningStockService openingStockService) {
        this.openingStockService = openingStockService;
    }

    /**
     * 新建期初单并过账(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 单据头
     */
    @Operation(summary = "新建期初单并过账")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public OpeningStockCreatedVO create(@Valid @RequestBody OpeningStockCreateDTO dto,
                                        HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return openingStockService.create(dto, username);
    }

    /**
     * 期初单分页列表。
     *
     * @param query 查询条件(warehouseId/docNo/status/from/to/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "期初单列表")
    @GetMapping
    public PageResult<OpeningStockDocVO> list(@Valid OpeningStockQuery query) {
        return openingStockService.list(query);
    }

    /**
     * 期初单详情。
     *
     * @param id 单据 ID
     * @return 单据
     */
    @Operation(summary = "期初单详情")
    @GetMapping("/{id}")
    public OpeningStockDocVO get(@PathVariable long id) {
        return openingStockService.get(id);
    }
}
