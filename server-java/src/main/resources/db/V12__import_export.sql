-- V12: 导入导出模块按钮权限(RBAC)
-- 1) 11 个按钮菜单(照 V8/V11 幂等模式):
--    主数据:item:import / item:export / supplier:import / supplier:export / customer:import / customer:export
--    列表导出:stock:export / inbound:export / outbound:export / purchase-order:export / sales-order:export
-- 2) 角色绑定:
--    admin:全部 11 个
--    operator:仅 5 个列表导出(stock / inbound / outbound / purchase-order / sales-order)
--    viewer:不绑按钮(纯菜单,维持现状;主数据导出与列表读一致,viewer 可导,不靠按钮码拦截)
-- 幂等:菜单 ON CONFLICT (menu_code) DO NOTHING,绑定 ON CONFLICT (role_id, menu_id) DO NOTHING,
--       父菜单不存在时 SELECT 无结果安全跳过,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V12__import_export.sql
--       (inventory_test 测试库同样执行)

BEGIN;

-- ---------- 1) 主数据按钮(parent = 物品/供应商/客户菜单) ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'item:import', '物品-导入', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'items'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'item:export', '物品-导出', 'button', NULL, 11
FROM sys_menu p WHERE p.menu_code = 'items'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'supplier:import', '供应商-导入', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'suppliers'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'supplier:export', '供应商-导出', 'button', NULL, 11
FROM sys_menu p WHERE p.menu_code = 'suppliers'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'customer:import', '客户-导入', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'customers'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'customer:export', '客户-导出', 'button', NULL, 11
FROM sys_menu p WHERE p.menu_code = 'customers'
ON CONFLICT (menu_code) DO NOTHING;

-- ---------- 2) 列表导出按钮 ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'stock:export', '库存-导出', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'stock'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'inbound:export', '入库单-导出', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'inbound'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'outbound:export', '出库单-导出', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'outbound'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'purchase-order:export', '采购订单-导出', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'purchase-orders'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'sales-order:export', '销售订单-导出', 'button', NULL, 10
FROM sys_menu p WHERE p.menu_code = 'sales-orders'
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), GREATEST(1, (SELECT MAX(id) FROM sys_menu)));

-- ---------- 3) 角色绑定 ----------
-- admin:全部 11 个按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN (
    'item:import', 'item:export',
    'supplier:import', 'supplier:export',
    'customer:import', 'customer:export',
    'stock:export', 'inbound:export', 'outbound:export',
    'purchase-order:export', 'sales-order:export'
)
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- operator:仅 5 个列表导出按钮(主数据导入/导出无权限,与写权限矩阵一致)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN (
    'stock:export', 'inbound:export', 'outbound:export',
    'purchase-order:export', 'sales-order:export'
)
WHERE r.role_code = 'operator'
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
