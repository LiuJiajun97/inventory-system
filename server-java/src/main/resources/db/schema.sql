-- 库存系统 PostgreSQL DDL(与 server/prisma/schema.prisma 等价,12 张表)
-- 表名为 Prisma 模型名(PascalCase),列为 camelCase,Decimal(18,4)
-- 生产库(5433/inventory)已由 Fastify 版建好,本文件仅用于新环境初始化;
-- 测试库 inventory_test 已存在等价结构,测试启动前只清空数据、不重建表。

CREATE TABLE IF NOT EXISTS warehouse (
    id             SERIAL PRIMARY KEY,
    warehouse_code  TEXT NOT NULL,
    warehouse_name  TEXT NOT NULL,
    warehouse_type  TEXT NOT NULL,
    enable_batch    BOOLEAN NOT NULL DEFAULT FALSE,
    enable_expiry   BOOLEAN NOT NULL DEFAULT FALSE,
    enable_serial   BOOLEAN NOT NULL DEFAULT FALSE,
    enable_location BOOLEAN NOT NULL DEFAULT FALSE,
    status         INTEGER NOT NULL DEFAULT 1,
    creator        VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(64),
    updated_at      TIMESTAMP,
    CONSTRAINT "Warehouse_warehouseCode_key" UNIQUE (warehouse_code)
);

CREATE TABLE IF NOT EXISTS item (
    id         SERIAL PRIMARY KEY,
    item_code   TEXT NOT NULL,
    item_name   TEXT NOT NULL,
    unit       TEXT NOT NULL,
    spec       TEXT,
    attributes TEXT,
    status     INTEGER NOT NULL DEFAULT 1,
    creator    VARCHAR(64),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater    VARCHAR(64),
    updated_at  TIMESTAMP,
    CONSTRAINT "Item_itemCode_key" UNIQUE (item_code)
);

CREATE TABLE IF NOT EXISTS batch (
    id             SERIAL PRIMARY KEY,
    item_id         INTEGER NOT NULL,
    batch_no        TEXT NOT NULL,
    production_date DATE,
    expiry_date     DATE,
    supplier       TEXT,
    status         TEXT NOT NULL DEFAULT 'active',
    CONSTRAINT "Batch_itemId_batchNo_key" UNIQUE (item_id, batch_no),
    CONSTRAINT "Batch_itemId_fkey" FOREIGN KEY (item_id) REFERENCES item (id)
);

