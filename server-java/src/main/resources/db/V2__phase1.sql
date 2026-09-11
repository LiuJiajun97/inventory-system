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
    "createdBy"      TEXT,
    "createdAt"      TIMESTAMP,
    "updatedBy"      TEXT,
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
    "createdBy"      TEXT,
    "createdAt"      TIMESTAMP,
    "updatedBy"      TEXT,
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
    "lineRemark"         TEXT
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
    "lineRemark"          TEXT
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
    "lineRemark"     TEXT
);

-- ---------- 3.5 盘点 / 调整 ----------
CREATE TABLE IF NOT EXISTS "StocktakeDoc" (
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
    "diffQty"      NUMERIC(18, 4)
);

CREATE TABLE IF NOT EXISTS "StockAdjustDoc" (
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
    "reason"       TEXT
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
