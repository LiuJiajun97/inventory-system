package com.company.inventory.common.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.inventory.common.constant.RoleEnum;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.rbac.UserRoleMapper;
import com.company.inventory.model.entity.user.UserDO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审批资格校验组件(一期收尾口径调整):校验当前用户能否审批/驳回指定单据。
 *
 * <p>规则:管理员(admin)可审批自己制的单据;库员(operator)审批人不能是制单人;
 * 其他角色由 Controller 层 {@code @RequirePerm} 拦截,不应到达此处。</p>
 *
 * <p>管理员判定按 sys_user_role 多角色绑定取角色码集合(含 admin 即管理员),
 * 不再读已废弃的单角色列,避免多角色用户被误判"禁自批"。</p>
 *
 * @author inventory
 */
@Component
public class ApprovalGuard {

    /** 管理员角色值。 */
    private static final String ROLE_ADMIN = RoleEnum.ADMIN.getValue();

    /** 用户 Mapper。 */
    private final UserMapper userMapper;

    /** 用户-角色 Mapper(多角色绑定,sys_user_role)。 */
    private final UserRoleMapper userRoleMapper;

    /**
     * 构造组件。
     *
     * @param userMapper     用户 Mapper
     * @param userRoleMapper 用户-角色 Mapper
     */
    public ApprovalGuard(UserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /**
     * 校验审批资格:管理员可自批,库员禁自批。
     *
     * <p>查无用户时(仅单测以虚构用户名调用服务层)按非管理员处理,保持旧版
     * "禁自批"行为;生产请求用户名来自 JWT,必然存在。</p>
     *
     * @param username 当前操作人用户名
     * @param creator  单据制单人用户名
     * @throws BizException 非管理员自批
     */
    public void assertApprovable(String username, String creator) {
        if (!username.equals(creator)) {
            return;
        }
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username));
        boolean isAdmin = false;
        if (user != null) {
            List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(user.getId());
            isAdmin = roleCodes != null && roleCodes.contains(ROLE_ADMIN);
        }
        if (!isAdmin) {
            throw new BizException("审批人不能是制单人");
        }
    }
}
