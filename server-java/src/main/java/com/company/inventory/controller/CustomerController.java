package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.customer.CustomerCreateDTO;
import com.company.inventory.model.dto.customer.CustomerUpdateDTO;
import com.company.inventory.model.query.CustomerQuery;
import com.company.inventory.model.vo.excel.ImportResultVO;
import com.company.inventory.service.CustomerService;
import com.company.inventory.service.ExportService;
import com.company.inventory.service.ImportService;
import com.company.inventory.model.vo.customer.CustomerVO;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客户主数据接口(admin 可建可改,其余角色只读)。
 *
 * @author inventory
 */
@Tag(name = "客户")
@RestController
@RequestMapping("/api/v1/customers")
@Validated
public class CustomerController {

    /** 客户服务。 */
    private final CustomerService customerService;

    /** 导入服务。 */
    private final ImportService importService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param customerService 客户服务
     * @param importService   导入服务
     * @param exportService   导出服务
     */
    public CustomerController(CustomerService customerService, ImportService importService,
            ExportService exportService) {
        this.customerService = customerService;
        this.importService = importService;
        this.exportService = exportService;
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
    @RequirePerm("customer:create")
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
    @RequirePerm("customer:edit")
    public CustomerVO update(@PathVariable long id, @Valid @RequestBody CustomerUpdateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return customerService.update(id, dto, username);
    }

    /**
     * 客户导入(仅 admin,逐行校验,成功行走既有创建链路)。
     *
     * @param file    xlsx 文件(列头须与模板一致)
     * @param request 请求(取当前用户名)
     * @return 导入结果(imported + 失败行汇总)
     */
    @Operation(summary = "客户导入")
    @PostMapping("/import")
    @RequirePerm("customer:import")
    public ImportResultVO importCustomers(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return importService.importCustomers(file, username);
    }

    /**
     * 客户导出(xlsx,不分页,权限与列表读一致)。
     *
     * @param query 列表查询条件(keyword,分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "客户导出")
    @GetMapping("/export")
    public void exportCustomers(@Valid CustomerQuery query, HttpServletResponse resp) {
        exportService.exportCustomers(query, resp);
    }

    /**
     * 客户导入模板下载(表头 + 1 行示例)。
     *
     * @param resp HTTP 响应(xlsx 流)
     */
    @Operation(summary = "客户导入模板")
    @GetMapping("/template")
    public void customerTemplate(HttpServletResponse resp) {
        importService.writeCustomerTemplate(resp);
    }
}
