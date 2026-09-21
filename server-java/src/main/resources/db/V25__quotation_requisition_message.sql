-- V25:销售报价单 + 请购单 + 站内消息(V25 链路留痕)
-- 背景:销售订单/采购订单前面补需求环节留痕——
--   1) 销售报价单(sales_quotation, BJ-):报价→转销售订单;无审批流程(draft/sent/converted/voided);
--   2) 请购单(purchase_requisition, QG-):需求→转采购订单;无审批流程(draft/submitted/converted/cancelled);
--   3) 站内消息(sys_message):审批/转换/预警通知,消息发送失败绝不影响主流程。
-- 口径:
--   - 报价单 6 列金额照抄销售订单(sales_order_item 同口径,服务端价税重算);
--   - 请购单无金额合计列(行价可空不汇总);
--   - 单据转换不建新接口:销售订单/采购订单 create DTO 加可选 ref 三列;
--     refDocType='quotation'/'requisition' 时服务端校验并落库后将源单置 converted。
--   - 销售/采购订单各加 ref_doc_type/ref_doc_no/ref_doc_id 三列(存量 NULL,IF NOT EXISTS 幂等);
--   - dict_type 加 dept 类型(部门字典),请购单"申请部门"字段存 dict_key(与 item.category 同口径);
--   - sys_message.type = approval/conversion/alert_daily;
--   - sys_message.read 列名与项目现有 boolean 口径一致(closed/instock 列名为 boolean;Java 字段禁 is 前缀)。
-- 幂等:CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS / ON CONFLICT DO NOTHING;重复执行结果一致。

BEGIN;

-- ============================================================
-- 销售报价单(sales_quotation):状态 draft/sent/converted/voided,无审批
-- ============================================================
CREATE TABLE IF NOT EXISTS sales_quotation (
    id                   serial NOT NULL                    ,
    doc_no               text NOT NULL                      ,
    doc_date             date NOT NULL                      ,
    customer_id          integer NOT NULL                   ,
    salesperson_id       integer                              ,
    warehouse_id         integer NOT NULL                   ,
    total_amount         numeric(18,4) DEFAULT 0 NOT NULL   ,
    total_tax_amount     numeric(18,4) DEFAULT 0 NOT NULL   ,
    total_tax_inclusive  numeric(18,4) DEFAULT 0 NOT NULL   ,
    quote_valid_until    date                                 ,
    status               text DEFAULT 'draft'::text NOT NULL,
    remark               text                                 ,
    creator              text                                 ,
    created_at           timestamp without time zone          ,
    updater              text                                 ,
    updated_at           timestamp without time zone
);

COMMENT ON TABLE sales_quotation IS '销售报价单(V25):报价→转销售订单,无审批流程';

COMMENT ON COLUMN sales_quotation.id IS '主键ID';
COMMENT ON COLUMN sales_quotation.doc_no IS '单据编号(BJ-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN sales_quotation.doc_date IS '单据日期';
COMMENT ON COLUMN sales_quotation.customer_id IS '客户 ID';
COMMENT ON COLUMN sales_quotation.salesperson_id IS '销售员用户 ID(可空,报价环节可不指定)';
COMMENT ON COLUMN sales_quotation.warehouse_id IS '发货仓库 ID(转销售订单时沿用)';
COMMENT ON COLUMN sales_quotation.total_amount IS '整单不含税合计(服务端重算)';
COMMENT ON COLUMN sales_quotation.total_tax_amount IS '整单税额合计(服务端重算)';
COMMENT ON COLUMN sales_quotation.total_tax_inclusive IS '整单价税合计(服务端重算)';
COMMENT ON COLUMN sales_quotation.quote_valid_until IS '报价有效期(可空,过期仅展示标记)';
COMMENT ON COLUMN sales_quotation.status IS '单据状态:draft/sent/converted/voided';
COMMENT ON COLUMN sales_quotation.remark IS '备注';
COMMENT ON COLUMN sales_quotation.creator IS '制单人';
COMMENT ON COLUMN sales_quotation.created_at IS '制单时间';
COMMENT ON COLUMN sales_quotation.updater IS '更新人';
COMMENT ON COLUMN sales_quotation.updated_at IS '更新时间';