CREATE TABLE IF NOT EXISTS location (
    id           SERIAL PRIMARY KEY,
    warehouse_id  INTEGER NOT NULL,
    location_code TEXT NOT NULL,
    location_name TEXT,
    creator      VARCHAR(64),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(64),
    updated_at    TIMESTAMP,
    CONSTRAINT "Location_warehouseId_locationCode_key" UNIQUE (warehouse_id, location_code),
    CONSTRAINT "Location_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);

CREATE TABLE IF NOT EXISTS serial (
    id           SERIAL PRIMARY KEY,
    item_id       INTEGER NOT NULL,
    serial_no     TEXT NOT NULL,
    warehouse_id  INTEGER,
    status       TEXT NOT NULL DEFAULT 'in_stock',
    inbound_time  TIMESTAMP,
    outbound_time TIMESTAMP,
    CONSTRAINT "Serial_itemId_serialNo_key" UNIQUE (item_id, serial_no),
    CONSTRAINT "Serial_itemId_fkey" FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT "Serial_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);

CREATE TABLE IF NOT EXISTS stock (
    id          SERIAL PRIMARY KEY,
    warehouse_id INTEGER NOT NULL,
    item_id      INTEGER NOT NULL,
    batch_id     INTEGER NOT NULL DEFAULT 0,
    location_id  INTEGER NOT NULL DEFAULT 0,
    quantity    NUMERIC(18, 4) NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP NOT NULL,
    CONSTRAINT "Stock_warehouseId_itemId_batchId_locationId_key"
        UNIQUE (warehouse_id, item_id, batch_id, location_id)
);

CREATE TABLE IF NOT EXISTS stock_transaction (
    id          SERIAL PRIMARY KEY,
    warehouse_id INTEGER NOT NULL,
    item_id      INTEGER NOT NULL,
    batch_id     INTEGER NOT NULL DEFAULT 0,
    location_id  INTEGER NOT NULL DEFAULT 0,
    change_qty   NUMERIC(18, 4) NOT NULL,
    after_qty    NUMERIC(18, 4) NOT NULL,
    biz_code     TEXT NOT NULL,
    doc_no       TEXT,
    operator    TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inbound_doc (
    id          SERIAL PRIMARY KEY,
    doc_no       TEXT NOT NULL,
    warehouse_id INTEGER NOT NULL,
    status      TEXT NOT NULL DEFAULT 'finished',
    remark      TEXT,
    creator     TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     TEXT,
    updated_at   TIMESTAMP,
    CONSTRAINT "InboundDoc_docNo_key" UNIQUE (doc_no),
    CONSTRAINT "InboundDoc_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);

CREATE TABLE IF NOT EXISTS outbound_doc (
    id          SERIAL PRIMARY KEY,
    doc_no       TEXT NOT NULL,
    warehouse_id INTEGER NOT NULL,
    status      TEXT NOT NULL DEFAULT 'finished',
    remark      TEXT,
    creator     TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     TEXT,
    updated_at   TIMESTAMP,
    CONSTRAINT "OutboundDoc_docNo_key" UNIQUE (doc_no),
    CONSTRAINT "OutboundDoc_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);

CREATE TABLE IF NOT EXISTS inbound_doc_item (
    id         SERIAL PRIMARY KEY,
    doc_id      INTEGER NOT NULL,
    item_id     INTEGER NOT NULL,
    quantity   NUMERIC(18, 4) NOT NULL,
    batch_id    INTEGER NOT NULL DEFAULT 0,
    location_id INTEGER NOT NULL DEFAULT 0,
    serial_nos  TEXT,
    CONSTRAINT "InboundDocItem_docId_fkey" FOREIGN KEY (doc_id) REFERENCES inbound_doc (id)
);

CREATE TABLE IF NOT EXISTS outbound_doc_item (
    id         SERIAL PRIMARY KEY,
    doc_id      INTEGER NOT NULL,
    item_id     INTEGER NOT NULL,
    quantity   NUMERIC(18, 4) NOT NULL,
    batch_id    INTEGER NOT NULL DEFAULT 0,
    location_id INTEGER NOT NULL DEFAULT 0,
    serial_nos  TEXT,
    CONSTRAINT "OutboundDocItem_docId_fkey" FOREIGN KEY (doc_id) REFERENCES outbound_doc (id)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id           SERIAL PRIMARY KEY,
    username     TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    name         TEXT NOT NULL,
    role         TEXT NOT NULL,
    status       INTEGER NOT NULL DEFAULT 1,
    creator      VARCHAR(64),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(64),
    updated_at    TIMESTAMP,
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
    creator        TEXT,
    created_at      TIMESTAMP,
    updater        TEXT,
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
    creator        TEXT,
    created_at      TIMESTAMP,
    updater        TEXT,
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
    line_remark         TEXT,
    creator            VARCHAR(64)
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
    line_remark          TEXT,
    creator             VARCHAR(64)
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
    line_remark     TEXT,
    creator        VARCHAR(64)
);

-- ---------- 3.5 盘点 / 调整 ----------
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
    diff_qty      NUMERIC(18, 4),
    creator      VARCHAR(64)
);
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
    reason       TEXT,
    creator      VARCHAR(64)
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

-- ---------- 3.7 字典表(V3,新环境随 schema 建表 + 种子) ----------
CREATE TABLE IF NOT EXISTS dict (
    id          SERIAL PRIMARY KEY,
    dict_type   TEXT NOT NULL,
    dict_key    TEXT NOT NULL,
    dict_label  TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    status     INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT "Dict_type_key_unique" UNIQUE (dict_type, dict_key)
);

INSERT INTO dict (dict_type, dict_key, dict_label, sort_order, status) VALUES
    ('warehouseType', 'raw',      '原材料仓', 1, 1),
    ('warehouseType', 'finished', '成品仓',   2, 1),
    ('warehouseType', 'hardware', '五金仓',   3, 1),
    ('itemCategory',  'hardware', '五金',     1, 1),
    ('itemCategory',  'finished', '成品',     2, 1),
    ('itemCategory',  'raw',      '原料',     3, 1),
    ('settleMethod',  'prepay',   '预付',     1, 1),
    ('settleMethod',  'cod',      '货到付款', 2, 1),
    ('settleMethod',  'credit',   '账期',     3, 1)
ON CONFLICT (dict_type, dict_key) DO NOTHING;
