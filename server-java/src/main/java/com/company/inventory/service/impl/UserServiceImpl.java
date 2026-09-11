package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.constant.RoleEnum;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.user.UserCreateDTO;
import com.company.inventory.model.dto.user.UserUpdateDTO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.model.query.UserQuery;
import com.company.inventory.service.UserService;
import com.company.inventory.model.vo.user.UserVO;



















import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;









import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户服务实现。
 *
 * @author inventory
 */
@Service
public class UserServiceImpl implements UserService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 密码编码器(BCrypt,强度 10,与 Fastify 版一致)。 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /** 用户 Mapper。 */
    private final UserMapper userMapper;

    /**
     * 构造服务。
     *
     * @param userMapper 用户 Mapper
     */
    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 用户分页列表(按 id 升序,6 字段,不含密码)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<UserVO> list(UserQuery query) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String like = query.getKeyword().trim();
            wrapper.and(w -> w.like(UserDO::getUsername, like)
                    .or().like(UserDO::getName, like));
        }
        wrapper.orderByAsc(UserDO::getId);
        Page<UserDO> page = userMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<UserVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 新建用户。
     *
     * @param dto 入参
     * @return 新用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateDTO dto) {
        if (!RoleEnum.isValid(dto.role())) {
            throw new BizException("角色必须为 admin / operator / viewer",
                    ErrorCode.BAD_ROLE, ErrorCode.HTTP_BAD_REQUEST);
        }
        Long exist = userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, dto.username()));
        if (exist != null && exist > 0) {
            throw new BizException("用户名已存在", ErrorCode.USERNAME_DUP, ErrorCode.HTTP_BAD_REQUEST);
        }
        UserDO user = new UserDO();
        user.setUsername(dto.username());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setName(dto.name());
        user.setRole(dto.role());
        user.setStatus(dto.status() == null ? 1 : dto.status());
        userMapper.insert(user);
        LOGGER.info("新建用户: username={}, role={}", dto.username(), dto.role());
        return toVO(user);
    }

    /**
     * 更新用户(部分字段)。
     *
     * @param id  用户 ID
     * @param dto 入参
     * @return 更新后用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO update(long id, UserUpdateDTO dto) {
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (StringUtils.hasText(dto.role()) && !RoleEnum.isValid(dto.role())) {
            throw new BizException("角色必须为 admin / operator / viewer",
                    ErrorCode.BAD_ROLE, ErrorCode.HTTP_BAD_REQUEST);
        }
        if (StringUtils.hasText(dto.name())) {
            user.setName(dto.name());
        }
        if (StringUtils.hasText(dto.role())) {
            user.setRole(dto.role());
        }
        if (dto.status() != null) {
            user.setStatus(dto.status());
        }
        if (StringUtils.hasText(dto.password())) {
            user.setPasswordHash(passwordEncoder.encode(dto.password()));
        }
        userMapper.updateById(user);
        LOGGER.info("更新用户: userId={}, fields={name={}, role={}, status={}, password={}}",
                id, StringUtils.hasText(dto.name()), StringUtils.hasText(dto.role()),
                dto.status(), StringUtils.hasText(dto.password()));
        return toVO(user);
    }

    /**
     * 实体转 VO(6 字段,不含密码)。
     *
     * @param user 实体
     * @return VO
     */
    private UserVO toVO(UserDO user) {
        return new UserVO(user.getId(), user.getUsername(), user.getName(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
