package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.dto.supplier.SupplierCreateDTO;
import com.company.inventory.dto.supplier.SupplierUpdateDTO;
import com.company.inventory.query.SupplierQuery;
import com.company.inventory.service.SupplierService;
import com.company.inventory.vo.supplier.SupplierVO;

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
 * 供应商主数据接口(admin 可建可改,其余角色只读)。
 *
 * @author inventory
 */
@Tag(name = "供应商")
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    /** 供应商服务。 */
    private final SupplierService supplierService;

    /**
     * 构造控制器。
     *
     * @param supplierService 供应商服务
     */
    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /**
     * 供应商分页列表。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "供应商列表")
    @GetMapping
    public PageResult<SupplierVO> list(@Valid SupplierQuery query) {
        return supplierService.list(query);
    }

    /**
     * 供应商详情。
     *
     * @param id 供应商 ID
     * @return 供应商
     */
    @Operation(summary = "供应商详情")
    @GetMapping("/{id}")
    public SupplierVO get(@PathVariable long id) {
        return supplierService.get(id);
    }

    /**
     * 新建供应商(仅 admin)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建供应商
     */
    @Operation(summary = "新建供应商")
    @PostMapping
    @RequireRole("admin")
    public SupplierVO create(@Valid @RequestBody SupplierCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return supplierService.create(dto, username);
    }

    /**
     * 编辑供应商(仅 admin,编码不可改)。
     *
     * @param id      供应商 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的供应商
     */
    @Operation(summary = "编辑供应商")
    @PutMapping("/{id}")
    @RequireRole("admin")
    public SupplierVO update(@PathVariable long id, @Valid @RequestBody SupplierUpdateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return supplierService.update(id, dto, username);
    }
}
