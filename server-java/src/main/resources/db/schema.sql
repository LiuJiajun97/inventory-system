-- 库存系统 PostgreSQL DDL(与 server/prisma/schema.prisma 等价,12 张表)
-- 表名为 Prisma 模型名(PascalCase),列为 camelCase,Decimal(18,4)
-- 生产库(5433/inventory)已由 Fastify 版建好,本文件仅用于新环境初始化;
-- 测试库 inventory_test 已存在等价结构,测试启动前只清空数据、不重建表。

CREATE TABLE IF NOT EXISTS "Warehouse" (
    id             SERIAL PRIMARY KEY,
    warehouseCode  TEXT NOT NULL,
    warehouseName  TEXT NOT NULL,
    warehouseType  TEXT NOT NULL,
    enableBatch    BOOLEAN NOT NULL DEFAULT FALSE,
    enableExpiry   BOOLEAN NOT NULL DEFAULT FALSE,
    enableSerial   BOOLEAN NOT NULL DEFAULT FALSE,
    enableLocation BOOLEAN NOT NULL DEFAULT FALSE,
    status         INTEGER NOT NULL DEFAULT 1,
    creator        VARCHAR(64),
    createdAt      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(64),
    updatedAt      TIMESTAMP,
    CONSTRAINT "Warehouse_warehouseCode_key" UNIQUE (warehouseCode)
);

CREATE TABLE IF NOT EXISTS "Item" (
    id         SERIAL PRIMARY KEY,
    itemCode   TEXT NOT NULL,
    itemName   TEXT NOT NULL,
    unit       TEXT NOT NULL,
    spec       TEXT,
    attributes TEXT,
    status     INTEGER NOT NULL DEFAULT 1,
    creator    VARCHAR(64),
    createdAt  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater    VARCHAR(64),
    updatedAt  TIMESTAMP,
    CONSTRAINT "Item_itemCode_key" UNIQUE (itemCode)
);

CREATE TABLE IF NOT EXISTS "Batch" (
    id             SERIAL PRIMARY KEY,
    itemId         INTEGER NOT NULL,
    batchNo        TEXT NOT NULL,
    productionDate DATE,
    expiryDate     DATE,
    supplier       TEXT,
    status         TEXT NOT NULL DEFAULT 'active',
    CONSTRAINT "Batch_itemId_batchNo_key" UNIQUE (itemId, batchNo),
    CONSTRAINT "Batch_itemId_fkey" FOREIGN KEY (itemId) REFERENCES "Item" (id)
);

CREATE TABLE IF NOT EXISTS "Location" (
    id           SERIAL PRIMARY KEY,
    warehouseId  INTEGER NOT NULL,
    locationCode TEXT NOT NULL,
    locationName TEXT,
    creator      VARCHAR(64),
    createdAt    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(64),
    updatedAt    TIMESTAMP,
    CONSTRAINT "Location_warehouseId_locationCode_key" UNIQUE (warehouseId, locationCode),
    CONSTRAINT "Location_warehouseId_fkey" FOREIGN KEY (warehouseId) REFERENCES "Warehouse" (id)
);

CREATE TABLE IF NOT EXISTS "Serial" (
    id           SERIAL PRIMARY KEY,
    itemId       INTEGER NOT NULL,
    serialNo     TEXT NOT NULL,
    warehouseId  INTEGER,
    status       TEXT NOT NULL DEFAULT 'in_stock',
    inboundTime  TIMESTAMP,
    outboundTime TIMESTAMP,
    CONSTRAINT "Serial_itemId_serialNo_key" UNIQUE (itemId, serialNo),
    CONSTRAINT "Serial_itemId_fkey" FOREIGN KEY (itemId) REFERENCES "Item" (id),
    CONSTRAINT "Serial_warehouseId_fkey" FOREIGN KEY (warehouseId) REFERENCES "Warehouse" (id)
);

CREATE TABLE IF NOT EXISTS "Stock" (
    id          SERIAL PRIMARY KEY,
    warehouseId INTEGER NOT NULL,
    itemId      INTEGER NOT NULL,
    batchId     INTEGER NOT NULL DEFAULT 0,
    locationId  INTEGER NOT NULL DEFAULT 0,
    quantity    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    updatedAt   TIMESTAMP NOT NULL,
    CONSTRAINT "Stock_warehouseId_itemId_batchId_locationId_key"
        UNIQUE (warehouseId, itemId, batchId, locationId)
);

