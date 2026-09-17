-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V4: 字典类型表(动态管理字典分类)
CREATE TABLE IF NOT EXISTS dict_type (
    id          BIGSERIAL PRIMARY KEY,
    type_code  VARCHAR(64) NOT NULL,
    type_name  VARCHAR(64) NOT NULL,
    remark    VARCHAR(255) DEFAULT '',
    status    SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT "uk_dict_type_code" UNIQUE (type_code)
);

-- 种子:3 类
INSERT INTO dict_type (type_code, type_name, remark, status) VALUES
    ('warehouseType', '仓库类型', '用于标识仓库的业务分类', 1),
    ('itemCategory',  '物品分类', '用于标识物品的类别', 1),
    ('settleMethod',  '结算方式', '供应商/客户的结算方式', 1)
ON CONFLICT (type_code) DO NOTHING;
