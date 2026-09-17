package com.company.inventory.controller;

import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.rbac.MenuCreateDTO;
import com.company.inventory.model.dto.rbac.MenuUpdateDTO;
import com.company.inventory.model.vo.rbac.MenuNodeVO;
import com.company.inventory.service.rbac.MenuService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 菜单管理接口(仅 admin):菜单树全量查看 + 增删改(删除有子节点禁删,连带清 role_menu)。
 *
 * @author inventory
 */
@Tag(name = "菜单")
@RestController
@RequestMapping("/api/v1/menus")
@RequireRole("admin")
@Validated
public class MenuController {

    /** 菜单服务。 */
    private final MenuService menuService;

    /**
     * 构造控制器。
     *
     * @param menuService 菜单服务
     */
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 全量菜单树(含 button 子节点,管理页用)。
     *
     * @return 顶级节点列表
     */
    @Operation(summary = "全量菜单树")
    @GetMapping("/tree")
    public List<MenuNodeVO> tree() {
        return menuService.tree();
    }

    /**
     * 新建菜单。
     *
     * @param dto 入参
     * @return 新菜单节点
     */
    @Operation(summary = "新建菜单")
    @PostMapping
    public MenuNodeVO create(@Valid @RequestBody MenuCreateDTO dto) {
        return menuService.create(dto);
    }

    /**
     * 更新菜单(menu_code 不可改)。
     *
     * @param id  菜单 ID
     * @param dto 入参(可选字段)
     * @return 更新后节点
     */
    @Operation(summary = "更新菜单")
    @PutMapping("/{id}")
    public MenuNodeVO update(@PathVariable long id, @Valid @RequestBody MenuUpdateDTO dto) {
        return menuService.update(id, dto);
    }

    /**
     * 删除菜单(有子节点禁删;连带清 role_menu)。
     *
     * @param id 菜单 ID
     * @return {ok:true}
     */
    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        menuService.delete(id);
        return Map.of("ok", Boolean.TRUE);
    }
}
