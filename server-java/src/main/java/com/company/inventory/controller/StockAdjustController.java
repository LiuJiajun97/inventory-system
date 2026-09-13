package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.adjust.StockAdjustCreateDTO;
import com.company.inventory.model.query.StockAdjustQuery;
import com.company.inventory.model.query.StockAdjustDocLineQuery;
import com.company.inventory.service.StockAdjustService;
import com.company.inventory.service.DocLineService;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;
import com.company.inventory.model.vo.adjust.StockAdjustDocLineVO;

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

import java.util.Map;

/**
 * 库存调整单接口(admin+operator 可建可提交可审批;审批人不得为制单人;审批即执行)。
 *
 * @author inventory
 */
@Tag(name = "库存调整单")
@RestController
@RequestMapping("/api/v1/stock-adjusts")
public class StockAdjustController {

    /** 库存调整单服务。 */
    private final StockAdjustService stockAdjustService;

    /** 明细行查询服务(V17 主表/明细切换)。 */
    private final DocLineService docLineService;

    /**
     * 构造控制器。
     *
     * @param stockAdjustService 库存调整单服务
     * @param docLineService 明细行查询服务
     */
    public StockAdjustController(StockAdjustService stockAdjustService, DocLineService docLineService) {
        this.stockAdjustService = stockAdjustService;
        this.docLineService = docLineService;
    }

    /**
     * 调整单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "调整单列表")
    @GetMapping
    public PageResult<StockAdjustDocVO> list(@Valid StockAdjustQuery query) {
        return stockAdjustService.list(query);
    }

    /**
     * 新建调整单(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建调整单
     */
    @Operation(summary = "新建调整单")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public StockAdjustDocVO create(@Valid @RequestBody StockAdjustCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stockAdjustService.create(dto, username);
    }

    /**
     * 编辑调整单(admin/operator,仅草稿/已驳回可编辑)。
     *
     * @param id      调整单 ID
     * @param dto     入参(与新建同结构)
     * @param request 请求(取当前用户名)
     * @return 更新后的调整单
     */
    @Operation(summary = "编辑调整单")
    @PutMapping("/{id}")
    @RequireRole({"admin", "operator"})
    public StockAdjustDocVO update(@PathVariable long id,
            @Valid @RequestBody StockAdjustCreateDTO dto, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stockAdjustService.update(id, dto, username);
    }

    /**
     * 调整单详情。
     *
     * @param id 调整单 ID
     * @return 调整单
     */
    @Operation(summary = "调整单详情")
    @GetMapping("/{id}")
    public StockAdjustDocVO get(@PathVariable long id) {
        return stockAdjustService.get(id);
    }

    /**
     * 提交审批(admin/operator)。
     *
     * @param id      调整单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的调整单
     */
    @Operation(summary = "提交调整单")
    @PostMapping("/{id}/submit")
    @RequireRole({"admin", "operator"})
    public StockAdjustDocVO submit(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stockAdjustService.submit(id, username);
    }

    /**
     * 审批执行(admin/operator:gain 入库/loss 出库,执行完成即 completed)。
     *
     * @param id      调整单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的调整单
     */
    @Operation(summary = "审批执行调整单")
    @PostMapping("/{id}/approve")
    @RequireRole({"admin", "operator"})
    public StockAdjustDocVO approve(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stockAdjustService.approve(id, username);
    }

    /**
     * 驳回(admin/operator,驳回原因必填)。
     *
     * @param id      调整单 ID
     * @param body    请求体({rejectReason})
     * @param request 请求(取当前用户名)
     * @return 更新后的调整单
     */
    @Operation(summary = "驳回调整单")
    @PostMapping("/{id}/reject")
    @RequireRole({"admin", "operator"})
    public StockAdjustDocVO reject(@PathVariable long id,
            @RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stockAdjustService.reject(id, body.get("rejectReason"), username);
    }

    /**
     * 作废(admin/operator)。
     *
     * @param id      调整单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的调整单
     */
    @Operation(summary = "作废调整单")
    @PostMapping("/{id}/void")
    @RequireRole({"admin", "operator"})
    public StockAdjustDocVO voidDoc(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stockAdjustService.voidDoc(id, username);
    }
    /**
     * 调整单明细行列表(V17 主表/明细切换,纯只读)。
     *
     * @param query 明细行查询条件
     * @return 分页结果
     */
    @Operation(summary = "调整单明细行列表")
    @GetMapping("/lines")
    public PageResult<StockAdjustDocLineVO> lines(@Valid StockAdjustDocLineQuery query) {
        return docLineService.listAdjustLines(query);
    }

}
