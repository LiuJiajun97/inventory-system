-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V18: 结算域(三单匹配 + 应收应付)
-- 1) 新表 invoice / invoice_item(发票头/行,采购票+销售票,正票+负票/红字凭单)
--    发票状态机:draft(平账草稿) → confirmed(确认);差异行 |variance|>0.01 落 mismatch(挂起),
--    改平后 draft,作废 voided:头表留痕,物理删行释放源行占用(可再开票)。只有 confirmed 发票进应收应付台账。
--    防超开:同一源单据行的累计已开票额(未作废)+ 本次 ≤ 该行含税额,服务端硬校验。
--    退货过账同事务自动生成负数草稿发票(source_type=return_gen,sign=negative),确认后核减应付费/应收。
-- 2) 新表 payment_doc / payment_line(付款单/收款单,create 即生效 confirmed;核销行挂正票,
--    支持一票多笔部分核销;防超核:累计已核销 + 本次 ≤ 票额)
-- 3) 台账零建表:应付/应收台账 = 发票 + 核销行 + 入库/出库实时聚合(只读 SQL,见 InvoiceMapper.xml)
-- 4) 菜单 seed:结算分组 settlement-dir + 5 菜单(发票登记/应付台账/应收台账/付款单/收款单)+ 按钮
--    绑定:admin/operator 菜单+按钮(可读写),viewer 仅菜单(只读)
-- 幂等:CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
--       菜单与绑定 ON CONFLICT DO NOTHING,父菜单不存在时 SELECT 无结果安全跳过,可重复执行。
-- 执行:docker exec -i inventory-postgres psql -U inv -d inventory < server-java/src/main/resources/db/V18__settlement.sql
--       (inventory_test 测试库同样执行一遍)

BEGIN;

-- ---------- 1) invoice:发票头 ----------
CREATE TABLE IF NOT EXISTS invoice (
    id            BIGSERIAL PRIMARY KEY,
    doc_no        TEXT NOT NULL,
    invoice_type  VARCHAR(16) NOT NULL,
    party_id      INTEGER NOT NULL,
    invoice_date  DATE NOT NULL,
    total_amount  NUMERIC(14, 2) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    sign          VARCHAR(8) NOT NULL DEFAULT 'positive',
    source_type   VARCHAR(16) NOT NULL DEFAULT 'manual',
    ref_return_id INTEGER,
    remark        TEXT,
    creator       TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       TEXT,
    updated_at    TIMESTAMP,
    CONSTRAINT uk_invoice_doc_no UNIQUE (doc_no)
);
COMMENT ON TABLE invoice IS '发票头:采购票(invoice_type=purchase,对方=供应商)/销售票(sales,对方=客户);负票 sign=negative 为退货生成的红字凭单,确认后核减应付/应收;仅 confirmed 进台账';
COMMENT ON COLUMN invoice.invoice_type IS '发票类型:purchase 采购票 | sales 销售票';
COMMENT ON COLUMN invoice.party_id IS '对方 ID:采购票=供应商 ID,销售票=客户 ID';
COMMENT ON COLUMN invoice.status IS '发票状态:draft 草稿(平账)| mismatch 差异(挂起,存在 |差异|>0.01 的行)| confirmed 已确认(进台账)| voided 已作废(头表留痕,行释放源行占用)';
COMMENT ON COLUMN invoice.sign IS '正负号:positive 正票 | negative 负票(红字凭单,退货生成)';
COMMENT ON COLUMN invoice.source_type IS '来源:manual 手工登记 | return_gen 退货过账自动生成';
COMMENT ON COLUMN invoice.ref_return_id IS 'return_gen 时指退货单 ID(采购退货单/销售退货单)';

CREATE INDEX IF NOT EXISTS idx_invoice_party  ON invoice (party_id, invoice_type);
CREATE INDEX IF NOT EXISTS idx_invoice_status ON invoice (status);
CREATE INDEX IF NOT EXISTS idx_invoice_date   ON invoice (invoice_date);

-- ---------- 2) invoice_item:发票行(挂源单据行) ----------
CREATE TABLE IF NOT EXISTS invoice_item (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT NOT NULL,
    line_no         INTEGER,
    src_doc_type    VARCHAR(16) NOT NULL,
    src_doc_id      INTEGER NOT NULL,
    src_doc_item_id INTEGER NOT NULL,
    item_id         INTEGER NOT NULL,
    spec_snapshot   TEXT,
    unit            TEXT,
    quantity        NUMERIC(18, 4),
    invoiced_amount NUMERIC(14, 2) NOT NULL,
    src_amount      NUMERIC(14, 2),
    variance        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sign            VARCHAR(8) NOT NULL DEFAULT 'positive',
    batch_no        TEXT,
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT uk_invoice_item_src UNIQUE (src_doc_type, src_doc_id, src_doc_item_id, sign)
);
COMMENT ON TABLE invoice_item IS '发票行:行级匹配挂源单据行;src=采购关联入库行/销售关联出库行(正票)或退货单行(负票);variance=开票额-源行含税额';
COMMENT ON COLUMN invoice_item.src_doc_type IS '源单据类型:inbound 入库行 | outbound 出库行 | purchase_return 采购退货行 | sales_return 销售退货行';
COMMENT ON COLUMN invoice_item.invoiced_amount IS '开票额(负票为负数)';
COMMENT ON COLUMN invoice_item.src_amount IS '源单据行含税额快照(负票为负数)';
COMMENT ON COLUMN invoice_item.variance IS '差异额=开票额-源行含税额(负票两者均负);|差异|>0.01 时发票挂 mismatch';
COMMENT ON COLUMN invoice_item.sign IS '冗余头表正负号,参与防同一行同向重复挂票的唯一约束';

