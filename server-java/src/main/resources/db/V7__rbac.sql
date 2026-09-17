-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V7: RBAC 权限架构(5 表 + seed)
-- 1) sys_role / sys_menu / sys_role_menu / sys_user_role / sys_user_warehouse
-- 2) seed:3 内置角色 + 全量菜单树(6 目录 + 20 菜单 + 59 按钮权限码)+ 角色-菜单绑定
-- 3) 存量用户迁移:sys_user.role → sys_user_role
-- 幂等:全部 IF NOT EXISTS / ON CONFLICT DO NOTHING,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V7__rbac.sql
--       (inventory_test 测试库同样执行一遍)

BEGIN;

-- ---------- sys_role:角色表 ----------
CREATE TABLE IF NOT EXISTS sys_role (
    id          SERIAL PRIMARY KEY,
    role_code   TEXT NOT NULL,
    role_name   TEXT NOT NULL,
    remark      TEXT,
    is_builtin  BOOLEAN NOT NULL DEFAULT FALSE,
    status      INTEGER NOT NULL DEFAULT 1,
    creator     VARCHAR(64) NULL,
    created_at  TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(64) NULL,
    updated_at  TIMESTAMP NULL,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

-- ---------- sys_menu:菜单表(目录/菜单/按钮三型合一) ----------
CREATE TABLE IF NOT EXISTS sys_menu (
    id          SERIAL PRIMARY KEY,
    parent_id   INTEGER NOT NULL DEFAULT 0,
    menu_code   TEXT NOT NULL,
    menu_name   TEXT NOT NULL,
    type        TEXT NOT NULL,
    path        TEXT,
    sort        INTEGER NOT NULL DEFAULT 0,
    status      INTEGER NOT NULL DEFAULT 1,
    creator     VARCHAR(64) NULL,
    created_at  TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(64) NULL,
    updated_at  TIMESTAMP NULL,
    CONSTRAINT uk_sys_menu_code UNIQUE (menu_code),
    CONSTRAINT ck_sys_menu_type CHECK (type IN ('directory', 'menu', 'button'))
);

CREATE INDEX IF NOT EXISTS idx_sys_menu_parent ON sys_menu (parent_id);

-- ---------- sys_role_menu:角色-菜单绑定 ----------
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id     INTEGER NOT NULL,
    menu_id     INTEGER NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

-- ---------- sys_user_role:用户-角色绑定(多角色,权限取并集) ----------
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id     INTEGER NOT NULL,
    role_id     INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- ---------- sys_user_warehouse:用户-仓库授权(数据权限,admin 豁免) ----------
CREATE TABLE IF NOT EXISTS sys_user_warehouse (
    user_id         INTEGER NOT NULL,
    warehouse_id    INTEGER NOT NULL,
    PRIMARY KEY (user_id, warehouse_id)
);

-- ---------- seed:3 内置角色 ----------
INSERT INTO sys_role (role_code, role_name, remark, is_builtin, status)
VALUES
    ('admin',    '系统管理员', '内置角色', TRUE, 1),
    ('operator', '库员',       '内置角色', TRUE, 1),
    ('viewer',   '查看员',     '内置角色', TRUE, 1)
ON CONFLICT (role_code) DO NOTHING;
SELECT setval(pg_get_serial_sequence('sys_role', 'id'), (SELECT MAX(id) FROM sys_role));

-- ---------- seed:菜单树(1)目录 ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES
    (0, 'dashboard-dir', '总览',     'directory', NULL, 1),
    (0, 'purchase-dir',  '采购',     'directory', NULL, 2),
    (0, 'sales-dir',     '销售',     'directory', NULL, 3),
    (0, 'stock-dir',     '库存',     'directory', NULL, 4),
    (0, 'base-dir',      '基础数据', 'directory', NULL, 5),
    (0, 'system-dir',    '系统',     'directory', NULL, 6)
ON CONFLICT (menu_code) DO NOTHING;

-- ---------- seed:菜单树(2)菜单 ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES
    ((SELECT id FROM sys_menu WHERE menu_code = 'dashboard-dir'), 'dashboard',        '仪表盘',     'menu', '/dashboard',       1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'dashboard-dir'), 'alerts',           '预警中心',   'menu', '/alerts',          2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'purchase-dir'),  'purchase-orders',  '采购订单',   'menu', '/purchase-orders', 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'sales-dir'),     'sales-orders',     '销售订单',   'menu', '/sales-orders',    1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-dir'),     'inbound',          '入库单',     'menu', '/inbound',         1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-dir'),     'outbound',         '出库单',     'menu', '/outbound',        2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-dir'),     'transfers',        '调拨单',     'menu', '/transfers',       3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-dir'),     'stocktakes',       '盘点单',     'menu', '/stocktakes',      4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-dir'),     'stock-adjusts',    '库存调整',   'menu', '/stock-adjusts',   5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-dir'),     'stock',            '库存查询',   'menu', '/stock',           6),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-dir'),     'transactions',     '流水查询',   'menu', '/transactions',    7),
    ((SELECT id FROM sys_menu WHERE menu_code = 'base-dir'),      'items',            '物品',       'menu', '/items',           1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'base-dir'),      'warehouses',       '仓库',       'menu', '/warehouses',      2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'base-dir'),      'locations',        '库位',       'menu', '/locations',       3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'base-dir'),      'suppliers',        '供应商',     'menu', '/suppliers',       4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'base-dir'),      'customers',        '客户',       'menu', '/customers',       5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'system-dir'),    'users',            '用户管理',   'menu', '/users',           1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'system-dir'),    'roles',            '角色权限',   'menu', '/roles',           2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'system-dir'),    'dicts',            '字典管理',   'menu', '/dicts',           3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'system-dir'),    'monitor',          '系统监控',   'menu', '/monitor',         4)
