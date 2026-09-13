package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.settlement.PaymentCreateDTO;
import com.company.inventory.model.query.PaymentQuery;
import com.company.inventory.model.vo.settlement.PaymentVO;
import com.company.inventory.model.vo.settlement.UnsettledInvoiceVO;
import com.company.inventory.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 付款/收款单接口(V18 结算域):一表一 type 区分,create 即生效,核销行挂 confirmed 正票。
 *
 * @author inventory
 */
@Tag(name = "付款/收款")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    /** 付款/收款单服务。 */
    private final PaymentService paymentService;

    /**
     * 构造控制器。
     *
     * @param paymentService 付款/收款单服务
     */
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 新建付款/收款单(admin/operator)。
     *
     * @param dto     入参(头 + 核销行)
     * @param request 请求(取当前用户名)
     * @return 单据
     */
    @Operation(summary = "新建付款/收款单(核销行挂正票,防超核)")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public PaymentVO create(@Valid @RequestBody PaymentCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return paymentService.create(dto, username);
    }

    /**
     * 作废付款/收款单(释放核销额度;admin/operator)。
     *
     * @param id      单据 ID
     * @param request 请求(取当前用户名)
     * @return 单据
     */
    @Operation(summary = "作废付款/收款单")
    @PostMapping("/{id}/void")
    @RequireRole({"admin", "operator"})
    public PaymentVO voidPayment(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return paymentService.voidPayment(id, username);
    }

    /**
     * 付款/收款单分页列表。
     *
     * @param query 查询条件(类型/对方/日期范围/状态/单号)
     * @return 分页结果
     */
    @Operation(summary = "付款/收款单列表")
    @GetMapping
    public PageResult<PaymentVO> list(@Valid PaymentQuery query) {
        return paymentService.list(query);
    }

    /**
     * 付款/收款单详情(带核销行)。
     *
     * @param id 单据 ID
     * @return 单据
     */
    @Operation(summary = "付款/收款单详情")
    @GetMapping("/{id}")
    public PaymentVO get(@PathVariable long id) {
        return paymentService.get(id);
    }

    /**
     * 某对方 confirmed 正票未核销额(新建核销行选择区用)。
     *
     * @param payType 单据类型(payment | receipt)
     * @param partyId 对方 ID
     * @return 未核销票列表
     */
    @Operation(summary = "可核销发票(未核销额)")
    @GetMapping("/unsettled-invoices")
    public List<UnsettledInvoiceVO> unsettledInvoices(@RequestParam String payType,
            @RequestParam long partyId) {
        return paymentService.unsettledInvoices(payType, partyId);
    }
}
