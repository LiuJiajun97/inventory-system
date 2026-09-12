-- V8: RBAC 批 2(系统组按钮权限码补全 + 「菜单管理」菜单 + admin 绑定)
-- 1) V7 系统组(users/roles/dicts/monitor)无 button 节点,本迁移补:
--    users 下 user:create / user:edit / user:delete
--    roles 下 role:create / role:edit / role:delete / role:assign
--    menus 下 menu:create / menu:edit / menu:delete
--    dicts 下 dict:create / dict:edit / dict:delete
--    monitor 为只读页,不加按钮
-- 2) 新增「菜单管理」菜单(parent=system-dir,path=/menus,sort 放 roles 之后)
-- 3) 上述菜单 + 全部按钮绑定 admin 角色
-- 幂等:全部 ON CONFLICT DO NOTHING,可重复执行(依赖 V7 已建表与 seed)。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V8__rbac2.sql
--       (inventory_test 测试库同样执行一遍)

BEGIN;

-- ---------- 新增「菜单管理」菜单 ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES
    ((SELECT id FROM sys_menu WHERE menu_code = 'system-dir'), 'menus', '菜单管理', 'menu', '/menus', 5)
ON CONFLICT (menu_code) DO NOTHING;

-- ---------- 系统组按钮权限码(V7 全缺,一并补) ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES
    -- 用户管理
    ((SELECT id FROM sys_menu WHERE menu_code = 'users'),  'user:create',  '用户-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'users'),  'user:edit',    '用户-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'users'),  'user:delete',  '用户-删除', 'button', NULL, 3),
    -- 角色权限
    ((SELECT id FROM sys_menu WHERE menu_code = 'roles'),  'role:create',  '角色-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'roles'),  'role:edit',    '角色-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'roles'),  'role:delete',  '角色-删除', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'roles'),  'role:assign',  '角色-分配菜单', 'button', NULL, 4),
    -- 菜单管理
    ((SELECT id FROM sys_menu WHERE menu_code = 'menus'),  'menu:create',  '菜单-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'menus'),  'menu:edit',    '菜单-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'menus'),  'menu:delete',  '菜单-删除', 'button', NULL, 3),
    -- 字典管理
    ((SELECT id FROM sys_menu WHERE menu_code = 'dicts'),  'dict:create',  '字典-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'dicts'),  'dict:edit',    '字典-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'dicts'),  'dict:delete',  '字典-删除', 'button', NULL, 3)
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));

-- ---------- 绑定 admin(V7 的 admin 全量绑定发生在 V7 执行时,新节点需显式补绑) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN (
    'menus',
    'user:create', 'user:edit', 'user:delete',
    'role:create', 'role:edit', 'role:delete', 'role:assign',
    'menu:create', 'menu:edit', 'menu:delete',
    'dict:create', 'dict:edit', 'dict:delete'
)
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
