package com.company.inventory.controller;

import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.service.rbac.UserTablePrefService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 表格偏好接口(登录即可,非 admin 功能:列宽拖拽/列显隐/列序按用户持久化)。
 *
 * <p>userId 从 {@link JwtInterceptor} 写入的请求属性取(拦截器已保证登录态),
 * 故本类不加 @RequireRole/@RequirePermission。</p>
 *
 * @author inventory
 */
@Tag(name = "表格偏好")
@RestController
@RequestMapping("/api/v1/table-prefs")
public class TablePrefController {

    /** 表格偏好服务。 */
    private final UserTablePrefService tablePrefService;

    /**
     * 构造控制器。
     *
     * @param tablePrefService 表格偏好服务
     */
    public TablePrefController(UserTablePrefService tablePrefService) {
        this.tablePrefService = tablePrefService;
    }

    /**
     * 当前用户全部页面列配置(pageKey→config)。
     *
     * @param request 请求(取登录用户 ID)
     * @return pageKey→config map(可能为空)
     */
    @Operation(summary = "当前用户全部页面列配置")
    @GetMapping
    public Map<String, Object> all(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        return tablePrefService.allForUser(userId == null ? -1L : userId);
    }

    /**
     * 保存指定页面的列配置(按 user_id + page_key upsert)。
     *
     * @param request 请求(取登录用户 ID)
     * @param pageKey 页面标识(前端路由段)
     * @param config  前端列配置对象({widths, hidden, order})
     * @return {ok:true}
     */
    @Operation(summary = "保存指定页面列配置")
    @PutMapping("/{pageKey}")
    public Map<String, Object> save(HttpServletRequest request,
            @PathVariable String pageKey, @RequestBody Map<String, Object> config) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (userId != null) {
            tablePrefService.save(userId, pageKey, config);
        }
        return Map.of("ok", Boolean.TRUE);
    }
}