CREATE TABLE IF NOT EXISTS "StockTransaction" (
    id          SERIAL PRIMARY KEY,
    warehouseId INTEGER NOT NULL,
    itemId      INTEGER NOT NULL,
    batchId     INTEGER NOT NULL DEFAULT 0,
    locationId  INTEGER NOT NULL DEFAULT 0,
    changeQty   NUMERIC(18, 4) NOT NULL,
    afterQty    NUMERIC(18, 4) NOT NULL,
    bizCode     TEXT NOT NULL,
    docNo       TEXT,
    operator    TEXT,
    createdAt   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "InboundDoc" (
    id          SERIAL PRIMARY KEY,
    docNo       TEXT NOT NULL,
    warehouseId INTEGER NOT NULL,
    status      TEXT NOT NULL DEFAULT 'finished',
    remark      TEXT,
    creator     TEXT,
    createdAt   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     TEXT,
    updatedAt   TIMESTAMP,
    CONSTRAINT "InboundDoc_docNo_key" UNIQUE (docNo),
    CONSTRAINT "InboundDoc_warehouseId_fkey" FOREIGN KEY (warehouseId) REFERENCES "Warehouse" (id)
);

CREATE TABLE IF NOT EXISTS "OutboundDoc" (
    id          SERIAL PRIMARY KEY,
    docNo       TEXT NOT NULL,
    warehouseId INTEGER NOT NULL,
    status      TEXT NOT NULL DEFAULT 'finished',
    remark      TEXT,
    creator     TEXT,
    createdAt   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     TEXT,
    updatedAt   TIMESTAMP,
    CONSTRAINT "OutboundDoc_docNo_key" UNIQUE (docNo),
    CONSTRAINT "OutboundDoc_warehouseId_fkey" FOREIGN KEY (warehouseId) REFERENCES "Warehouse" (id)
);

CREATE TABLE IF NOT EXISTS "InboundDocItem" (
    id         SERIAL PRIMARY KEY,
    docId      INTEGER NOT NULL,
    itemId     INTEGER NOT NULL,
    quantity   NUMERIC(18, 4) NOT NULL,
    batchId    INTEGER NOT NULL DEFAULT 0,
    locationId INTEGER NOT NULL DEFAULT 0,
    serialNos  TEXT,
    CONSTRAINT "InboundDocItem_docId_fkey" FOREIGN KEY (docId) REFERENCES "InboundDoc" (id)
);

CREATE TABLE IF NOT EXISTS "OutboundDocItem" (
    id         SERIAL PRIMARY KEY,
    docId      INTEGER NOT NULL,
    itemId     INTEGER NOT NULL,
    quantity   NUMERIC(18, 4) NOT NULL,
    batchId    INTEGER NOT NULL DEFAULT 0,
    locationId INTEGER NOT NULL DEFAULT 0,
    serialNos  TEXT,
    CONSTRAINT "OutboundDocItem_docId_fkey" FOREIGN KEY (docId) REFERENCES "OutboundDoc" (id)
);

CREATE TABLE IF NOT EXISTS "User" (
    id           SERIAL PRIMARY KEY,
    username     TEXT NOT NULL,
    passwordHash TEXT NOT NULL,
    name         TEXT NOT NULL,
    role         TEXT NOT NULL,
    status       INTEGER NOT NULL DEFAULT 1,
    creator      VARCHAR(64),
    createdAt    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(64),
    updatedAt    TIMESTAMP,
    CONSTRAINT "User_username_key" UNIQUE (username)
);

-- =====================================================================
-- 一期增量(V2__phase1.sql 同款,新环境一次建成)
-- =====================================================================
-- =====================================================================
-- 进销存一期增量 DDL(方案 V2.1 §3)
-- 规则:只加表加列,不 DROP/ALTER 已有列;对生产库(5433/inventory)与
-- 测试库(inventory_test)各执行一次。
-- =====================================================================

-- ---------- 3.1 主数据扩展 ----------
ALTER TABLE "Item" ADD COLUMN "category" TEXT;
ALTER TABLE "Item" ADD COLUMN "minStock" NUMERIC(18, 4);
ALTER TABLE "Item" ADD COLUMN "defaultTaxRate" NUMERIC(5, 2) NOT NULL DEFAULT 13.00;

CREATE TABLE IF NOT EXISTS "Supplier" (
    id               SERIAL PRIMARY KEY,
    "supplierCode"   TEXT NOT NULL,
    "supplierName"   TEXT NOT NULL,
    "taxNo"          TEXT,
    "defaultTaxRate" NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    "contact"        TEXT,
    "phone"          TEXT,
    "address"        TEXT,
    "settleMethod"   TEXT,
    "payTermDays"    INTEGER,
    "status"         INTEGER NOT NULL DEFAULT 1,
    "remark"         TEXT,
    "creator"        TEXT,
    "createdAt"      TIMESTAMP,
    "updater"        TEXT,
    "updatedAt"      TIMESTAMP,
    CONSTRAINT "Supplier_supplierCode_key" UNIQUE ("supplierCode")
);

CREATE TABLE IF NOT EXISTS "Customer" (
    id               SERIAL PRIMARY KEY,
    "customerCode"   TEXT NOT NULL,
    "customerName"   TEXT NOT NULL,
    "taxNo"          TEXT,
    "defaultTaxRate" NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    "contact"        TEXT,
    "phone"          TEXT,
    "address"        TEXT,
    "settleMethod"   TEXT,
    "payTermDays"    INTEGER,
    "status"         INTEGER NOT NULL DEFAULT 1,
    "remark"         TEXT,
    "creator"        TEXT,
    "createdAt"      TIMESTAMP,
    "updater"        TEXT,
    "updatedAt"      TIMESTAMP,
    CONSTRAINT "Customer_customerCode_key" UNIQUE ("customerCode")
);

-- ---------- 3.2 采购 ----------
CREATE TABLE IF NOT EXISTS "PurchaseOrder" (
    id                     SERIAL PRIMARY KEY,
    "docNo"                TEXT NOT NULL,
    "docDate"              DATE NOT NULL,
    "supplierId"           INTEGER NOT NULL,
    "buyerId"              INTEGER NOT NULL,
    "allowOverReceiptRate" NUMERIC(5, 4) NOT NULL DEFAULT 0,
    "totalAmount"          NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "totalTaxAmount"       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "totalTaxInclusive"    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "status"               TEXT NOT NULL DEFAULT 'draft',
    "creator"              TEXT,
    "createdAt"            TIMESTAMP,
    "updater"              TEXT,
    "updatedAt"            TIMESTAMP,
    "approver"             TEXT,
    "approvedAt"           TIMESTAMP,
    "rejectReason"         TEXT,
    "remark"               TEXT,
    CONSTRAINT "PurchaseOrder_docNo_key" UNIQUE ("docNo")
);

CREATE TABLE IF NOT EXISTS "PurchaseOrderItem" (
    id                   SERIAL PRIMARY KEY,
    "orderId"            INTEGER NOT NULL,
    "lineNo"             INTEGER NOT NULL,
    "itemId"             INTEGER NOT NULL,
    "specSnapshot"       TEXT,
    "unit"               TEXT,
    "orderedQty"         NUMERIC(18, 4) NOT NULL,
    "arrivedQty"         NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "expectedDeliveryDate" DATE,
    "unitPrice"          NUMERIC(18, 4) NOT NULL,
    "taxRate"            NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    "amount"             NUMERIC(18, 4) NOT NULL,
    "taxAmount"          NUMERIC(18, 4) NOT NULL,
    "taxInclusiveTotal"  NUMERIC(18, 4) NOT NULL,
    "closed"             BOOLEAN NOT NULL DEFAULT FALSE,
    "lineRemark"         TEXT,
    "creator"            VARCHAR(64)
);

-- ---------- 3.3 销售 ----------
CREATE TABLE IF NOT EXISTS "SalesOrder" (
    id                  SERIAL PRIMARY KEY,
    "docNo"             TEXT NOT NULL,
    "docDate"           DATE NOT NULL,
    "customerId"        INTEGER NOT NULL,
    "salespersonId"     INTEGER NOT NULL,
    "warehouseId"       INTEGER NOT NULL,
    "totalAmount"       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "totalTaxAmount"    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "totalTaxInclusive" NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "status"            TEXT NOT NULL DEFAULT 'draft',
    "creator"           TEXT,
    "createdAt"         TIMESTAMP,
    "updater"           TEXT,
    "updatedAt"         TIMESTAMP,
    "approver"          TEXT,
    "approvedAt"        TIMESTAMP,
    "rejectReason"      TEXT,
    "remark"            TEXT,
    CONSTRAINT "SalesOrder_docNo_key" UNIQUE ("docNo")
);

CREATE TABLE IF NOT EXISTS "SalesOrderItem" (
    id                    SERIAL PRIMARY KEY,
    "orderId"             INTEGER NOT NULL,
    "lineNo"              INTEGER NOT NULL,
    "itemId"              INTEGER NOT NULL,
    "specSnapshot"        TEXT,
    "unit"                TEXT,
    "orderedQty"          NUMERIC(18, 4) NOT NULL,
    "shippedQty"          NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "customerDeliveryDate" DATE,
    "unitPrice"           NUMERIC(18, 4) NOT NULL,
    "taxRate"             NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    "amount"              NUMERIC(18, 4) NOT NULL,
    "taxAmount"           NUMERIC(18, 4) NOT NULL,
    "taxInclusiveTotal"   NUMERIC(18, 4) NOT NULL,
    "closed"              BOOLEAN NOT NULL DEFAULT FALSE,
    "lineRemark"          TEXT,
    "creator"             VARCHAR(64)
);

-- ---------- 3.4 调拨 ----------
CREATE TABLE IF NOT EXISTS "TransferDoc" (
    id                SERIAL PRIMARY KEY,
    "docNo"           TEXT NOT NULL,
    "docDate"         DATE NOT NULL,
    "fromWarehouseId" INTEGER NOT NULL,
    "toWarehouseId"   INTEGER NOT NULL,
    "totalAmount"     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    "status"          TEXT NOT NULL DEFAULT 'draft',
    "creator"         TEXT,
    "createdAt"       TIMESTAMP,
    "updater"         TEXT,
    "updatedAt"       TIMESTAMP,
    "approver"        TEXT,
    "approvedAt"      TIMESTAMP,
    "rejectReason"    TEXT,
    "remark"          TEXT,
    CONSTRAINT "TransferDoc_docNo_key" UNIQUE ("docNo")
);

CREATE TABLE IF NOT EXISTS "TransferDocItem" (
    id               SERIAL PRIMARY KEY,
    "docId"          INTEGER NOT NULL,
    "lineNo"         INTEGER NOT NULL,
    "itemId"         INTEGER NOT NULL,
    "specSnapshot"   TEXT,
    "unit"           TEXT,
    "qty"            NUMERIC(18, 4) NOT NULL,
    "unitPrice"      NUMERIC(18, 4) NOT NULL,
    "fromLocationId" INTEGER,
    "toLocationId"   INTEGER,
    "lineRemark"     TEXT,
    "creator"        VARCHAR(64)
);

-- ---------- 3.5 盘点 / 调整 ----------
    id           SERIAL PRIMARY KEY,
    "docNo"      TEXT NOT NULL,
    "docDate"    DATE NOT NULL,
    "warehouseId" INTEGER NOT NULL,
    "scopeType"  TEXT NOT NULL DEFAULT 'all',
    "status"     TEXT NOT NULL DEFAULT 'draft',
    "creator"    TEXT,
    "createdAt"  TIMESTAMP,
    "updater"    TEXT,
    "updatedAt"  TIMESTAMP,
    "approver"   TEXT,
    "approvedAt" TIMESTAMP,
    "rejectReason" TEXT,
    "remark"     TEXT,
    CONSTRAINT "StocktakeDoc_docNo_key" UNIQUE ("docNo")
);