ON CONFLICT (menu_code) DO NOTHING;

-- ---------- seed:菜单树(3)按钮权限码 ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES
    -- 采购订单
    ((SELECT id FROM sys_menu WHERE menu_code = 'purchase-orders'), 'purchase-order:edit',    '采购订单-编辑', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'purchase-orders'), 'purchase-order:submit',  '采购订单-提交', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'purchase-orders'), 'purchase-order:approve', '采购订单-审批通过', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'purchase-orders'), 'purchase-order:reject',  '采购订单-驳回', 'button', NULL, 4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'purchase-orders'), 'purchase-order:close',   '采购订单-关闭', 'button', NULL, 5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'purchase-orders'), 'purchase-order:void',    '采购订单-作废', 'button', NULL, 6),
    -- 销售订单
    ((SELECT id FROM sys_menu WHERE menu_code = 'sales-orders'), 'sales-order:edit',    '销售订单-编辑', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'sales-orders'), 'sales-order:submit',  '销售订单-提交', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'sales-orders'), 'sales-order:approve', '销售订单-审批通过', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'sales-orders'), 'sales-order:reject',  '销售订单-驳回', 'button', NULL, 4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'sales-orders'), 'sales-order:close',   '销售订单-关闭', 'button', NULL, 5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'sales-orders'), 'sales-order:void',    '销售订单-作废', 'button', NULL, 6),
    -- 入库单
    ((SELECT id FROM sys_menu WHERE menu_code = 'inbound'), 'inbound:create', '入库单-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'inbound'), 'inbound:edit',   '入库单-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'inbound'), 'inbound:submit', '入库单-提交', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'inbound'), 'inbound:approve', '入库单-审批通过', 'button', NULL, 4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'inbound'), 'inbound:reject',  '入库单-驳回', 'button', NULL, 5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'inbound'), 'inbound:close',   '入库单-关闭', 'button', NULL, 6),
    ((SELECT id FROM sys_menu WHERE menu_code = 'inbound'), 'inbound:void',    '入库单-作废', 'button', NULL, 7),
    -- 出库单
    ((SELECT id FROM sys_menu WHERE menu_code = 'outbound'), 'outbound:create', '出库单-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'outbound'), 'outbound:edit',   '出库单-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'outbound'), 'outbound:submit', '出库单-提交', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'outbound'), 'outbound:approve', '出库单-审批通过', 'button', NULL, 4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'outbound'), 'outbound:reject',  '出库单-驳回', 'button', NULL, 5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'outbound'), 'outbound:close',   '出库单-关闭', 'button', NULL, 6),
    ((SELECT id FROM sys_menu WHERE menu_code = 'outbound'), 'outbound:void',    '出库单-作废', 'button', NULL, 7),
    -- 调拨单
    ((SELECT id FROM sys_menu WHERE menu_code = 'transfers'), 'transfer:edit',    '调拨单-编辑', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'transfers'), 'transfer:submit',  '调拨单-提交', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'transfers'), 'transfer:approve', '调拨单-审批通过', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'transfers'), 'transfer:reject',  '调拨单-驳回', 'button', NULL, 4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'transfers'), 'transfer:close',   '调拨单-关闭', 'button', NULL, 5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'transfers'), 'transfer:void',    '调拨单-作废', 'button', NULL, 6),
    -- 盘点单
    ((SELECT id FROM sys_menu WHERE menu_code = 'stocktakes'), 'stocktake:edit',    '盘点单-编辑', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stocktakes'), 'stocktake:submit',  '盘点单-提交', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stocktakes'), 'stocktake:approve', '盘点单-审批通过', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stocktakes'), 'stocktake:reject',  '盘点单-驳回', 'button', NULL, 4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stocktakes'), 'stocktake:close',   '盘点单-关闭', 'button', NULL, 5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stocktakes'), 'stocktake:void',    '盘点单-作废', 'button', NULL, 6),
    -- 库存调整
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-adjusts'), 'stock-adjust:edit',    '库存调整-编辑', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-adjusts'), 'stock-adjust:submit',  '库存调整-提交', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-adjusts'), 'stock-adjust:approve', '库存调整-审批通过', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-adjusts'), 'stock-adjust:reject',  '库存调整-驳回', 'button', NULL, 4),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-adjusts'), 'stock-adjust:close',   '库存调整-关闭', 'button', NULL, 5),
    ((SELECT id FROM sys_menu WHERE menu_code = 'stock-adjusts'), 'stock-adjust:void',    '库存调整-作废', 'button', NULL, 6),
    -- 基础数据(各 3 个写操作)
    ((SELECT id FROM sys_menu WHERE menu_code = 'items'),      'item:create',      '物品-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'items'),      'item:edit',        '物品-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'items'),      'item:delete',      '物品-删除', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'warehouses'), 'warehouse:create', '仓库-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'warehouses'), 'warehouse:edit',   '仓库-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'warehouses'), 'warehouse:delete', '仓库-删除', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'locations'),  'location:create',  '库位-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'locations'),  'location:edit',    '库位-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'locations'),  'location:delete',  '库位-删除', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'suppliers'),  'supplier:create',  '供应商-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'suppliers'),  'supplier:edit',    '供应商-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'suppliers'),  'supplier:delete',  '供应商-删除', 'button', NULL, 3),
    ((SELECT id FROM sys_menu WHERE menu_code = 'customers'),  'customer:create',  '客户-新建', 'button', NULL, 1),
    ((SELECT id FROM sys_menu WHERE menu_code = 'customers'),  'customer:edit',    '客户-编辑', 'button', NULL, 2),
    ((SELECT id FROM sys_menu WHERE menu_code = 'customers'),  'customer:delete',  '客户-删除', 'button', NULL, 3)
