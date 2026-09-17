package com.company.inventory.controller;

import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.rbac.RoleCreateDTO;
import com.company.inventory.model.dto.rbac.RoleMenuDTO;
import com.company.inventory.model.dto.rbac.RoleUpdateDTO;
import com.company.inventory.model.vo.rbac.RoleVO;
import com.company.inventory.service.rbac.RoleService;

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
 * 角色管理接口(仅 admin):角色 CRUD + 角色-菜单树全量分配。
 *
 * <p>内置角色(admin/operator/viewer)禁删且禁改 role_code。</p>
 *
 * @author inventory
 */
@Tag(name = "角色")
@RestController
@RequestMapping("/api/v1/roles")
@RequireRole("admin")
@Validated
public class RoleController {

    /** 角色服务。 */
    private final RoleService roleService;

    /**
     * 构造控制器。
     *
     * @param roleService 角色服务
     */
    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 角色列表(不分页,管理页用)。
     *
     * @return 角色列表
     */
    @Operation(summary = "角色列表")
    @GetMapping
    public List<RoleVO> list() {
        return roleService.list();
    }

    /**
     * 角色详情。
     *
     * @param id 角色 ID
     * @return 角色
     */
    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    public RoleVO get(@PathVariable long id) {
        return roleService.getById(id);
    }

    /**
     * 新建角色。
     *
     * @param dto 入参
     * @return 新角色
     */
    @Operation(summary = "新建角色")
    @PostMapping
    public RoleVO create(@Valid @RequestBody RoleCreateDTO dto) {
        return roleService.create(dto);
    }

    /**
     * 更新角色(内置角色禁改 role_code)。
     *
     * @param id  角色 ID
     * @param dto 入参(可选字段)
     * @return 更新后角色
     */
    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public RoleVO update(@PathVariable long id, @Valid @RequestBody RoleUpdateDTO dto) {
        return roleService.update(id, dto);
    }

    /**
     * 删除角色(内置禁删,有用户绑定禁删)。
     *
     * @param id 角色 ID
     * @return {ok:true}
     */
    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        roleService.delete(id);
        return Map.of("ok", Boolean.TRUE);
    }

    /**
     * 全量替换角色的菜单绑定(空数组 = 清空)。
     *
     * @param id  角色 ID
     * @param dto 菜单 ID 数组
     * @return {ok:true}
     */
    @Operation(summary = "角色-菜单全量分配")
    @PutMapping("/{id}/menus")
    public Map<String, Object> assignMenus(@PathVariable long id,
                                           @Valid @RequestBody RoleMenuDTO dto) {
        roleService.assignMenus(id, dto.menuIds());
        return Map.of("ok", Boolean.TRUE);
    }

    /**
     * 查询角色的菜单 ID 列表(角色分配页回显用)。
     *
     * @param id 角色 ID
     * @return 菜单 ID 列表
     */
    @Operation(summary = "角色已绑定菜单 ID")
    @GetMapping("/{id}/menus")
    public List<Long> menuIds(@PathVariable long id) {
        return roleService.menuIdsOf(id);
    }
}