CREATE TABLE IF NOT EXISTS "StocktakeDocItem" (
    id             SERIAL PRIMARY KEY,
    "docId"        INTEGER NOT NULL,
    "lineNo"       INTEGER NOT NULL,
    "itemId"       INTEGER NOT NULL,
    "specSnapshot" TEXT,
    "unit"         TEXT,
    "batchId"      INTEGER NOT NULL DEFAULT 0,
    "locationId"   INTEGER NOT NULL DEFAULT 0,
    "bookQty"      NUMERIC(18, 4) NOT NULL,
    "actualQty"    NUMERIC(18, 4),
    "diffQty"      NUMERIC(18, 4),
    "creator"      VARCHAR(64)
);
    id           SERIAL PRIMARY KEY,
    "docNo"      TEXT NOT NULL,
    "docDate"    DATE NOT NULL,
    "warehouseId" INTEGER NOT NULL,
    "adjustType" TEXT NOT NULL,
    "refDocNo"   TEXT,
    "status"     TEXT NOT NULL DEFAULT 'draft',
    "creator"    TEXT,
    "createdAt"  TIMESTAMP,
    "updater"    TEXT,
    "updatedAt"  TIMESTAMP,
    "approver"   TEXT,
    "approvedAt" TIMESTAMP,
    "rejectReason" TEXT,
    "remark"     TEXT,
    CONSTRAINT "StockAdjustDoc_docNo_key" UNIQUE ("docNo")
);

