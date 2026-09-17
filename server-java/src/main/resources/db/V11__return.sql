-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V11: 退货模块(采购退货 + 销售退货)
-- 1) purchase_order_item / sales_order_item 加 returned_qty(已退累计,NOT NULL DEFAULT 0)
-- 2) 新表 purchase_return / purchase_return_item(采购退货单头/行)
--    新表 sales_return / sales_return_item(销售退货单头/行)
--    退货单 create 即过账(同事务生成出入库单走现有过账链路),status 恒 finished,无草稿/作废
-- 3) 菜单 seed(照 V8 幂等模式):
--    采购分组 purchase-return(采购退货单 /purchase-returns)+ 按钮 purchase-return:create
--    销售分组 sales-return(销售退货单 /sales-returns)  + 按钮 sales-return:create
--    绑定:admin 全部;operator 两个 create;viewer 只菜单不给按钮
-- 幂等:ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
--       菜单与绑定 ON CONFLICT DO NOTHING,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V11__return.sql
--       (inventory_test 测试库同样执行一遍)

BEGIN;

-- ---------- 1) 原单行加已退累计列 ----------
ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS returned_qty numeric(16, 4) NOT NULL DEFAULT 0;
COMMENT ON COLUMN purchase_order_item.returned_qty IS '累计退货数量(退货单过账回写)';

ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS returned_qty numeric(16, 4) NOT NULL DEFAULT 0;
COMMENT ON COLUMN sales_order_item.returned_qty IS '累计退货数量(退货单过账回写,净发货=shipped-returned)';

-- ---------- 2) 采购退货单 ----------
CREATE TABLE IF NOT EXISTS purchase_return (
    id               SERIAL PRIMARY KEY,
    doc_no           TEXT NOT NULL,
    doc_date         DATE NOT NULL,
    purchase_order_id INTEGER NOT NULL,
    warehouse_id     INTEGER NOT NULL,
    total_amount     NUMERIC(14, 2),
    remark           TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'finished',
    creator          TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          TEXT,
    updated_at       TIMESTAMP,
    CONSTRAINT uk_purchase_return_doc_no UNIQUE (doc_no),
    CONSTRAINT fk_purchase_return_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_order (id),
    CONSTRAINT fk_purchase_return_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);
CREATE INDEX IF NOT EXISTS idx_purchase_return_order ON purchase_return (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_warehouse ON purchase_return (warehouse_id);

CREATE TABLE IF NOT EXISTS purchase_return_item (
    id                     SERIAL PRIMARY KEY,
    doc_id                 INTEGER NOT NULL,
    line_no                INTEGER,
    purchase_order_item_id INTEGER NOT NULL,
    item_id                INTEGER NOT NULL,
    spec_snapshot          TEXT,
    unit                   TEXT,
    quantity               NUMERIC(16, 4) NOT NULL,
    unit_price             NUMERIC(14, 4) NOT NULL,
    tax_rate               NUMERIC(6, 4),
    amount                 NUMERIC(14, 2),
    tax_amount             NUMERIC(14, 2),
    tax_inclusive_total    NUMERIC(14, 2),
    creator                TEXT,
    CONSTRAINT fk_purchase_return_item_doc FOREIGN KEY (doc_id) REFERENCES purchase_return (id)
);
CREATE INDEX IF NOT EXISTS idx_purchase_return_item_po_item ON purchase_return_item (purchase_order_item_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_item_doc ON purchase_return_item (doc_id);

-- ---------- 3) 销售退货单 ----------
CREATE TABLE IF NOT EXISTS sales_return (
    id               SERIAL PRIMARY KEY,
    doc_no           TEXT NOT NULL,
    doc_date         DATE NOT NULL,
    sales_order_id   INTEGER NOT NULL,
    warehouse_id     INTEGER NOT NULL,
    total_amount     NUMERIC(14, 2),
    remark           TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'finished',
    creator          TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          TEXT,
    updated_at       TIMESTAMP,
    CONSTRAINT uk_sales_return_doc_no UNIQUE (doc_no),
    CONSTRAINT fk_sales_return_order FOREIGN KEY (sales_order_id) REFERENCES sales_order (id),
    CONSTRAINT fk_sales_return_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);
CREATE INDEX IF NOT EXISTS idx_sales_return_order ON sales_return (sales_order_id);
CREATE INDEX IF NOT EXISTS idx_sales_return_warehouse ON sales_return (warehouse_id);

CREATE TABLE IF NOT EXISTS sales_return_item (
    id                 SERIAL PRIMARY KEY,
    doc_id             INTEGER NOT NULL,
    line_no            INTEGER,
    sales_order_item_id INTEGER NOT NULL,
    item_id            INTEGER NOT NULL,
    spec_snapshot      TEXT,
    unit               TEXT,
    quantity           NUMERIC(16, 4) NOT NULL,
    unit_price         NUMERIC(14, 4) NOT NULL,
    tax_rate           NUMERIC(6, 4),
    amount             NUMERIC(14, 2),
    tax_amount         NUMERIC(14, 2),
    tax_inclusive_total NUMERIC(14, 2),
    creator            TEXT,
    CONSTRAINT fk_sales_return_item_doc FOREIGN KEY (doc_id) REFERENCES sales_return (id)
);
CREATE INDEX IF NOT EXISTS idx_sales_return_item_so_item ON sales_return_item (sales_order_item_id);
CREATE INDEX IF NOT EXISTS idx_sales_return_item_doc ON sales_return_item (doc_id);

-- ---------- 4) 菜单 seed(照 V8 幂等模式;父节点不存在时安全跳过,兼容菜单自管的测试库) ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'purchase-return', '采购退货单', 'menu', '/purchase-returns', 2
FROM sys_menu p WHERE p.menu_code = 'purchase-dir'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'sales-return', '销售退货单', 'menu', '/sales-returns', 2
FROM sys_menu p WHERE p.menu_code = 'sales-dir'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'purchase-return:create', '采购退货-新建', 'button', NULL, 1
FROM sys_menu p WHERE p.menu_code = 'purchase-return'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'sales-return:create', '销售退货-新建', 'button', NULL, 1
FROM sys_menu p WHERE p.menu_code = 'sales-return'
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), GREATEST(1, (SELECT MAX(id) FROM sys_menu)));

-- ---------- 5) 角色绑定(V7 全量绑定发生在 V7 执行时,新节点需显式补绑) ----------
-- admin:菜单 + 按钮全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN (
    'purchase-return', 'purchase-return:create',
    'sales-return', 'sales-return:create'
)
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- operator:两个新建按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN (
    'purchase-return:create',
    'sales-return:create'
)
WHERE r.role_code = 'operator'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- viewer:只菜单不给按钮
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN (
    'purchase-return',
    'sales-return'
)
WHERE r.role_code = 'viewer'
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
