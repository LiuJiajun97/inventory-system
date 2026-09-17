package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.inbound.InboundCreateDTO;
import com.company.inventory.model.query.InboundDocQuery;
import com.company.inventory.model.query.InboundDocLineQuery;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.DocLineService;
import com.company.inventory.service.ExportService;
import com.company.inventory.model.vo.inbound.InboundDocCreatedVO;
import com.company.inventory.model.vo.inbound.InboundDocVO;
import com.company.inventory.model.vo.inbound.InboundDocLineVO;

















import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入库单接口。
 *
 * @author inventory
 */
@Tag(name = "入库")
@RestController
@RequestMapping("/api/v1/inbound")
@Validated
public class InboundController {

    /** 入库单服务。 */
    private final InboundService inboundService;

    /** 明细行查询服务(V17 主表/明细切换)。 */
    private final DocLineService docLineService;

    /** 导出服务。 */
    private final ExportService exportService;

    /**
     * 构造控制器。
     *
     * @param inboundService 入库单服务
     * @param docLineService 明细行查询服务
     * @param exportService 导出服务
     */
    public InboundController(InboundService inboundService,
            ExportService exportService, DocLineService docLineService) {
        this.inboundService = inboundService;
        this.docLineService = docLineService;
        this.exportService = exportService;
    }

    /**
     * 新建入库单(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 单据头
     */
    @Operation(summary = "新建入库单")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public InboundDocCreatedVO create(@Valid @RequestBody InboundCreateDTO dto,
                                      HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return inboundService.create(dto, username);
    }

    /**
     * 入库单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "入库单列表")
    @GetMapping
    public PageResult<InboundDocVO> list(@Valid InboundDocQuery query) {
        return inboundService.list(query);
    }

    /**
     * 入库单详情。
     *
     * @param id 单据 ID
     * @return 单据
     */
    @Operation(summary = "入库单详情")
    @GetMapping("/{id}")
    public InboundDocVO get(@PathVariable long id) {
        return inboundService.get(id);
    }


    /**
     * 入库单导出(xlsx,不分页,权限与列表读一致)。
     *
     * @param query 列表查询条件(分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    @Operation(summary = "入库单导出")
    @GetMapping("/export")
    public void export(@Valid InboundDocQuery query, HttpServletResponse resp) {
        exportService.exportInbound(query, resp);
    }
    /**
     * 入库单明细行列表(V17 主表/明细切换,纯只读)。
     *
     * @param query 明细行查询条件
     * @return 分页结果
     */
    @Operation(summary = "入库单明细行列表")
    @GetMapping("/lines")
    public PageResult<InboundDocLineVO> lines(@Valid InboundDocLineQuery query) {
        return docLineService.listInboundLines(query);
    }

}
