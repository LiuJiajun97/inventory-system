package com.company.inventory.common.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.entity.user.UserDO;
import com.company.inventory.mapper.UserMapper;
import org.springframework.stereotype.Component;

/**
 * 审批资格校验组件(一期收尾口径调整):校验当前用户能否审批/驳回指定单据。
 *
 * <p>规则:管理员(admin)可审批自己制的单据;库员(operator)审批人不能是制单人;
 * 其他角色由 Controller 层 {@code @RequireRole} 拦截,不应到达此处。</p>
 *
 * @author inventory
 */
@Component
public class ApprovalGuard {

    /** 管理员角色值。 */
    private static final String ROLE_ADMIN = "admin";

    /** 用户 Mapper。 */
    private final UserMapper userMapper;

    /**
     * 构造组件。
     *
     * @param userMapper 用户 Mapper
     */
    public ApprovalGuard(UserMapper userMapper) {
        this.userMapper = userMapper;
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
        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, username));
        boolean isAdmin = user != null && ROLE_ADMIN.equals(user.getRole());
        if (username.equals(creator) && !isAdmin) {
            throw new BizException("审批人不能是制单人");
        }
    }
}
