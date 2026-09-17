-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- =====================================================================
-- 进销存一期增量 DDL(方案 V2.1 §3)
-- 规则:只加表加列,不 DROP/ALTER 已有列;对生产库(5433/inventory)与
-- 测试库(inventory_test)各执行一次。
-- =====================================================================

-- ---------- 3.1 主数据扩展 ----------
ALTER TABLE item ADD COLUMN category TEXT;
ALTER TABLE item ADD COLUMN min_stock NUMERIC(18, 4);
ALTER TABLE item ADD COLUMN default_tax_rate NUMERIC(5, 2) NOT NULL DEFAULT 13.00;

CREATE TABLE IF NOT EXISTS supplier (
    id               SERIAL PRIMARY KEY,
    supplier_code   TEXT NOT NULL,
    supplier_name   TEXT NOT NULL,
    tax_no          TEXT,
    default_tax_rate NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    contact        TEXT,
    phone          TEXT,
    address        TEXT,
    settle_method   TEXT,
    pay_term_days    INTEGER,
    status         INTEGER NOT NULL DEFAULT 1,
    remark         TEXT,
    created_by      TEXT,
    created_at      TIMESTAMP,
    updated_by      TEXT,
    updated_at      TIMESTAMP,
    CONSTRAINT "Supplier_supplierCode_key" UNIQUE (supplier_code)
);

CREATE TABLE IF NOT EXISTS customer (
    id               SERIAL PRIMARY KEY,
    customer_code   TEXT NOT NULL,
    customer_name   TEXT NOT NULL,
    tax_no          TEXT,
    default_tax_rate NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    contact        TEXT,
    phone          TEXT,
    address        TEXT,
    settle_method   TEXT,
    pay_term_days    INTEGER,
    status         INTEGER NOT NULL DEFAULT 1,
    remark         TEXT,
    created_by      TEXT,
    created_at      TIMESTAMP,
    updated_by      TEXT,
    updated_at      TIMESTAMP,
    CONSTRAINT "Customer_customerCode_key" UNIQUE (customer_code)
);

-- ---------- 3.2 采购 ----------
CREATE TABLE IF NOT EXISTS purchase_order (
    id                     SERIAL PRIMARY KEY,
    doc_no                TEXT NOT NULL,
    doc_date              DATE NOT NULL,
    supplier_id           INTEGER NOT NULL,
    buyer_id              INTEGER NOT NULL,
    allow_over_receipt_rate NUMERIC(5, 4) NOT NULL DEFAULT 0,
    total_amount          NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_tax_amount       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_tax_inclusive    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    status               TEXT NOT NULL DEFAULT 'draft',
    creator              TEXT,
    created_at            TIMESTAMP,
    updater              TEXT,
    updated_at            TIMESTAMP,
    approver             TEXT,
    approved_at           TIMESTAMP,
    reject_reason         TEXT,
    remark               TEXT,
    CONSTRAINT "PurchaseOrder_docNo_key" UNIQUE (doc_no)
);

CREATE TABLE IF NOT EXISTS purchase_order_item (
    id                   SERIAL PRIMARY KEY,
    order_id            INTEGER NOT NULL,
    line_no             INTEGER NOT NULL,
    item_id             INTEGER NOT NULL,
    spec_snapshot       TEXT,
    unit               TEXT,
    ordered_qty         NUMERIC(18, 4) NOT NULL,
    arrived_qty         NUMERIC(18, 4) NOT NULL DEFAULT 0,
    expected_delivery_date DATE,
    unit_price          NUMERIC(18, 4) NOT NULL,
    tax_rate            NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    amount             NUMERIC(18, 4) NOT NULL,
    tax_amount          NUMERIC(18, 4) NOT NULL,
    tax_inclusive_total  NUMERIC(18, 4) NOT NULL,
    closed             BOOLEAN NOT NULL DEFAULT FALSE,
    line_remark         TEXT
);

-- ---------- 3.3 销售 ----------
CREATE TABLE IF NOT EXISTS sales_order (
    id                  SERIAL PRIMARY KEY,
    doc_no             TEXT NOT NULL,
    doc_date           DATE NOT NULL,
    customer_id        INTEGER NOT NULL,
    salesperson_id     INTEGER NOT NULL,
    warehouse_id       INTEGER NOT NULL,
    total_amount       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_tax_amount    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    total_tax_inclusive NUMERIC(18, 4) NOT NULL DEFAULT 0,
    status            TEXT NOT NULL DEFAULT 'draft',
    creator           TEXT,
    created_at         TIMESTAMP,
    updater           TEXT,
    updated_at         TIMESTAMP,
    approver          TEXT,
    approved_at        TIMESTAMP,
    reject_reason      TEXT,
    remark            TEXT,
    CONSTRAINT "SalesOrder_docNo_key" UNIQUE (doc_no)
);

CREATE TABLE IF NOT EXISTS sales_order_item (
    id                    SERIAL PRIMARY KEY,
    order_id             INTEGER NOT NULL,
    line_no              INTEGER NOT NULL,
    item_id              INTEGER NOT NULL,
    spec_snapshot        TEXT,
    unit                TEXT,
    ordered_qty          NUMERIC(18, 4) NOT NULL,
    shipped_qty          NUMERIC(18, 4) NOT NULL DEFAULT 0,
    customer_delivery_date DATE,
    unit_price           NUMERIC(18, 4) NOT NULL,
    tax_rate             NUMERIC(5, 2) NOT NULL DEFAULT 13.00,
    amount              NUMERIC(18, 4) NOT NULL,
    tax_amount           NUMERIC(18, 4) NOT NULL,
    tax_inclusive_total   NUMERIC(18, 4) NOT NULL,
    closed              BOOLEAN NOT NULL DEFAULT FALSE,
    line_remark          TEXT
);

