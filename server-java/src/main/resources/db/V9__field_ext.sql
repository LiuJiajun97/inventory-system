-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V9: 通用字段补全(全部可空,不改现有行为)
BEGIN;
-- ① supplier
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS bank_name text;
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS bank_account text;
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS credit_limit numeric;
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS delivery_address text;
-- ② customer(与 supplier 对称)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS bank_name text;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS bank_account text;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS credit_limit numeric;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS delivery_address text;
-- ③ 采购单头
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS contract_no text;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS freight numeric;
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS shipping_address text;
-- ④ 销售单头
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS contract_no text;
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS freight numeric;
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS shipping_address text;
-- ⑤ 入库/出库单头(运输信息)
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS carrier text;
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS vehicle_no text;
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS freight numeric;
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS carrier text;
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS vehicle_no text;
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS freight numeric;
-- ⑥ 调拨行(车牌)
ALTER TABLE transfer_doc_item ADD COLUMN IF NOT EXISTS vehicle_no text;
-- ⑦ 物品(条码/双单位/品牌)
ALTER TABLE item ADD COLUMN IF NOT EXISTS barcode text;
ALTER TABLE item ADD COLUMN IF NOT EXISTS second_unit text;
ALTER TABLE item ADD COLUMN IF NOT EXISTS convert_factor numeric;
ALTER TABLE item ADD COLUMN IF NOT EXISTS brand text;
-- ⑧ 序列号台账(追溯链)
ALTER TABLE serial ADD COLUMN IF NOT EXISTS ref_doc_no text;
ALTER TABLE serial ADD COLUMN IF NOT EXISTS ref_out_doc_no text;
-- ⑨ 盘点行(盘点人/日期)
ALTER TABLE stocktake_doc_item ADD COLUMN IF NOT EXISTS checker_name text;
ALTER TABLE stocktake_doc_item ADD COLUMN IF NOT EXISTS check_date date;
-- 物品条码部分唯一索引(barcode 可空,多 NULL 合法)
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_barcode ON item (barcode) WHERE barcode IS NOT NULL;
COMMIT;