-- 唯一约束 + 主键(幂等:若已存在则跳过)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_quotation_pkey') THEN
        ALTER TABLE sales_quotation ADD CONSTRAINT sales_quotation_pkey PRIMARY KEY (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_quotation_doc_no_key') THEN
        ALTER TABLE sales_quotation ADD CONSTRAINT sales_quotation_doc_no_key UNIQUE (doc_no);
    END IF;
END $$;

-- ============================================================
-- 销售报价单行(sales_quotation_item):六列金额照抄销售订单行
-- ============================================================
CREATE TABLE IF NOT EXISTS sales_quotation_item (
    id                   serial NOT NULL                    ,
    quotation_id         integer NOT NULL                   ,
    line_no              integer NOT NULL                   ,
    item_id              integer NOT NULL                   ,
    quantity             numeric(18,4) NOT NULL             ,
    unit_price           numeric(18,4)                      ,
    tax_price            numeric(18,4)                      ,
    tax_rate             numeric(5,2) DEFAULT 13.00 NOT NULL,
    amount               numeric(18,4)                      ,
    tax_amount           numeric(18,4)                      ,
    total_amount         numeric(18,4)                      ,
    remark               text                                 ,
    creator              text
);

COMMENT ON TABLE public.sales_quotation_item IS '销售报价单行(V25):六列金额照抄销售订单行,服务端价税重算';

COMMENT ON COLUMN public.sales_quotation_item.id IS '主键ID';
COMMENT ON COLUMN public.sales_quotation_item.quotation_id IS '报价单 ID';
COMMENT ON COLUMN public.sales_quotation_item.line_no IS '行号';
COMMENT ON COLUMN public.sales_quotation_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.sales_quotation_item.quantity IS '数量';
COMMENT ON COLUMN public.sales_quotation_item.unit_price IS '不含税单价(可空,与 taxPrice 二选一)';
COMMENT ON COLUMN public.sales_quotation_item.tax_price IS '含税单价(可空,与 unitPrice 二选一)';
COMMENT ON COLUMN public.sales_quotation_item.tax_rate IS '税率(百分数)';
COMMENT ON COLUMN public.sales_quotation_item.amount IS '不含税金额(服务端重算)';
COMMENT ON COLUMN public.sales_quotation_item.tax_amount IS '税额(服务端重算)';
COMMENT ON COLUMN public.sales_quotation_item.total_amount IS '价税合计(服务端重算)';
COMMENT ON COLUMN public.sales_quotation_item.remark IS '行备注';
COMMENT ON COLUMN public.sales_quotation_item.creator IS '创建人';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sales_quotation_item_pkey') THEN
        ALTER TABLE sales_quotation_item ADD CONSTRAINT sales_quotation_item_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- ============================================================
-- 请购单(purchase_requisition):状态 draft/submitted/converted/cancelled,无审批
-- ============================================================
CREATE TABLE IF NOT EXISTS purchase_requisition (
    id                   serial NOT NULL                    ,
    doc_no               text NOT NULL                      ,
    doc_date             date NOT NULL                      ,
    warehouse_id         integer NOT NULL                   ,
    applicant_id         integer NOT NULL                   ,
    department           text                                 ,
    status               text DEFAULT 'draft'::text NOT NULL,
    remark               text                                 ,
    creator              text                                 ,
    created_at           timestamp without time zone          ,
    updater              text                                 ,
    updated_at           timestamp without time zone
);

COMMENT ON TABLE purchase_requisition IS '请购单(V25):需求→转采购订单,无审批流程';

COMMENT ON COLUMN purchase_requisition.id IS '主键ID';
COMMENT ON COLUMN purchase_requisition.doc_no IS '单据编号(QG-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN purchase_requisition.doc_date IS '单据日期';
COMMENT ON COLUMN purchase_requisition.warehouse_id IS '收货仓库 ID(转采购订单时沿用)';
COMMENT ON COLUMN purchase_requisition.applicant_id IS '申请人用户 ID';
COMMENT ON COLUMN purchase_requisition.department IS '申请部门(字典 dept 类型的 dict_key,可空)';
COMMENT ON COLUMN purchase_requisition.status IS '单据状态:draft/submitted/converted/cancelled';
COMMENT ON COLUMN purchase_requisition.remark IS '备注';
COMMENT ON COLUMN purchase_requisition.creator IS '制单人';
COMMENT ON COLUMN purchase_requisition.created_at IS '制单时间';
COMMENT ON COLUMN purchase_requisition.updater IS '更新人';
COMMENT ON COLUMN purchase_requisition.updated_at IS '更新时间';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_requisition_pkey') THEN
        ALTER TABLE purchase_requisition ADD CONSTRAINT purchase_requisition_pkey PRIMARY KEY (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_requisition_doc_no_key') THEN
        ALTER TABLE purchase_requisition ADD CONSTRAINT purchase_requisition_doc_no_key UNIQUE (doc_no);
    END IF;
END $$;

-- ============================================================
-- 请购单行(purchase_requisition_item):无金额合计(行价可空不汇总)
-- ============================================================
CREATE TABLE IF NOT EXISTS purchase_requisition_item (
    id                   serial NOT NULL                    ,
    requisition_id       integer NOT NULL                   ,
    line_no              integer NOT NULL                   ,
    item_id              integer NOT NULL                   ,
    quantity             numeric(18,4) NOT NULL             ,
    expected_date        date                                 ,
    unit_price           numeric(18,4)                       ,
    remark               text                                 ,
    creator              text
);

COMMENT ON TABLE public.purchase_requisition_item IS '请购单行(V25):行价可空不汇总,转采购订单时按需携带';

COMMENT ON COLUMN public.purchase_requisition_item.id IS '主键ID';
COMMENT ON COLUMN public.purchase_requisition_item.requisition_id IS '请购单 ID';
COMMENT ON COLUMN public.purchase_requisition_item.line_no IS '行号';
COMMENT ON COLUMN public.purchase_requisition_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.purchase_requisition_item.quantity IS '数量';
COMMENT ON COLUMN public.purchase_requisition_item.expected_date IS '期望到货日(可空)';
COMMENT ON COLUMN public.purchase_requisition_item.unit_price IS '参考单价(可空,仅参考用)';
COMMENT ON COLUMN public.purchase_requisition_item.remark IS '行备注';
COMMENT ON COLUMN public.purchase_requisition_item.creator IS '创建人';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'purchase_requisition_item_pkey') THEN
        ALTER TABLE purchase_requisition_item ADD CONSTRAINT purchase_requisition_item_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- ============================================================
-- 站内消息(sys_message):审批/转换/预警通知
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_message (
    id                   serial NOT NULL                    ,
    receiver_id          integer NOT NULL                   ,
    type                 text NOT NULL                      ,
    title                text NOT NULL                      ,
    content              text NOT NULL                      ,
    ref_doc_type         text                                 ,
    ref_doc_id           integer                               ,
    is_read              boolean DEFAULT false NOT NULL     ,
    created_at           timestamp without time zone NOT NULL
);

COMMENT ON TABLE sys_message IS '站内消息(V25):审批/转换/预警通知,read 列名与项目 boolean 口径一致';

COMMENT ON COLUMN sys_message.id IS '主键ID';
COMMENT ON COLUMN sys_message.receiver_id IS '接收人用户 ID';
COMMENT ON COLUMN sys_message.type IS '消息类型:approval/conversion/alert_daily';
COMMENT ON COLUMN sys_message.title IS '消息标题';
COMMENT ON COLUMN sys_message.content IS '消息内容';
COMMENT ON COLUMN sys_message.ref_doc_type IS '关联单据类型(可空,如 quotation/requisition/sales_order/purchase_order)';
COMMENT ON COLUMN sys_message.ref_doc_id IS '关联单据 ID(可空)';
COMMENT ON COLUMN sys_message.is_read IS '是否已读';
COMMENT ON COLUMN sys_message.created_at IS '创建时间';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'sys_message_pkey') THEN
        ALTER TABLE sys_message ADD CONSTRAINT sys_message_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- 索引:按"接收人 + 是否已读"过滤(铃铛未读列表)
