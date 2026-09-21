-- V26:库存冻结/解冻 + 价目表带价
-- 背景:
--   1) 库存冻结(批次级,质检/客诉/破损场景):stock 表加 frozen 列,冻结粒度=仓+批次
--      (该仓该批次下所有库位行一起置位);冻结只拦出不拦入(insertStockRow 不动);
--      审计走纯追加表 stock_freeze_log(查询热路径不碰)。
--   2) 价目表带价:price_list(供应商/客户维度)+ price_list_item(物品不含税单价+税率可空),
--      新建单据选物品时命中生效价目预填单价/税率(命中规则:owner+物品+单据日期在
--      [valid_from, valid_until] 内,边界任一可空=不限,status=1,同物品多条取 valid_from 最近)。
-- 红线:StockMapper.xml 仅 selectActiveStocks/selectPreAllocCandidates 两条 select 候选集
--      各加 AND NOT frozen,所有 UPDATE 不动;StockCoreService 仅手动指定批次校验段加冻结检查。
-- 幂等:ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS / ON CONFLICT DO NOTHING;
--      重复执行结果一致(开发库 inventory 与测试库 inventory_test 各执行一次)。

BEGIN;

-- ============================================================
-- 库存冻结位:stock 表加 frozen 列(仓+批次粒度,Java 层同批次行一起置位)
-- ============================================================
ALTER TABLE stock ADD COLUMN IF NOT EXISTS frozen boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN stock.frozen IS '冻结位(V26):true=该仓该批次禁止出库/预占,冻结只拦出不拦入';

-- ============================================================
-- 冻结审计流水(stock_freeze_log):纯追加,记录每次冻结/解冻
-- ============================================================
CREATE TABLE IF NOT EXISTS stock_freeze_log (
    id           serial NOT NULL                                    ,
    warehouse_id integer NOT NULL                                   ,
    batch_id     integer NOT NULL                                   ,
    item_id      integer NOT NULL                                   ,
    action       text NOT NULL                                      ,
    reason       text                                               ,
    operator     text                                               ,
    created_at   timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE stock_freeze_log IS '库存冻结审计流水(V26):纯追加,freeze/unfreeze 各一条,查询热路径不碰';

COMMENT ON COLUMN stock_freeze_log.id IS '主键ID';
COMMENT ON COLUMN stock_freeze_log.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN stock_freeze_log.batch_id IS '批次 ID';
COMMENT ON COLUMN stock_freeze_log.item_id IS '物品 ID(回填展示用)';
COMMENT ON COLUMN stock_freeze_log.action IS '操作:freeze 冻结 / unfreeze 解冻';
COMMENT ON COLUMN stock_freeze_log.reason IS '原因(冻结必填,解冻可空)';
COMMENT ON COLUMN stock_freeze_log.operator IS '操作人';
COMMENT ON COLUMN stock_freeze_log.created_at IS '创建时间';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'stock_freeze_log_pkey') THEN
        ALTER TABLE stock_freeze_log ADD CONSTRAINT stock_freeze_log_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- 索引:按 仓+批次 查最近原因/流水
CREATE INDEX IF NOT EXISTS idx_stock_freeze_log_wh_batch ON stock_freeze_log (warehouse_id, batch_id, id);

-- ============================================================
-- 价目表(price_list):供应商/客户维度的生效价目
-- ============================================================
CREATE TABLE IF NOT EXISTS price_list (
    id           serial NOT NULL                    ,
    owner_type   text NOT NULL                      ,
    owner_id     integer NOT NULL                   ,
    name         text                               ,
    valid_from   date                               ,
    valid_until  date                               ,
    status       integer DEFAULT 1 NOT NULL         ,
    creator      text                               ,
    created_at   timestamp without time zone        ,
    updater      text                               ,
    updated_at   timestamp without time zone
);

COMMENT ON TABLE price_list IS '价目表(V26):供应商/客户维度的生效价目,命中规则=owner+物品+日期在 [valid_from, valid_until] 内且 status=1';

COMMENT ON COLUMN price_list.id IS '主键ID';
COMMENT ON COLUMN price_list.owner_type IS '对方类型:supplier 供应商 / customer 客户';
COMMENT ON COLUMN price_list.owner_id IS '对方 ID(供应商/客户表主键)';
COMMENT ON COLUMN price_list.name IS '价目名称(可空,默认对方单位简称)';
COMMENT ON COLUMN price_list.valid_from IS '生效起(可空=不限)';
COMMENT ON COLUMN price_list.valid_until IS '生效止(可空=不限)';
COMMENT ON COLUMN price_list.status IS '状态:1 启用 / 0 停用(停用不参与命中)';
COMMENT ON COLUMN price_list.creator IS '创建人';
COMMENT ON COLUMN price_list.created_at IS '创建时间';
COMMENT ON COLUMN price_list.updater IS '更新人';
COMMENT ON COLUMN price_list.updated_at IS '更新时间';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'price_list_pkey') THEN
        ALTER TABLE price_list ADD CONSTRAINT price_list_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- 索引:命中查询按 对方类型+对方 ID 过滤
CREATE INDEX IF NOT EXISTS idx_price_list_owner ON price_list (owner_type, owner_id, valid_from);

-- ============================================================
-- 价目表行(price_list_item):物品不含税单价 + 税率可空(NULL=按物品默认税率)
-- ============================================================
CREATE TABLE IF NOT EXISTS price_list_item (
    id            serial NOT NULL                    ,
    price_list_id integer NOT NULL                   ,
    item_id       integer NOT NULL                   ,
    unit_price    numeric(18,4) NOT NULL             ,
    tax_rate      numeric(5,2)                       ,
    creator       text
);

COMMENT ON TABLE price_list_item IS '价目表行(V26):物品不含税单价,税率可空=NULL 按物品默认税率';

COMMENT ON COLUMN price_list_item.id IS '主键ID';
COMMENT ON COLUMN price_list_item.price_list_id IS '价目表 ID';
COMMENT ON COLUMN price_list_item.item_id IS '物品 ID';
COMMENT ON COLUMN price_list_item.unit_price IS '不含税单价';
COMMENT ON COLUMN price_list_item.tax_rate IS '税率(百分数,可空=NULL 按物品默认税率)';
COMMENT ON COLUMN price_list_item.creator IS '创建人';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'price_list_item_pkey') THEN
        ALTER TABLE price_list_item ADD CONSTRAINT price_list_item_pkey PRIMARY KEY (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'price_list_item_list_item_key') THEN
        ALTER TABLE price_list_item ADD CONSTRAINT price_list_item_list_item_key UNIQUE (price_list_id, item_id);
    END IF;
END $$;

-- ============================================================
-- 菜单与按钮:价目表(基础数据组) + 价目表编辑 + 库存冻结
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, type, path, sort, status)
VALUES
    (155, 5, 'price-lists', '价目表', 'menu', '/price-lists', 6, 1),
    (156, 155, 'price:edit', '价目表-编辑', 'button', NULL, 1, 1),
    (157, 16, 'stock:freeze', '库存-冻结/解冻', 'button', NULL, 11, 1)
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM sys_menu), 1));

-- 角色绑定:价目表菜单三角色可见(读跟随登录,operator/viewer 只读);
-- price:edit 与 stock:freeze 绑 admin(1) + operator(2),与同设备写操作同口径
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 155), (1, 156), (1, 157),
    (2, 155), (2, 156), (2, 157),
    (3, 155)
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
