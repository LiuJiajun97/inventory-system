package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.user.UserCreateDTO;
import com.company.inventory.dto.user.UserUpdateDTO;
import com.company.inventory.query.UserQuery;
import com.company.inventory.vo.user.UserVO;

















/**
 * 用户服务接口(仅 admin 可管理)。
 *
 * @author inventory
 */
public interface UserService {

    /**
     * 用户分页列表(按 id 升序,6 字段,不含密码)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    PageResult<UserVO> list(UserQuery query);

    /**
     * 新建用户:角色非法抛 400,用户名重复抛 400。
     *
     * @param dto 入参
     * @return 新用户(6 字段)
     */
    UserVO create(UserCreateDTO dto);

    /**
     * 更新用户(部分字段):用户不存在抛 404,角色非法抛 400。
     *
     * @param id  用户 ID
     * @param dto 入参(全字段可选)
     * @return 更新后用户(6 字段)
     */
    UserVO update(long id, UserUpdateDTO dto);
}
