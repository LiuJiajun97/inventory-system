-- =====================================================================
-- V3 增量 DDL:字典表 + 种子数据(一期收尾)
-- 规则:幂等(CREATE TABLE IF NOT EXISTS + INSERT ... ON CONFLICT DO NOTHING);
-- 对生产库(5433/inventory)与测试库(inventory_test)各执行一次。
-- 说明:状态机枚举(单据 status/bizCode/adjustType)不进字典,保持常量类。
-- =====================================================================

CREATE TABLE IF NOT EXISTS "Dict" (
    id          SERIAL PRIMARY KEY,
    "dictType"   TEXT NOT NULL,
    "dictKey"    TEXT NOT NULL,
    "dictLabel"  TEXT NOT NULL,
    "sortOrder"  INTEGER NOT NULL DEFAULT 0,
    "status"     INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT "Dict_type_key_unique" UNIQUE ("dictType", "dictKey")
);

-- 种子:仓库类型 / 物品分类 / 结算方式
INSERT INTO "Dict" ("dictType", "dictKey", "dictLabel", "sortOrder", "status") VALUES
    ('warehouseType', 'raw',      '原材料仓', 1, 1),
    ('warehouseType', 'finished', '成品仓',   2, 1),
    ('warehouseType', 'hardware', '五金仓',   3, 1),
    ('itemCategory',  'hardware', '五金',     1, 1),
    ('itemCategory',  'finished', '成品',     2, 1),
    ('itemCategory',  'raw',      '原料',     3, 1),
    ('settleMethod',  'prepay',   '预付',     1, 1),
    ('settleMethod',  'cod',      '货到付款', 2, 1),
    ('settleMethod',  'credit',   '账期',     3, 1)
ON CONFLICT ("dictType", "dictKey") DO NOTHING;