CREATE TABLE IF NOT EXISTS "StockAdjustDocItem" (
    id             SERIAL PRIMARY KEY,
    "docId"        INTEGER NOT NULL,
    "lineNo"       INTEGER NOT NULL,
    "itemId"       INTEGER NOT NULL,
    "specSnapshot" TEXT,
    "unit"         TEXT,
    "batchId"      INTEGER NOT NULL DEFAULT 0,
    "locationId"   INTEGER NOT NULL DEFAULT 0,
    "qty"          NUMERIC(18, 4) NOT NULL,
    "unitPrice"    NUMERIC(18, 4),
    "reason"       TEXT,
    "creator"      VARCHAR(64)
);

-- ---------- 3.6 现有表增量 ----------
ALTER TABLE "Stock" ADD COLUMN "preAllocatedQty" NUMERIC(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE "InboundDoc" ADD COLUMN "refType" TEXT;
ALTER TABLE "InboundDoc" ADD COLUMN "refDocId" INTEGER;
ALTER TABLE "InboundDoc" ADD COLUMN "docDate" DATE;

ALTER TABLE "InboundDocItem" ADD COLUMN "unitPrice" NUMERIC(18, 4);
ALTER TABLE "InboundDocItem" ADD COLUMN "taxRate" NUMERIC(5, 2);
ALTER TABLE "InboundDocItem" ADD COLUMN "batchNo" TEXT;
ALTER TABLE "InboundDocItem" ADD COLUMN "productionDate" DATE;
ALTER TABLE "InboundDocItem" ADD COLUMN "expiryDate" DATE;
-- 工程补充:采购到货行 → 采购订单行 关联(用于 arrivedQty 精确回写,方案未明说,见报告)
ALTER TABLE "InboundDocItem" ADD COLUMN "refLineId" INTEGER;

ALTER TABLE "OutboundDoc" ADD COLUMN "refType" TEXT;
ALTER TABLE "OutboundDoc" ADD COLUMN "refDocId" INTEGER;
ALTER TABLE "OutboundDoc" ADD COLUMN "docDate" DATE;

ALTER TABLE "OutboundDocItem" ADD COLUMN "unitPrice" NUMERIC(18, 4);
-- 工程补充:销售发货行 → 销售订单行 关联(用于 shippedQty 精确回写)
ALTER TABLE "OutboundDocItem" ADD COLUMN "refLineId" INTEGER;

-- ---------- 3.7 字典表(V3,新环境随 schema 建表 + 种子) ----------
CREATE TABLE IF NOT EXISTS "Dict" (
    id          SERIAL PRIMARY KEY,
    "dictType"   TEXT NOT NULL,
    "dictKey"    TEXT NOT NULL,
    "dictLabel"  TEXT NOT NULL,
    "sortOrder"  INTEGER NOT NULL DEFAULT 0,
    "status"     INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT "Dict_type_key_unique" UNIQUE ("dictType", "dictKey")
);

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
