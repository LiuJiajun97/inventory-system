-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V13: 期初库存模块
-- 1) 新表 opening_stock_doc / opening_stock_doc_item(期初单头/行,create 即过账,status 恒 finished)
--    过账走现有入库链路(inbound_doc.ref_type='opening',ref_doc_id=期初单 ID),库存/流水可追溯
--    每物品×仓库限一次期初(服务端校验 inbound_doc 已有 finished 期初入库)
-- 2) 菜单 seed(照 V11/V12 幂等模式):库存分组下 opening(期初库存 /opening)
--    按钮 opening:create(期初库存-新建)
--    绑定:admin 全部;operator 菜单 + opening:create;viewer 只菜单
-- 幂等:CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
--       菜单与绑定 ON CONFLICT DO NOTHING,父菜单不存在时 SELECT 无结果安全跳过,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V13__opening_stock.sql
--       (inventory_test 测试库同样执行一遍)

BEGIN;

-- ---------- 1) 期初单头 ----------
CREATE TABLE IF NOT EXISTS opening_stock_doc (
    id           BIGSERIAL PRIMARY KEY,
    doc_no       TEXT NOT NULL,
    doc_date     DATE NOT NULL,
    warehouse_id BIGINT NOT NULL,
    total_qty    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    remark       TEXT,
    status       VARCHAR(20) NOT NULL,
    creator      TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      TEXT,
    updated_at   TIMESTAMP,
    CONSTRAINT uk_opening_stock_doc_doc_no UNIQUE (doc_no)
    -- 注:warehouse_id 按任务书为 BIGINT,warehouse.id 为 SERIAL(INTEGER),
    -- PG 不允许 BIGINT 外键引用 INTEGER 主键,故不加 FK,由服务端校验仓库存在性
);
CREATE INDEX IF NOT EXISTS idx_opening_stock_doc_warehouse ON opening_stock_doc (warehouse_id);

-- ---------- 2) 期初单行 ----------
CREATE TABLE IF NOT EXISTS opening_stock_doc_item (
    id               BIGSERIAL PRIMARY KEY,
    doc_id           BIGINT NOT NULL,
    line_no          INTEGER NOT NULL,
    item_id          BIGINT NOT NULL,
    spec_snapshot    VARCHAR(200),
    unit             VARCHAR(50),
    quantity         NUMERIC(18, 4) NOT NULL,
    unit_price       NUMERIC(14, 4),
    batch_no         VARCHAR(50),
    production_date  DATE,
    expiry_date      DATE,
    location_id      BIGINT,
    creator          TEXT,
    CONSTRAINT fk_opening_stock_doc_item_doc FOREIGN KEY (doc_id) REFERENCES opening_stock_doc (id)
    -- 注:item_id/location_id 为 BIGINT(任务书),item/location.id 为 INTEGER,同上不加 FK
);
CREATE INDEX IF NOT EXISTS idx_opening_stock_doc_item_doc ON opening_stock_doc_item (doc_id);

-- ---------- 3) 菜单 seed ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'opening', '期初库存', 'menu', '/opening', 8
FROM sys_menu p WHERE p.menu_code = 'stock-dir'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'opening:create', '期初库存-新建', 'button', NULL, 1
FROM sys_menu p WHERE p.menu_code = 'opening'
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), GREATEST(1, (SELECT MAX(id) FROM sys_menu)));

-- ---------- 4) 角色绑定 ----------
-- admin:菜单 + 新建按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('opening', 'opening:create')
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- operator:菜单 + 新建按钮(与出入库 create 权限一致)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('opening', 'opening:create')
WHERE r.role_code = 'operator'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- viewer:只菜单(列表可见,无写按钮)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('opening')
WHERE r.role_code = 'viewer'
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
