package com.company.inventory.service.rbac.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.constant.MenuType;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.mapper.rbac.MenuMapper;
import com.company.inventory.mapper.rbac.RoleMenuMapper;
import com.company.inventory.model.dto.rbac.MenuCreateDTO;
import com.company.inventory.model.dto.rbac.MenuUpdateDTO;
import com.company.inventory.model.entity.rbac.MenuDO;
import com.company.inventory.model.entity.rbac.RoleMenuDO;
import com.company.inventory.model.vo.rbac.MenuNodeVO;
import com.company.inventory.model.vo.rbac.UserMenuTreeVO;
import com.company.inventory.service.rbac.MenuService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单管理服务实现。
 *
 * @author inventory
 */
@Service
public class MenuServiceImpl implements MenuService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(MenuServiceImpl.class);

    /** 菜单启用状态值。 */
    private static final int STATUS_ENABLED = 1;

    /** 顶级节点 parent_id 约定值。 */
    private static final long ROOT_PARENT_ID = 0L;

    /** 菜单 Mapper。 */
    private final MenuMapper menuMapper;

    /** 角色-菜单绑定 Mapper(删除菜单连带清理用)。 */
    private final RoleMenuMapper roleMenuMapper;

    /**
     * 构造服务。
     *
     * @param menuMapper     菜单 Mapper
     * @param roleMenuMapper 角色-菜单绑定 Mapper
     */
    public MenuServiceImpl(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    /**
     * 全量菜单树(含 button 子节点,admin 管理页用)。
     *
     * @return 顶级节点列表(按 sort 升序)
     */
    @Override
    public List<MenuNodeVO> tree() {
        List<MenuDO> rows = menuMapper.selectList(
                new LambdaQueryWrapper<MenuDO>().orderByAsc(MenuDO::getSort)
                        .orderByAsc(MenuDO::getId));
        return buildFullTree(rows);
    }

    /**
     * 当前用户可见菜单树(仅目录+菜单,按钮折叠为权限码列表,前端导航用)。
     *
     * @param userId 用户 ID
     * @return 顶级节点列表(多角色取并集)
     */
    @Override
    public List<UserMenuTreeVO> userMenuTree(long userId) {
        List<MenuDO> rows = menuMapper.selectMenusByUserId(userId);
        // parentId -> 按钮权限码列表(button 挂在叶子菜单下)
        Map<Long, List<String>> permMap = new LinkedHashMap<>();
        for (MenuDO row : rows) {
            if (MenuType.BUTTON.getValue().equals(row.getType())) {
                permMap.computeIfAbsent(row.getParentId(), k -> new ArrayList<>())
                        .add(row.getMenuCode());
            }
        }
        Map<Long, UserMenuTreeVO> nodeMap = new LinkedHashMap<>();
        for (MenuDO row : rows) {
            if (MenuType.BUTTON.getValue().equals(row.getType())) {
                continue;
            }
            nodeMap.put(row.getId(), toUserNode(row, permMap.get(row.getId())));
        }
        List<UserMenuTreeVO> roots = new ArrayList<>();
        for (MenuDO row : rows) {
            if (MenuType.BUTTON.getValue().equals(row.getType())) {
                continue;
            }
            UserMenuTreeVO node = nodeMap.get(row.getId());
            // 父节点未授权(或未设置)时按顶级处理,保证菜单不丢
            if (ROOT_PARENT_ID == row.getParentId() || !nodeMap.containsKey(row.getParentId())) {
                roots.add(node);
                continue;
            }
            nodeMap.get(row.getParentId()).children().add(node);
        }
        return roots;
    }

    /**
     * 新建菜单(menu_code 唯一校验;父节点必须存在且为目录/菜单)。
     *
     * @param dto 入参
     * @return 新菜单节点(含空 children)
     */
    @Override
    public MenuNodeVO create(MenuCreateDTO dto) {
        MenuType type = MenuType.fromValue(dto.type());
        if (type == null) {
            throw new BizException("菜单类型非法(仅 directory/menu/button)", ErrorCode.BIZ_ERROR,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        long dup = menuMapper.selectCount(new LambdaQueryWrapper<MenuDO>()
                .eq(MenuDO::getMenuCode, dto.menuCode().trim()));
        if (dup > 0) {
            throw new BizException("菜单编码已存在", ErrorCode.MENU_CODE_DUP,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        long parentId = dto.parentId() == null ? ROOT_PARENT_ID : dto.parentId();
        checkParent(parentId);
        MenuDO menu = new MenuDO();
        menu.setParentId(parentId);
        menu.setMenuCode(dto.menuCode().trim());
        menu.setMenuName(dto.menuName().trim());
        menu.setType(type.getValue());
        menu.setPath(type == MenuType.BUTTON ? null : dto.path());
        menu.setSort(dto.sort() == null ? 0 : dto.sort());
        menu.setStatus(STATUS_ENABLED);
        menuMapper.insert(menu);
        LOGGER.info("新建菜单: {}", menu.getMenuCode());
        return toFullNode(menu);
    }

    /**
     * 更新菜单(menu_code 不可改;父节点变更校验同新建)。
     *
     * @param id  菜单 ID
     * @param dto 入参(可选字段,非空才更新)
     * @return 更新后节点
     */
    @Override
    public MenuNodeVO update(long id, MenuUpdateDTO dto) {
        MenuDO menu = requireMenu(id);
        if (dto.parentId() != null) {
            if (!dto.parentId().equals(menu.getParentId())) {
                if (dto.parentId() == id) {
                    throw new BizException("父节点不能是自身", ErrorCode.BIZ_ERROR,
                            ErrorCode.HTTP_BAD_REQUEST);
                }
                checkParent(dto.parentId());
                menu.setParentId(dto.parentId());
            }
        }
        if (StringUtils.hasText(dto.menuName())) {
            menu.setMenuName(dto.menuName().trim());
        }
        if (dto.path() != null) {
            menu.setPath(dto.path());
        }
        if (dto.sort() != null) {
            menu.setSort(dto.sort());
        }
        if (dto.status() != null) {
            menu.setStatus(dto.status());
        }
        menuMapper.updateById(menu);
        LOGGER.info("更新菜单: id={}", id);
        return toFullNode(menuMapper.selectById(id));
    }

    /**
     * 删除菜单(有子节点禁删;连带清 role_menu 绑定)。
     *
     * @param id 菜单 ID
     */
    @Override
    @Transactional
    public void delete(long id) {
        requireMenu(id);
        long children = menuMapper.selectCount(new LambdaQueryWrapper<MenuDO>()
                .eq(MenuDO::getParentId, id));
        if (children > 0) {
            throw new BizException("菜单存在子节点,不可删除", ErrorCode.MENU_HAS_CHILDREN,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuDO>()
                .eq(RoleMenuDO::getMenuId, id));
        menuMapper.deleteById(id);
        LOGGER.info("删除菜单: id={}", id);
    }

    /**
     * 校验父节点:0 表示顶级放行;否则必须存在且类型为目录/菜单。
     *
     * @param parentId 父节点 ID
     */
    private void checkParent(long parentId) {
        if (ROOT_PARENT_ID == parentId) {
            return;
        }
        MenuDO parent = menuMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException("父节点不存在", ErrorCode.BIZ_ERROR, ErrorCode.HTTP_BAD_REQUEST);
        }
        if (MenuType.BUTTON.getValue().equals(parent.getType())) {
            throw new BizException("父节点必须是目录或菜单", ErrorCode.BIZ_ERROR,
                    ErrorCode.HTTP_BAD_REQUEST);
        }
    }

    /**
     * 取菜单,不存在抛 404。
     *
     * @param id 菜单 ID
     * @return 菜单实体
     */
    private MenuDO requireMenu(long id) {
        MenuDO menu = menuMapper.selectById(id);
        if (menu == null) {
            throw BizException.notFound("菜单不存在");
        }
        return menu;
    }

    /**
     * 平铺列表 → 全量树(含 button),按 sort/id 升序。
     *
     * @param rows 平铺菜单列表(已按 sort/id 升序)
     * @return 顶级节点列表
     */
    private List<MenuNodeVO> buildFullTree(List<MenuDO> rows) {
        Map<Long, MenuNodeVO> nodeMap = new LinkedHashMap<>();
        for (MenuDO row : rows) {
            nodeMap.put(row.getId(), toFullNode(row));
        }
        List<MenuNodeVO> roots = new ArrayList<>();
        for (MenuDO row : rows) {
            MenuNodeVO node = nodeMap.get(row.getId());
            if (ROOT_PARENT_ID == row.getParentId()
                    || nodeMap.get(row.getParentId()) == null) {
                roots.add(node);
                continue;
            }
            nodeMap.get(row.getParentId()).children().add(node);
        }
        return roots;
    }

    /**
     * 实体转全量节点(带可变空 children,便于挂子节点)。
     *
     * @param menu 菜单实体
     * @return 节点
     */
    private MenuNodeVO toFullNode(MenuDO menu) {
        return new MenuNodeVO(menu.getId(), menu.getParentId(), menu.getMenuCode(),
                menu.getMenuName(), menu.getType(), menu.getPath(), menu.getSort(),
                menu.getStatus(), new ArrayList<>());
    }

    /**
     * 实体转用户菜单树节点(button 权限码按 sort 顺序收集)。
     *
     * @param menu      菜单实体
     * @param perms     该节点下按钮权限码(可为 null)
     * @return 节点
     */
    private UserMenuTreeVO toUserNode(MenuDO menu, List<String> perms) {
        List<String> sortedPerms = perms == null ? new ArrayList<>()
                : perms.stream().sorted(Comparator.naturalOrder()).toList();
        return new UserMenuTreeVO(menu.getMenuCode(), menu.getMenuName(), menu.getPath(),
                menu.getSort(), new ArrayList<>(sortedPerms), new ArrayList<>());
    }
}
