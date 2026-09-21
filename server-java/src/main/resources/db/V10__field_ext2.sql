-- [已归档 2026-09-17] 历史增量迁移记录,仅作变更记录保留;新环境初始化只需执行 schema.sql + seed.sql(终态),勿再执行本文件。
-- V10: 主流系统字段对标补齐(全部可空,不改现有行为)
BEGIN;
-- ① supplier(邮箱)
ALTER TABLE supplier ADD COLUMN IF NOT EXISTS email varchar(100);
COMMENT ON COLUMN supplier.email IS '邮箱(可空)';
-- ② customer(邮箱)
ALTER TABLE customer ADD COLUMN IF NOT EXISTS email varchar(100);
COMMENT ON COLUMN customer.email IS '邮箱(可空)';
-- ③ item(参考采购价/参考销售价/产地,建单预填用)
ALTER TABLE item ADD COLUMN IF NOT EXISTS reference_purchase_price numeric(14,4);
COMMENT ON COLUMN item.reference_purchase_price IS '参考采购价(建单预填,可空)';
ALTER TABLE item ADD COLUMN IF NOT EXISTS reference_sale_price numeric(14,4);
COMMENT ON COLUMN item.reference_sale_price IS '参考销售价(建单预填,可空)';
ALTER TABLE item ADD COLUMN IF NOT EXISTS origin varchar(100);
COMMENT ON COLUMN item.origin IS '产地(可空)';
-- ④ purchase_order(折扣额/币种/汇率)
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS discount_amount numeric(14,2);
COMMENT ON COLUMN purchase_order.discount_amount IS '折扣额(可空,不参与合计计算)';
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS currency_code varchar(3);
COMMENT ON COLUMN purchase_order.currency_code IS '币种(可空,如 CNY)';
ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS exchange_rate numeric(14,6);
COMMENT ON COLUMN purchase_order.exchange_rate IS '汇率(可空)';
-- ⑤ sales_order(折扣额/币种/汇率)
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS discount_amount numeric(14,2);
COMMENT ON COLUMN sales_order.discount_amount IS '折扣额(可空,不参与合计计算)';
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS currency_code varchar(3);
COMMENT ON COLUMN sales_order.currency_code IS '币种(可空,如 CNY)';
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS exchange_rate numeric(14,6);
COMMENT ON COLUMN sales_order.exchange_rate IS '汇率(可空)';
-- ⑥ inbound_doc(总金额/单据类型/经办人)
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS total_amount numeric(14,2);
COMMENT ON COLUMN inbound_doc.total_amount IS '单据总金额(行金额合计,服务端落,可空)';
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS doc_type varchar(30);
COMMENT ON COLUMN inbound_doc.doc_type IS '单据类型(自由文本,如 采购入库/退货入库,可空)';
ALTER TABLE inbound_doc ADD COLUMN IF NOT EXISTS handler varchar(50);
COMMENT ON COLUMN inbound_doc.handler IS '经办人(可空)';
-- ⑦ outbound_doc(总金额/单据类型/经办人)
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS total_amount numeric(14,2);
COMMENT ON COLUMN outbound_doc.total_amount IS '单据总金额(行金额合计,服务端落,可空)';
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS doc_type varchar(30);
COMMENT ON COLUMN outbound_doc.doc_type IS '单据类型(自由文本,如 销售出库/领用出库,可空)';
ALTER TABLE outbound_doc ADD COLUMN IF NOT EXISTS handler varchar(50);
COMMENT ON COLUMN outbound_doc.handler IS '经办人(可空)';
-- ⑧ inbound_doc_item(行号/行金额快照)
ALTER TABLE inbound_doc_item ADD COLUMN IF NOT EXISTS line_no int;
COMMENT ON COLUMN inbound_doc_item.line_no IS '行号(从 1 连号,可空)';
ALTER TABLE inbound_doc_item ADD COLUMN IF NOT EXISTS amount numeric(14,2);
COMMENT ON COLUMN inbound_doc_item.amount IS '行金额快照(数量×不含税单价,可空)';
ALTER TABLE inbound_doc_item ADD COLUMN IF NOT EXISTS tax_amount numeric(14,2);
COMMENT ON COLUMN inbound_doc_item.tax_amount IS '行税额快照(金额×税率/100,可空)';
ALTER TABLE inbound_doc_item ADD COLUMN IF NOT EXISTS tax_inclusive_total numeric(14,2);
COMMENT ON COLUMN inbound_doc_item.tax_inclusive_total IS '含税行金额快照(金额+税额,可空)';
-- ⑨ outbound_doc_item(行号/税率/行金额快照)
ALTER TABLE outbound_doc_item ADD COLUMN IF NOT EXISTS line_no int;
COMMENT ON COLUMN outbound_doc_item.line_no IS '行号(从 1 连号,可空)';
ALTER TABLE outbound_doc_item ADD COLUMN IF NOT EXISTS tax_rate numeric(6,4);
COMMENT ON COLUMN outbound_doc_item.tax_rate IS '税率(百分数,可空,空按 0 算)';
ALTER TABLE outbound_doc_item ADD COLUMN IF NOT EXISTS amount numeric(14,2);
COMMENT ON COLUMN outbound_doc_item.amount IS '行金额快照(数量×不含税单价,可空)';
ALTER TABLE outbound_doc_item ADD COLUMN IF NOT EXISTS tax_amount numeric(14,2);
COMMENT ON COLUMN outbound_doc_item.tax_amount IS '行税额快照(金额×税率/100,可空)';
ALTER TABLE outbound_doc_item ADD COLUMN IF NOT EXISTS tax_inclusive_total numeric(14,2);
COMMENT ON COLUMN outbound_doc_item.tax_inclusive_total IS '含税行金额快照(金额+税额,可空)';
-- ⑩ transfer_doc(承运商)
ALTER TABLE transfer_doc ADD COLUMN IF NOT EXISTS carrier varchar(50);
COMMENT ON COLUMN transfer_doc.carrier IS '承运商(可空)';
-- ⑪ warehouse(默认仓,全表至多一个 true)
ALTER TABLE warehouse ADD COLUMN IF NOT EXISTS default_warehouse boolean;
COMMENT ON COLUMN warehouse.default_warehouse IS '默认仓(全表至多一个 true,可空)';
COMMIT;
