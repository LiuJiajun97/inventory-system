-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V15: 报表中心菜单 seed
-- 1) 新增顶级目录 reports-dir(报表中心)+ 菜单 reports(报表中心 /reports,单页 4 Tab)
-- 2) 绑定:admin/operator/viewer 均为 目录+菜单(与仪表盘口径一致:菜单控制页面可见性,
--    报表全部只读,不新增导出权限码)
-- 幂等:菜单与绑定 ON CONFLICT DO NOTHING,父节点不存在时 SELECT 无结果安全跳过,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V15__report_menu.sql
--       (inventory_test 测试库同样执行一遍)

BEGIN;

-- ---------- 1) 目录:报表中心(顶级,sort 7 排在系统之后) ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES (0, 'reports-dir', '报表中心', 'directory', NULL, 7)
ON CONFLICT (menu_code) DO NOTHING;

-- ---------- 2) 菜单:报表中心 ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES ((SELECT id FROM sys_menu WHERE menu_code = 'reports-dir'), 'reports', '报表中心', 'menu', '/reports', 1)
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), GREATEST(1, (SELECT MAX(id) FROM sys_menu)));

-- ---------- 3) 角色绑定:三个角色均为 目录+菜单(仪表盘口径) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('reports-dir', 'reports')
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('reports-dir', 'reports')
WHERE r.role_code = 'operator'
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('reports-dir', 'reports')
WHERE r.role_code = 'viewer'
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
