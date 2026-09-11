package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequireRole;
import com.company.inventory.dto.inbound.InboundCreateDTO;
import com.company.inventory.query.InboundDocQuery;
import com.company.inventory.service.InboundService;
import com.company.inventory.vo.inbound.InboundDocCreatedVO;
import com.company.inventory.vo.inbound.InboundDocVO;

















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
 * 入库单接口。
 *
 * @author inventory
 */
@Tag(name = "入库")
@RestController
@RequestMapping("/api/v1/inbound")
public class InboundController {

    /** 入库单服务。 */
    private final InboundService inboundService;

    /**
     * 构造控制器。
     *
     * @param inboundService 入库单服务
     */
    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
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
}
