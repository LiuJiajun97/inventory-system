package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePerm;
import com.company.inventory.model.dto.price.PriceListCreateDTO;
import com.company.inventory.model.query.PriceListQuery;
import com.company.inventory.model.vo.price.EffectivePriceVO;
import com.company.inventory.model.vo.price.PriceListDetailVO;
import com.company.inventory.model.vo.price.PriceListVO;
import com.company.inventory.service.PriceListService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 价目表接口(V26:读跟随登录,写绑 price:edit)。
 *
 * @author inventory
 */
@Tag(name = "价目表")
@RestController
@RequestMapping("/api/v1/price-lists")
@Validated
public class PriceListController {

    /** 价目表服务。 */
    private final PriceListService priceListService;

    /**
     * 构造控制器。
     *
     * @param priceListService 价目表服务
     */
    public PriceListController(PriceListService priceListService) {
        this.priceListService = priceListService;
    }

    /**
     * 价目表分页列表(owner_type 必填 + 对方单位关键字筛选)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "价目表列表")
    @GetMapping
    public PageResult<PriceListVO> list(@Valid PriceListQuery query) {
        return priceListService.list(query);
    }

    /**
     * 生效价目(单据新建选物品带价用,按物品去重取 valid_from 最近)。
     *
     * @param ownerType 对方类型:supplier / customer(必填)
     * @param ownerId   对方 ID(必填)
     * @param date      日期(可空默认今天,yyyy-MM-dd)
     * @return 生效价目行
     */
    @Operation(summary = "生效价目")
    @GetMapping("/effective")
    public List<EffectivePriceVO> effective(
            @RequestParam @NotBlank(message = "对方类型必填") String ownerType,
            @RequestParam @NotNull(message = "对方 ID 必填")
            @Positive(message = "对方 ID 必须为正数") Long ownerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return priceListService.effectivePrices(ownerType, ownerId, date);
    }

    /**
     * 价目表详情。
     *
     * @param id 价目表 ID
     * @return 详情
     */
    @Operation(summary = "价目表详情")
    @GetMapping("/{id}")
    public PriceListDetailVO get(@PathVariable long id) {
        return priceListService.get(id);
    }

    /**
     * 新建价目表。
     *
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 价目表
     */
    @Operation(summary = "新建价目表")
    @PostMapping
    @RequirePerm("price:edit")
    public PriceListVO create(@Valid @RequestBody PriceListCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return priceListService.create(dto, username);
    }

    /**
     * 编辑价目表(整表替换)。
     *
     * @param id      价目表 ID
     * @param dto     入参
     * @param request 请求(取当前用户名)
     * @return 价目表
     */
    @Operation(summary = "编辑价目表")
    @PutMapping("/{id}")
    @RequirePerm("price:edit")
    public PriceListVO update(@PathVariable long id, @Valid @RequestBody PriceListCreateDTO dto,
            HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        return priceListService.update(id, dto, username);
    }

    /**
     * 删除价目表(级联删行)。
     *
     * @param id      价目表 ID
     * @param request 请求(取当前用户名)
     */
    @Operation(summary = "删除价目表")
    @DeleteMapping("/{id}")
    @RequirePerm("price:edit")
    public void delete(@PathVariable long id, HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        priceListService.delete(id, username);
    }

}
