-- V5: 全表统一审计字段(creator/createdAt/updater/updatedAt)
-- 1) Supplier/Customer 重命名 createdBy→creator, updatedBy→updater
-- 2) 12 张表补缺列

-- ---------- Supplier: 重命名列 ----------
ALTER TABLE "Supplier" RENAME COLUMN "createdBy" TO "creator";
ALTER TABLE "Supplier" RENAME COLUMN "updatedBy" TO "updater";

-- ---------- Customer: 重命名列 ----------
ALTER TABLE "Customer" RENAME COLUMN "createdBy" TO "creator";
ALTER TABLE "Customer" RENAME COLUMN "updatedBy" TO "updater";

-- ---------- Item: 补 creator/createdAt/updater/updatedAt ----------
ALTER TABLE "Item" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "Item" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;
ALTER TABLE "Item" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP NULL;

-- ---------- Warehouse: 补 creator/updater/updatedAt ----------
ALTER TABLE "Warehouse" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "Warehouse" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;
ALTER TABLE "Warehouse" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP NULL;

-- ---------- Location: 补全部四件套 ----------
ALTER TABLE "Location" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "Location" ADD COLUMN IF NOT EXISTS "createdAt" TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE "Location" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;
ALTER TABLE "Location" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP NULL;

-- ---------- User: 补 creator/updater/updatedAt ----------
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP NULL;

-- ---------- Dict: 补全部四件套 ----------
ALTER TABLE "Dict" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "Dict" ADD COLUMN IF NOT EXISTS "createdAt" TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE "Dict" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;
ALTER TABLE "Dict" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP NULL;

-- ---------- DictType: 补 creator/updater ----------
ALTER TABLE "DictType" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "DictType" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;

-- ---------- InboundDoc: 补 updater/updatedAt ----------
ALTER TABLE "InboundDoc" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;
ALTER TABLE "InboundDoc" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP NULL;

-- ---------- OutboundDoc: 补 updater/updatedAt ----------
ALTER TABLE "OutboundDoc" ADD COLUMN IF NOT EXISTS "updater" VARCHAR(64) NULL;
ALTER TABLE "OutboundDoc" ADD COLUMN IF NOT EXISTS "updatedAt" TIMESTAMP NULL;

-- ---------- 5 张明细表: 补 creator ----------
ALTER TABLE "PurchaseOrderItem" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "SalesOrderItem" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "TransferDocItem" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "StocktakeDocItem" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
ALTER TABLE "StockAdjustDocItem" ADD COLUMN IF NOT EXISTS "creator" VARCHAR(64) NULL;