CREATE INDEX IF NOT EXISTS idx_sys_message_receiver_read ON sys_message (receiver_id, is_read);

-- ============================================================
-- 销售/采购订单加 ref 三列(关联源单:报价单/请购单)
-- ============================================================
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS ref_doc_type text;
COMMENT ON COLUMN sales_order.ref_doc_type IS '关联源单类型:quotation(销售报价单,本系统内唯一来源)';

ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS ref_doc_no text;
COMMENT ON COLUMN sales_order.ref_doc_no IS '关联源单单号';

ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS ref_doc_id integer;
COMMENT ON COLUMN sales_order.ref_doc_id IS '关联源单 ID';

ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS ref_doc_type text;
COMMENT ON COLUMN purchase_order.ref_doc_type IS '关联源单类型:requisition(请购单,本系统内唯一来源)';

ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS ref_doc_no text;
COMMENT ON COLUMN purchase_order.ref_doc_no IS '关联源单单号';

ALTER TABLE purchase_order ADD COLUMN IF NOT EXISTS ref_doc_id integer;
COMMENT ON COLUMN purchase_order.ref_doc_id IS '关联源单 ID';

-- ============================================================
-- 字典 dept 类型(申请部门)
-- ============================================================
INSERT INTO dict_type (id, type_code, type_name, status)
VALUES (4, 'dept', '部门', 1)
ON CONFLICT (type_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('dict_type', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM dict_type), 1));