ON CONFLICT (menu_code) DO NOTHING;
SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), (SELECT MAX(id) FROM sys_menu));

-- ---------- seed:角色-菜单绑定 ----------
-- admin:全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- operator:除"系统"整个子树(任意深度,含 V8 追加的系统组按钮)外全部;基础数据组
-- (物品/仓库/库位/供应商/客户)按钮码不绑——后端写端点一期起即 @RequireRole("admin"),
-- operator 只读(前端按钮随之隐藏)。用递归子树排除,保证在含 V8 菜单的库上重跑 V7 也不会把系统组按钮污染给 operator
INSERT INTO sys_role_menu (role_id, menu_id)
WITH RECURSIVE sys_tree AS (
    SELECT id FROM sys_menu WHERE menu_code = 'system-dir'
    UNION
    SELECT m2.id FROM sys_menu m2 JOIN sys_tree st ON m2.parent_id = st.id
)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_code = 'operator'
  AND m.id NOT IN (SELECT id FROM sys_tree)
  AND m.menu_code NOT LIKE 'item:%'
  AND m.menu_code NOT LIKE 'warehouse:%'
  AND m.menu_code NOT LIKE 'location:%'
  AND m.menu_code NOT LIKE 'supplier:%'
  AND m.menu_code NOT LIKE 'customer:%'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- viewer:全部目录+菜单,不含任何按钮,不含"系统"整个子树(任意深度,递归排除,与 operator 段一致)
INSERT INTO sys_role_menu (role_id, menu_id)
WITH RECURSIVE sys_tree AS (
    SELECT id FROM sys_menu WHERE menu_code = 'system-dir'
    UNION
    SELECT m2.id FROM sys_menu m2 JOIN sys_tree st ON m2.parent_id = st.id
)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_code = 'viewer'
  AND m.type <> 'button'
  AND m.id NOT IN (SELECT id FROM sys_tree)
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- ---------- 外键引用(幂等:不存在才创建;测试 TRUNCATE ... CASCADE 可级联清绑定行) ----------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_role_menu_role') THEN
        ALTER TABLE sys_role_menu
            ADD CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_role_menu_menu') THEN
        ALTER TABLE sys_role_menu
            ADD CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_user_role_user') THEN
        ALTER TABLE sys_user_role
            ADD CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_user_role_role') THEN
        ALTER TABLE sys_user_role
            ADD CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_user_warehouse_user') THEN
        ALTER TABLE sys_user_warehouse
            ADD CONSTRAINT fk_sys_user_warehouse_user FOREIGN KEY (user_id) REFERENCES sys_user (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_user_warehouse_warehouse') THEN
        ALTER TABLE sys_user_warehouse
            ADD CONSTRAINT fk_sys_user_warehouse_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id);
    END IF;
END
$$;

-- ---------- 存量用户迁移:sys_user.role → sys_user_role ----------
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = u.role
ON CONFLICT (user_id, role_id) DO NOTHING;

COMMIT;
