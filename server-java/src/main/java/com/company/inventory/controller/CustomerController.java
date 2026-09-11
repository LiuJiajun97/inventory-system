package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.customer.CustomerCreateDTO;
import com.company.inventory.model.dto.customer.CustomerUpdateDTO;
import com.company.inventory.model.query.CustomerQuery;
import com.company.inventory.service.CustomerService;
import com.company.inventory.model.vo.customer.CustomerVO;

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
 * 客户主数据接口(admin 可建可改,其余角色只读)。
 *
 * @author inventory
 */
@Tag(name = "客户")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    /** 客户服务。 */
    private final CustomerService customerService;

    /**
     * 构造控制器。
     *
     * @param customerService 客户服务
     */
    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * 客户分页列表。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "客户列表")
    @GetMapping
    public PageResult<CustomerVO> list(@Valid CustomerQuery query) {
        return customerService.list(query);
    }

    /**
     * 客户详情。
     *
     * @param id 客户 ID
     * @return 客户
     */
    @Operation(summary = "客户详情")
    @GetMapping("/{id}")
    public CustomerVO get(@PathVariable long id) {
        return customerService.get(id);
    }

    /**
     * 新建客户(仅 admin)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建客户
     */
    @Operation(summary = "新建客户")
    @PostMapping
    @RequireRole("admin")
    public CustomerVO create(@Valid @RequestBody CustomerCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return customerService.create(dto, username);
    }

    /**
     * 编辑客户(仅 admin,编码不可改)。
     *
     * @param id      客户 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的客户
     */
    @Operation(summary = "编辑客户")
    @PutMapping("/{id}")
    @RequireRole("admin")
    public CustomerVO update(@PathVariable long id, @Valid @RequestBody CustomerUpdateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return customerService.update(id, dto, username);
    }
}
