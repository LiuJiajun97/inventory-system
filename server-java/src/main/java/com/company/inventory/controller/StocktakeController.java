package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.stocktake.StocktakeActualDTO;
import com.company.inventory.model.dto.stocktake.StocktakeCreateDTO;
import com.company.inventory.model.query.StocktakeDocQuery;
import com.company.inventory.model.query.StocktakeDocLineQuery;
import com.company.inventory.service.StocktakeService;
import com.company.inventory.service.DocLineService;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;
import com.company.inventory.model.vo.stocktake.StocktakeDocVO;
import com.company.inventory.model.vo.stocktake.StocktakeDocLineVO;

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

import java.util.List;
import java.util.Map;

/**
 * 盘点单接口(admin+operator 可建/录实盘/刷快照/生成调整单可审批;审批人不得为制单人)。
 *
 * @author inventory
 */
@Tag(name = "盘点单")
@RestController
@RequestMapping("/api/v1/stocktakes")
@Validated
public class StocktakeController {

    /** 盘点单服务。 */
    private final StocktakeService stocktakeService;

    /** 明细行查询服务(V17 主表/明细切换)。 */
    private final DocLineService docLineService;

    /**
     * 构造控制器。
     *
     * @param stocktakeService 盘点单服务
     * @param docLineService 明细行查询服务
     */
    public StocktakeController(StocktakeService stocktakeService, DocLineService docLineService) {
        this.stocktakeService = stocktakeService;
        this.docLineService = docLineService;
    }

    /**
     * 盘点单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "盘点单列表")
    @GetMapping
    public PageResult<StocktakeDocVO> list(@Valid StocktakeDocQuery query) {
        return stocktakeService.list(query);
    }

    /**
     * 新建盘点单(admin/operator,保存即生成 bookQty 快照)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 新建盘点单
     */
    @Operation(summary = "新建盘点单")
    @PostMapping
    @RequirePerm("stocktake:edit")
    public StocktakeDocVO create(@Valid @RequestBody StocktakeCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.create(dto, username);
    }

    /**
     * 盘点单详情。
     *
     * @param id 盘点单 ID
     * @return 盘点单
     */
    @Operation(summary = "盘点单详情")
    @GetMapping("/{id}")
    public StocktakeDocVO get(@PathVariable long id) {
        return stocktakeService.get(id);
    }

    /**
     * 录入实盘(draft/pending 可录)。
     *
     * @param id      盘点单 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 更新后的盘点单
     */
    @Operation(summary = "盘点实盘录入")
    @PutMapping("/{id}/actual")
    @RequirePerm("stocktake:edit")
    public StocktakeDocVO enterActual(@PathVariable long id, @Valid @RequestBody StocktakeActualDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.enterActual(id, dto, username);
    }

    /**
     * 刷新快照(draft 可刷)。
     *
     * @param id      盘点单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的盘点单
     */
    @Operation(summary = "盘点刷新快照")
    @PostMapping("/{id}/refresh-book")
    @RequirePerm("stocktake:edit")
    public StocktakeDocVO refreshBook(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.refreshBook(id, username);
    }

    /**
     * 差异生成调整单(盘盈/盘亏各一张草稿)。
     *
     * @param id      盘点单 ID
     * @param request 请求(取当前用户名)
     * @return 生成的调整单列表
     */
    @Operation(summary = "盘点差异生成调整单")
    @PostMapping("/{id}/adjust")
    @RequirePerm("stocktake:edit")
    public List<StockAdjustDocVO> adjust(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.generateAdjust(id, username);
    }

    /**
     * 提交审批(admin/operator)。
     *
     * @param id      盘点单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的盘点单
     */
    @Operation(summary = "提交盘点单")
    @PostMapping("/{id}/submit")
    @RequirePerm("stocktake:submit")
    public StocktakeDocVO submit(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.submit(id, username);
    }

    /**
     * 审批通过(admin/operator,审批人≠制单人)。
     *
     * @param id      盘点单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的盘点单
     */
    @Operation(summary = "审批通过盘点单")
    @PostMapping("/{id}/approve")
    @RequirePerm("stocktake:approve")
    public StocktakeDocVO approve(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.approve(id, username);
    }

    /**
     * 驳回(admin/operator,驳回原因必填)。
     *
     * @param id      盘点单 ID
     * @param body    请求体({rejectReason})
     * @param request 请求(取当前用户名)
     * @return 更新后的盘点单
     */
    @Operation(summary = "驳回盘点单")
    @PostMapping("/{id}/reject")
    @RequirePerm("stocktake:reject")
    public StocktakeDocVO reject(@PathVariable long id,
            @RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.reject(id, body.get("rejectReason"), username);
    }

    /**
     * 作废(admin/operator)。
     *
     * @param id      盘点单 ID
     * @param request 请求(取当前用户名)
     * @return 更新后的盘点单
     */
    @Operation(summary = "作废盘点单")
    @PostMapping("/{id}/void")
    @RequirePerm("stocktake:void")
    public StocktakeDocVO voidDoc(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return stocktakeService.voidDoc(id, username);
    }
    /**
     * 盘点单明细行列表(V17 主表/明细切换,纯只读)。
     *
     * @param query 明细行查询条件
     * @return 分页结果
     */
    @Operation(summary = "盘点单明细行列表")
    @GetMapping("/lines")
    public PageResult<StocktakeDocLineVO> lines(@Valid StocktakeDocLineQuery query) {
        return docLineService.listStocktakeLines(query);
    }

}
