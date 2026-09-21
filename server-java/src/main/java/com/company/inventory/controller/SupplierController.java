package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierUpdateDTO;
import com.company.inventory.model.query.SupplierQuery;
import com.company.inventory.model.vo.excel.ImportResultVO;
import com.company.inventory.service.ExportService;
import com.company.inventory.service.ImportService;
import com.company.inventory.service.SupplierService;
import com.company.inventory.model.vo.supplier.SupplierVO;

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
 * 供应商主数据接口(admin 可建可改,其余角色只读)。
 *
 * @author inventory
 */
@Tag(name = "供应商")
@RestController
@RequestMapping("/api/v1/suppliers")
@Validated
public class SupplierController {

    /** 供应商服务。 */
    private final SupplierService supplierService;

    /** 导入服务。 */
    private final ImportService importService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param supplierService 供应商服务
     * @param importService   导入服务
     * @param exportService   导出服务
     */
    public SupplierController(SupplierService supplierService, ImportService importService,
            ExportService exportService) {
        this.supplierService = supplierService;
        this.importService = importService;
        this.exportService = exportService;
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
    @RequirePerm("supplier:create")
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
    @RequirePerm("supplier:edit")
    public SupplierVO update(@PathVariable long id, @Valid @RequestBody SupplierUpdateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return supplierService.update(id, dto, username);
    }

    /**
     * 供应商导入(仅 admin,逐行校验,成功行走既有创建链路)。
     *
     * @param file    xlsx 文件(列头须与模板一致)
     * @param request 请求(取当前用户名)
     * @return 导入结果(imported + 失败行汇总)
     */
    @Operation(summary = "供应商导入")
    @PostMapping("/import")
    @RequirePerm("supplier:import")
    public ImportResultVO importSuppliers(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return importService.importSuppliers(file, username);
    }

    /**
     * 供应商导出(xlsx,不分页,权限与列表读一致)。
     *
     * @param query 列表查询条件(keyword,分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "供应商导出")
    @GetMapping("/export")
    public void exportSuppliers(@Valid SupplierQuery query, HttpServletResponse resp) {
        exportService.exportSuppliers(query, resp);
    }

    /**
     * 供应商导入模板下载(表头 + 1 行示例)。
     *
     * @param resp HTTP 响应(xlsx 流)
     */
    @Operation(summary = "供应商导入模板")
    @GetMapping("/template")
    public void supplierTemplate(HttpServletResponse resp) {
        importService.writeSupplierTemplate(resp);
    }
}
