package com.company.inventory.controller;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.RequireRole;
import com.company.inventory.model.dto.user.UserCreateDTO;
import com.company.inventory.model.dto.user.UserUpdateDTO;
import com.company.inventory.model.query.UserQuery;
import com.company.inventory.service.UserService;
import com.company.inventory.model.vo.user.UserVO;













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
 * 用户管理接口(仅 admin)。
 *
 * @author inventory
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequireRole("admin")
public class UserController {

    /** 用户服务。 */
    private final UserService userService;

    /**
     * 构造控制器。
     *
     * @param userService 用户服务
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户列表(分页)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Operation(summary = "用户列表")
    @GetMapping
    public PageResult<UserVO> list(@Valid UserQuery query) {
        return userService.list(query);
    }

    /**
     * 新建用户。
     *
     * @param dto 入参
     * @return 新用户
     */
    @Operation(summary = "新建用户")
    @PostMapping
    public UserVO create(@Valid @RequestBody UserCreateDTO dto) {
        return userService.create(dto);
    }

    /**
     * 更新用户(部分字段)。
     *
     * @param id  用户 ID
     * @param dto 入参
     * @return 更新后用户
     */
    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public UserVO update(@PathVariable long id, @Valid @RequestBody UserUpdateDTO dto) {
        return userService.update(id, dto);
    }
}