INSERT INTO dict (id, dict_type, dict_key, dict_label, sort_order, status)
VALUES
    (10, 'dept', 'production', '生产部', 1, 1),
    (11, 'dept', 'purchase', '采购部', 2, 1),
    (12, 'dept', 'sales', '销售部', 3, 1),
    (13, 'dept', 'storage', '仓储部', 4, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('dict', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM dict), 1));

-- ============================================================
-- 菜单与按钮(销售报价单 + 请购单)
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, type, path, sort, status)
VALUES
    (143, 3, 'sales-quotations', '销售报价单', 'menu', '/sales-quotations', 0, 1),
    (144, 143, 'quotation:create', '销售报价单-新建', 'button', NULL, 1, 1),
    (145, 143, 'quotation:edit', '销售报价单-编辑', 'button', NULL, 2, 1),
    (146, 143, 'quotation:send', '销售报价单-发送', 'button', NULL, 3, 1),
    (147, 143, 'quotation:convert', '销售报价单-转销售订单', 'button', NULL, 4, 1),
    (148, 143, 'quotation:void', '销售报价单-作废', 'button', NULL, 5, 1),
    (149, 2, 'purchase-requisitions', '请购单', 'menu', '/purchase-requisitions', 0, 1),
    (150, 149, 'requisition:create', '请购单-新建', 'button', NULL, 1, 1),
    (151, 149, 'requisition:edit', '请购单-编辑', 'button', NULL, 2, 1),
    (152, 149, 'requisition:submit', '请购单-提交', 'button', NULL, 3, 1),
    (153, 149, 'requisition:convert', '请购单-转采购订单', 'button', NULL, 4, 1),
    (154, 149, 'requisition:cancel', '请购单-取消', 'button', NULL, 5, 1)
ON CONFLICT (menu_code) DO NOTHING;

SELECT setval(pg_get_serial_sequence('sys_menu', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM sys_menu), 1));

-- 角色绑定:admin(1) + operator(2) 与同设备写操作同口径
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 143), (1, 144), (1, 145), (1, 146), (1, 147), (1, 148),
    (1, 149), (1, 150), (1, 151), (1, 152), (1, 153), (1, 154),
    (2, 143), (2, 144), (2, 145), (2, 146), (2, 147), (2, 148),
    (2, 149), (2, 150), (2, 151), (2, 152), (2, 153), (2, 154)
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;