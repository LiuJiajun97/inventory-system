-- V4: 字典类型表(动态管理字典分类)
CREATE TABLE IF NOT EXISTS "DictType" (
    id          BIGSERIAL PRIMARY KEY,
    "typeCode"  VARCHAR(64) NOT NULL,
    "typeName"  VARCHAR(64) NOT NULL,
    "remark"    VARCHAR(255) DEFAULT '',
    "status"    SMALLINT NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP NOT NULL DEFAULT now(),
    "updatedAt" TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT "uk_dict_type_code" UNIQUE ("typeCode")
);

-- 种子:3 类
INSERT INTO "DictType" ("typeCode", "typeName", "remark", "status") VALUES
    ('warehouseType', '仓库类型', '用于标识仓库的业务分类', 1),
    ('itemCategory',  '物品分类', '用于标识物品的类别', 1),
    ('settleMethod',  '结算方式', '供应商/客户的结算方式', 1)
ON CONFLICT ("typeCode") DO NOTHING;
