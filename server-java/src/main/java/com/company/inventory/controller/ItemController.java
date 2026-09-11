package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.RequireRole;
import com.company.inventory.dto.item.ItemCreateDTO;
import com.company.inventory.dto.item.ItemUpdateDTO;
import com.company.inventory.query.ItemQuery;
import com.company.inventory.service.ItemService;
import com.company.inventory.vo.item.ItemVO;











import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物品接口。
 *
 * @author inventory
 */
@Tag(name = "物品")
@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    /** 物品服务。 */
    private final ItemService itemService;

    /**
     * 构造控制器。
     *
     * @param itemService 物品服务
     */
    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * 物品列表(分页)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "物品列表")
    @GetMapping
    public PageResult<ItemVO> list(@Valid ItemQuery query) {
        return itemService.list(query);
    }

    /**
     * 物品详情。
     *
     * @param id 物品 ID
     * @return 物品
     */
    @Operation(summary = "物品详情")
    @GetMapping("/{id}")
    public ItemVO get(@PathVariable long id) {
        return itemService.get(id);
    }

    /**
     * 新建物品(仅 admin)。
     *
     * @param dto 入参
     * @return 新建物品
     */
    @Operation(summary = "新建物品")
    @PostMapping
    @RequireRole("admin")
    public ItemVO create(@Valid @RequestBody ItemCreateDTO dto) {
        return itemService.create(dto);
    }

    /**
     * 编辑物品(仅 admin,编码不可改)。
     *
     * @param id  物品 ID
     * @param dto 入参
     * @return 更新后的物品
     */
    @Operation(summary = "编辑物品")
    @PutMapping("/{id}")
    @RequireRole("admin")
    public ItemVO update(@PathVariable long id, @Valid @RequestBody ItemUpdateDTO dto) {
        return itemService.update(id, dto);
    }
}