CREATE INDEX IF NOT EXISTS idx_invoice_item_invoice ON invoice_item (invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_item_src     ON invoice_item (src_doc_type, src_doc_id, src_doc_item_id);

-- ---------- 3) payment_doc:付款单/收款单头 ----------
CREATE TABLE IF NOT EXISTS payment_doc (
    id           BIGSERIAL PRIMARY KEY,
    doc_no       TEXT NOT NULL,
    pay_type     VARCHAR(16) NOT NULL,
    party_id     INTEGER NOT NULL,
    pay_date     DATE,
    total_amount NUMERIC(14, 2) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'confirmed',
    remark       TEXT,
    creator      TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      TEXT,
    updated_at   TIMESTAMP,
    CONSTRAINT uk_payment_doc_no UNIQUE (doc_no)
);
COMMENT ON TABLE payment_doc IS '付款单(pay_type=payment,对方=供应商,前缀 FK-)/收款单(receipt,对方=客户,前缀 SK-);create 即生效,仅 confirmed/voided 两态';
COMMENT ON COLUMN payment_doc.pay_type IS '单据类型:payment 付款 | receipt 收款';
COMMENT ON COLUMN payment_doc.party_id IS '对方 ID:付款=供应商 ID,收款=客户 ID';
COMMENT ON COLUMN payment_doc.status IS '状态:confirmed 已确认 | voided 已作废';

CREATE INDEX IF NOT EXISTS idx_payment_party  ON payment_doc (pay_type, party_id);
CREATE INDEX IF NOT EXISTS idx_payment_status ON payment_doc (status);

-- ---------- 4) payment_line:核销行(挂正票,部分核销) ----------
CREATE TABLE IF NOT EXISTS payment_line (
    id         BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    amount     NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_payment_line_payment FOREIGN KEY (payment_id) REFERENCES payment_doc (id),
    CONSTRAINT fk_payment_line_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT uk_payment_line UNIQUE (invoice_id, payment_id)
);
COMMENT ON TABLE payment_line IS '核销行:一笔付款可核多张票,一票可被多笔付款分次部分核销;只允许挂 confirmed 正票,防超核由服务端硬校验(累计已核销+本次≤票额)';
COMMENT ON COLUMN payment_line.amount IS '核销额(正数)';

CREATE INDEX IF NOT EXISTS idx_payment_line_invoice  ON payment_line (invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_line_payment  ON payment_line (payment_id);

-- ---------- 5) 菜单:结算分组 + 5 菜单 + 按钮(父节点不存在时安全跳过,兼容菜单自管的测试库) ----------
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
VALUES (0, 'settlement-dir', '结算', 'directory', NULL, 8)
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'invoices', '发票登记', 'menu', '/invoices', 1
FROM sys_menu p WHERE p.menu_code = 'settlement-dir'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'settlement-ap', '应付台账', 'menu', '/settlement/ap', 2
FROM sys_menu p WHERE p.menu_code = 'settlement-dir'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'settlement-ar', '应收台账', 'menu', '/settlement/ar', 3
FROM sys_menu p WHERE p.menu_code = 'settlement-dir'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'payments', '付款单', 'menu', '/payments', 4
FROM sys_menu p WHERE p.menu_code = 'settlement-dir'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'receipts', '收款单', 'menu', '/receipts', 5
FROM sys_menu p WHERE p.menu_code = 'settlement-dir'
ON CONFLICT (menu_code) DO NOTHING;

-- 按钮权限(与菜单同组)
INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'invoices:create', '发票-新建', 'button', NULL, 1
FROM sys_menu p WHERE p.menu_code = 'invoices'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'invoices:edit', '发票-编辑', 'button', NULL, 2
FROM sys_menu p WHERE p.menu_code = 'invoices'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'invoices:confirm', '发票-确认', 'button', NULL, 3
FROM sys_menu p WHERE p.menu_code = 'invoices'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'invoices:void', '发票-作废', 'button', NULL, 4
FROM sys_menu p WHERE p.menu_code = 'invoices'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'payments:create', '付款单-新建', 'button', NULL, 1
FROM sys_menu p WHERE p.menu_code = 'payments'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'payments:void', '付款单-作废', 'button', NULL, 2
FROM sys_menu p WHERE p.menu_code = 'payments'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'receipts:create', '收款单-新建', 'button', NULL, 1
FROM sys_menu p WHERE p.menu_code = 'receipts'
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu (parent_id, menu_code, menu_name, type, path, sort)
SELECT p.id, 'receipts:void', '收款单-作废', 'button', NULL, 2
FROM sys_menu p WHERE p.menu_code = 'receipts'
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'), GREATEST(1, (SELECT MAX(id) FROM sys_menu)));

-- ---------- 6) 角色绑定:admin/operator 菜单+按钮,viewer 仅菜单(只读) ----------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('settlement-dir', 'invoices', 'settlement-ap', 'settlement-ar',
        'payments', 'receipts', 'invoices:create', 'invoices:edit', 'invoices:confirm', 'invoices:void',
        'payments:create', 'payments:void', 'receipts:create', 'receipts:void')
WHERE r.role_code = 'admin'
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('settlement-dir', 'invoices', 'settlement-ap', 'settlement-ar',
        'payments', 'receipts', 'invoices:create', 'invoices:edit', 'invoices:confirm', 'invoices:void',
        'payments:create', 'payments:void', 'receipts:create', 'receipts:void')
WHERE r.role_code = 'operator'
ON CONFLICT (role_id, menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.menu_code IN ('settlement-dir', 'invoices', 'settlement-ap', 'settlement-ar',
        'payments', 'receipts')
WHERE r.role_code = 'viewer'
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
