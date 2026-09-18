package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.settlement.InvoiceCreateDTO;
import com.company.inventory.model.dto.settlement.InvoiceUpdateDTO;
import com.company.inventory.model.query.InvoiceQuery;
import com.company.inventory.model.vo.settlement.InvoiceVO;
import com.company.inventory.model.vo.settlement.InvoiceableLineVO;
import com.company.inventory.service.InvoiceService;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发票接口(V18 结算域):手工登记正票 + 确认/作废;退货自动生成红字凭单走退货单事务。
 *
 * @author inventory
 */
@Tag(name = "发票")
@RestController
@RequestMapping("/api/v1/invoices")
@Validated
public class InvoiceController {

    /** 发票服务。 */
    private final InvoiceService invoiceService;

    /**
     * 构造控制器。
     *
     * @param invoiceService 发票服务
     */
    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * 新建发票(admin/operator)。
     *
     * @param dto     入参(头 + 行)
     * @param request 请求(取当前用户名)
     * @return 发票
     */
    @Operation(summary = "新建发票(手工登记正票)")
    @PostMapping
    @RequirePerm("invoices:create")
    public InvoiceVO create(@Valid @RequestBody InvoiceCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return invoiceService.create(dto, username);
    }

    /**
     * 修改发票(仅 draft/mismatch;admin/operator)。
     *
     * @param id      发票 ID
     * @param dto     入参(全量行,覆盖式)
     * @param request 请求(取当前用户名)
     * @return 发票
     */
    @Operation(summary = "修改发票(仅草稿/差异)")
    @PutMapping("/{id}")
    @RequirePerm("invoices:edit")
    public InvoiceVO update(@PathVariable long id, @Valid @RequestBody InvoiceUpdateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return invoiceService.update(id, dto, username);
    }

    /**
     * 确认发票(draft/mismatch → confirmed;admin/operator)。
     *
     * @param id      发票 ID
     * @param request 请求(取当前用户名)
     * @return 发票
     */
    @Operation(summary = "确认发票(仍有差异行 400)")
    @PostMapping("/{id}/confirm")
    @RequirePerm("invoices:confirm")
    public InvoiceVO confirm(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return invoiceService.confirm(id, username);
    }

    /**
     * 作废发票(留痕,释放额度;admin/operator)。
     *
     * @param id      发票 ID
     * @param request 请求(取当前用户名)
     * @return 发票
     */
    @Operation(summary = "作废发票")
    @PostMapping("/{id}/void")
    @RequirePerm("invoices:void")
    public InvoiceVO voidInvoice(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return invoiceService.voidInvoice(id, username);
    }

    /**
     * 发票分页列表。
     *
     * @param query 查询条件(类型/对方/日期范围/状态/单号)
     * @return 分页结果
     */
    @Operation(summary = "发票列表")
    @GetMapping
    public PageResult<InvoiceVO> list(@Valid InvoiceQuery query) {
        return invoiceService.list(query);
    }

    /**
     * 发票详情(带行)。
     *
     * @param id 发票 ID
     * @return 发票
     */
    @Operation(summary = "发票详情")
    @GetMapping("/{id}")
    public InvoiceVO get(@PathVariable long id) {
        return invoiceService.get(id);
    }

    /**
     * 可挂票源单据行(未开票/部分开票,新建发票选择区用)。
     *
     * @param invoiceType 发票类型(purchase | sales)
     * @param partyId     对方 ID
     * @return 可挂票行列表
     */
    @Operation(summary = "可挂票源单据行")
    @GetMapping("/invoiceable-lines")
    public List<InvoiceableLineVO> invoiceableLines(@RequestParam String invoiceType,
            @RequestParam long partyId) {
        return invoiceService.invoiceableLines(invoiceType, partyId);
    }
}