-- ---------- 3.4 调拨 ----------
CREATE TABLE IF NOT EXISTS transfer_doc (
    id                SERIAL PRIMARY KEY,
    doc_no           TEXT NOT NULL,
    doc_date         DATE NOT NULL,
    from_warehouse_id INTEGER NOT NULL,
    to_warehouse_id   INTEGER NOT NULL,
    total_amount     NUMERIC(18, 4) NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'draft',
    creator         TEXT,
    created_at       TIMESTAMP,
    updater         TEXT,
    updated_at       TIMESTAMP,
    approver        TEXT,
    approved_at      TIMESTAMP,
    reject_reason    TEXT,
    remark          TEXT,
    CONSTRAINT "TransferDoc_docNo_key" UNIQUE (doc_no)
);

CREATE TABLE IF NOT EXISTS transfer_doc_item (
    id               SERIAL PRIMARY KEY,
    doc_id          INTEGER NOT NULL,
    line_no         INTEGER NOT NULL,
    item_id         INTEGER NOT NULL,
    spec_snapshot   TEXT,
    unit           TEXT,
    qty            NUMERIC(18, 4) NOT NULL,
    unit_price      NUMERIC(18, 4) NOT NULL,
    from_location_id INTEGER,
    to_location_id   INTEGER,
    line_remark     TEXT
);

-- ---------- 3.5 盘点 / 调整 ----------
CREATE TABLE IF NOT EXISTS stocktake_doc (
    id           SERIAL PRIMARY KEY,
    doc_no      TEXT NOT NULL,
    doc_date    DATE NOT NULL,
    warehouse_id INTEGER NOT NULL,
    scope_type  TEXT NOT NULL DEFAULT 'all',
    status     TEXT NOT NULL DEFAULT 'draft',
    creator    TEXT,
    created_at  TIMESTAMP,
    updater    TEXT,
    updated_at  TIMESTAMP,
    approver   TEXT,
    approved_at TIMESTAMP,
    reject_reason TEXT,
    remark     TEXT,
    CONSTRAINT "StocktakeDoc_docNo_key" UNIQUE (doc_no)
);

CREATE TABLE IF NOT EXISTS stocktake_doc_item (
    id             SERIAL PRIMARY KEY,
    doc_id        INTEGER NOT NULL,
    line_no       INTEGER NOT NULL,
    item_id       INTEGER NOT NULL,
    spec_snapshot TEXT,
    unit         TEXT,
    batch_id      INTEGER NOT NULL DEFAULT 0,
    location_id   INTEGER NOT NULL DEFAULT 0,
    book_qty      NUMERIC(18, 4) NOT NULL,
    actual_qty    NUMERIC(18, 4),
    diff_qty      NUMERIC(18, 4)
);

CREATE TABLE IF NOT EXISTS stock_adjust_doc (
    id           SERIAL PRIMARY KEY,
    doc_no      TEXT NOT NULL,
    doc_date    DATE NOT NULL,
    warehouse_id INTEGER NOT NULL,
    adjust_type TEXT NOT NULL,
    ref_doc_no   TEXT,
    status     TEXT NOT NULL DEFAULT 'draft',
    creator    TEXT,
    created_at  TIMESTAMP,
    updater    TEXT,
    updated_at  TIMESTAMP,
    approver   TEXT,
    approved_at TIMESTAMP,
    reject_reason TEXT,
    remark     TEXT,
    CONSTRAINT "StockAdjustDoc_docNo_key" UNIQUE (doc_no)
);

CREATE TABLE IF NOT EXISTS stock_adjust_doc_item (
    id             SERIAL PRIMARY KEY,
    doc_id        INTEGER NOT NULL,
    line_no       INTEGER NOT NULL,
    item_id       INTEGER NOT NULL,
    spec_snapshot TEXT,
    unit         TEXT,
    batch_id      INTEGER NOT NULL DEFAULT 0,
    location_id   INTEGER NOT NULL DEFAULT 0,
    qty          NUMERIC(18, 4) NOT NULL,
    unit_price    NUMERIC(18, 4),
    reason       TEXT
);

-- ---------- 3.6 现有表增量 ----------
ALTER TABLE stock ADD COLUMN pre_allocated_qty NUMERIC(18, 4) NOT NULL DEFAULT 0;

ALTER TABLE inbound_doc ADD COLUMN ref_type TEXT;
ALTER TABLE inbound_doc ADD COLUMN ref_doc_id INTEGER;
ALTER TABLE inbound_doc ADD COLUMN doc_date DATE;

ALTER TABLE inbound_doc_item ADD COLUMN unit_price NUMERIC(18, 4);
ALTER TABLE inbound_doc_item ADD COLUMN tax_rate NUMERIC(5, 2);
ALTER TABLE inbound_doc_item ADD COLUMN batch_no TEXT;
ALTER TABLE inbound_doc_item ADD COLUMN production_date DATE;
ALTER TABLE inbound_doc_item ADD COLUMN expiry_date DATE;
-- 工程补充:采购到货行 → 采购订单行 关联(用于 arrivedQty 精确回写,方案未明说,见报告)
ALTER TABLE inbound_doc_item ADD COLUMN ref_line_id INTEGER;

ALTER TABLE outbound_doc ADD COLUMN ref_type TEXT;
ALTER TABLE outbound_doc ADD COLUMN ref_doc_id INTEGER;
ALTER TABLE outbound_doc ADD COLUMN doc_date DATE;

ALTER TABLE outbound_doc_item ADD COLUMN unit_price NUMERIC(18, 4);
-- 工程补充:销售发货行 → 销售订单行 关联(用于 shippedQty 精确回写)
ALTER TABLE outbound_doc_item ADD COLUMN ref_line_id INTEGER;
