package com.company.inventory.controller;

import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.config.RequirePermission;
import com.company.inventory.model.dto.auth.ChangePasswordDTO;
import com.company.inventory.model.dto.auth.LoginDTO;
import com.company.inventory.model.vo.rbac.UserMenuTreeVO;
import com.company.inventory.service.AuthService;
import com.company.inventory.service.rbac.MenuService;
import com.company.inventory.model.vo.auth.LoginVO;
import com.company.inventory.model.vo.auth.MeVO;















import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证接口:登录(免鉴权)、改自己密码、当前用户信息、当前用户菜单树。
 *
 * @author inventory
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    /** 认证服务。 */
    private final AuthService authService;

    /** 菜单服务(当前用户菜单树)。 */
    private final MenuService menuService;

    /**
     * 构造控制器。
     *
     * @param authService 认证服务
     * @param menuService 菜单服务
     */
    public AuthController(AuthService authService, MenuService menuService) {
        this.authService = authService;
        this.menuService = menuService;
    }

    /**
     * 登录。
     *
     * @param dto 用户名/密码
     * @return token + 用户
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public LoginVO login(@Valid @RequestBody LoginDTO dto) {
        return authService.login(dto.username(), dto.password());
    }

    /**
     * 修改自己的密码。
     *
     * @param dto  原密码/新密码
     * @param request 请求(取当前用户 ID)
     * @return {ok:true}
     */
    @Operation(summary = "修改自己的密码")
    @PostMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        authService.changePassword(userId, dto.oldPassword(), dto.newPassword());
        return Map.of("ok", Boolean.TRUE);
    }

    /**
     * 当前登录用户信息。
     *
     * @param request 请求
     * @return 当前用户
     */
    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public MeVO me(HttpServletRequest request) {
        Long id = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        String username = (String) request.getAttribute(JwtInterceptor.ATTR_USERNAME);
        String name = (String) request.getAttribute(JwtInterceptor.ATTR_NAME);
        String role = (String) request.getAttribute(JwtInterceptor.ATTR_ROLE);
        return new MeVO(id, username, name, role);
    }

    /**
     * 当前用户菜单树(多角色并集,仅目录+菜单,附各菜单下按钮权限码列表,前端导航用)。
     *
     * @param request 请求
     * @return 顶级节点列表
     */
    @Operation(summary = "当前用户菜单树")
    @GetMapping("/menus")
    public List<UserMenuTreeVO> menus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        return menuService.userMenuTree(userId);
    }

    /**
     * 权限码探针:校验 @RequirePermission 生效(权限码 purchase-order:approve)。
     *
     * @return {ok:true}
     */
    @Operation(summary = "权限码探针")
    @GetMapping("/perm-check")
    @RequirePermission("purchase-order:approve")
    public Map<String, Object> permCheck() {
        return Map.of("ok", Boolean.TRUE);
    }
}
