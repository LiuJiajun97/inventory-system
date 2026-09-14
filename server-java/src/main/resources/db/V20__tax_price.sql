-- V20:价税六列全铺——6 张带价行表补含税单价列 tax_price
-- 口径(正规/金蝶):不含税单价 unit_price 与含税单价 tax_price 二选一录入;
--   金额 = 数量×不含税单价,税额 = 金额×税率,含税金额 = 金额+税额;
--   含税输入时反算:含税金额 = 数量×tax_price,金额 = 含税金额/(1+税率),税额 = 含税金额-金额。
-- 含税单价落库统一 tax_price,精度与空值性对齐各表 unit_price:
--   订单/退货 unit_price NOT NULL → tax_price NOT NULL DEFAULT 0;
--   出入库 unit_price 可空 → tax_price 可空。
-- 存量回填:tax_price = round(unit_price × (1 + COALESCE(tax_rate,0)/100), 4);
--   仅更新新列,其余列一律不动(存量数据为用户保留数据)。
-- 幂等:ADD COLUMN IF NOT EXISTS + 确定性公式回填,重复执行结果一致。

BEGIN;

ALTER TABLE purchase_order_item ADD COLUMN IF NOT EXISTS tax_price NUMERIC(18, 4) NOT NULL DEFAULT 0;
COMMENT ON COLUMN purchase_order_item.tax_price IS '含税单价(与 unit_price 二选一录入,服务端按价税口径重算)';

ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS tax_price NUMERIC(18, 4) NOT NULL DEFAULT 0;
COMMENT ON COLUMN sales_order_item.tax_price IS '含税单价(与 unit_price 二选一录入,服务端按价税口径重算)';

ALTER TABLE purchase_return_item ADD COLUMN IF NOT EXISTS tax_price NUMERIC(14, 4) NOT NULL DEFAULT 0;
COMMENT ON COLUMN purchase_return_item.tax_price IS '含税单价(继承原采购订单行快照)';

ALTER TABLE sales_return_item ADD COLUMN IF NOT EXISTS tax_price NUMERIC(14, 4) NOT NULL DEFAULT 0;
COMMENT ON COLUMN sales_return_item.tax_price IS '含税单价(继承原销售订单行快照)';

ALTER TABLE inbound_doc_item ADD COLUMN IF NOT EXISTS tax_price NUMERIC(18, 4);
COMMENT ON COLUMN inbound_doc_item.tax_price IS '含税单价快照(有源继承源单,无源按 unit_price×(1+税率)算)';

ALTER TABLE outbound_doc_item ADD COLUMN IF NOT EXISTS tax_price NUMERIC(18, 4);
COMMENT ON COLUMN outbound_doc_item.tax_price IS '含税单价快照(有源继承源单,无源按 unit_price×(1+税率)算)';

-- ====== 存量回填(确定性公式,幂等) ======
UPDATE purchase_order_item SET tax_price = ROUND(unit_price * (1 + COALESCE(tax_rate, 0) / 100.0), 4);
UPDATE sales_order_item SET tax_price = ROUND(unit_price * (1 + COALESCE(tax_rate, 0) / 100.0), 4);
UPDATE purchase_return_item SET tax_price = ROUND(unit_price * (1 + COALESCE(tax_rate, 0) / 100.0), 4);
UPDATE sales_return_item SET tax_price = ROUND(unit_price * (1 + COALESCE(tax_rate, 0) / 100.0), 4);
UPDATE inbound_doc_item SET tax_price = ROUND(unit_price * (1 + COALESCE(tax_rate, 0) / 100.0), 4) WHERE unit_price IS NOT NULL;
UPDATE outbound_doc_item SET tax_price = ROUND(unit_price * (1 + COALESCE(tax_rate, 0) / 100.0), 4) WHERE unit_price IS NOT NULL;

COMMIT;
