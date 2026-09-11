package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.outbound.OutboundCreateDTO;
import com.company.inventory.model.query.OutboundDocQuery;
import com.company.inventory.service.OutboundService;
import com.company.inventory.model.vo.outbound.OutboundDocCreatedVO;
import com.company.inventory.model.vo.outbound.OutboundDocVO;

















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
 * 出库单接口。
 *
 * @author inventory
 */
@Tag(name = "出库")
@RestController
@RequestMapping("/api/v1/outbound")
public class OutboundController {

    /** 出库单服务。 */
    private final OutboundService outboundService;

    /**
     * 构造控制器。
     *
     * @param outboundService 出库单服务
     */
    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    /**
     * 新建出库单(admin/operator)。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 单据头
     */
    @Operation(summary = "新建出库单")
    @PostMapping
    @RequireRole({"admin", "operator"})
    public OutboundDocCreatedVO create(@Valid @RequestBody OutboundCreateDTO dto,
                                       HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return outboundService.create(dto, username);
    }

    /**
     * 出库单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "出库单列表")
    @GetMapping
    public PageResult<OutboundDocVO> list(@Valid OutboundDocQuery query) {
        return outboundService.list(query);
    }

    /**
     * 出库单详情。
     *
     * @param id 单据 ID
     * @return 单据
     */
    @Operation(summary = "出库单详情")
    @GetMapping("/{id}")
    public OutboundDocVO get(@PathVariable long id) {
        return outboundService.get(id);
    }
}
