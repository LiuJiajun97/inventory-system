-- ============================================================
-- 库存管理系统 PostgreSQL 终态 DDL(42 张表 + 索引 + 约束 + 注释)
-- 按业务域分组,逐表整理版(2026-09-17 重构自 pg_dump 提取物)。
-- 用法(新环境初始化):
--   psql -U inv -d <新库> -f schema.sql
-- 已初始化的库不要重跑(非幂等);历史增量迁移文件 V2__*.sql ~ V20__*.sql
-- 仅作变更记录保留,不再用于建库。
-- 约定:表/列统一小写蛇形;金额 NUMERIC;时间 TIMESTAMP(东八区应用层控制)。
-- ============================================================


-- ######################################################################
-- 业务域:基础档案
-- ######################################################################

-- ============================================================
-- 表:warehouse 仓库
-- 说明:仓库表实体(表 Warehouse),4 个 enable 开关注驱动出入库必填校验。
-- ============================================================
CREATE TABLE public.warehouse (
    id                serial NOT NULL                                               ,  -- 主键ID
    warehouse_code    text NOT NULL                                                 ,  -- 仓库编码(唯一)
    warehouse_name    text NOT NULL                                                 ,  -- 仓库名称
    warehouse_type    text NOT NULL                                                 ,  -- 仓库类型:raw / finished / hardware 等
    enable_batch      boolean DEFAULT false NOT NULL                                ,  -- 启用批次管理
    enable_expiry     boolean DEFAULT false NOT NULL                                ,  -- 启用保质期(要求同时启用批次)
    enable_serial     boolean DEFAULT false NOT NULL                                ,  -- 启用序列号
    enable_location   boolean DEFAULT false NOT NULL                                ,  -- 启用库位
    status            integer DEFAULT 1 NOT NULL                                    ,  -- 状态:1 启用
    creator           character varying(64)                                         ,  -- 创建人
    created_at        timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,  -- 创建时间
    updater           character varying(64)                                         ,  -- 更新人
    updated_at        timestamp without time zone                                   ,  -- 更新时间
    default_warehouse boolean                                                         -- 默认仓(全表至多一个 true,可空)
);

COMMENT ON TABLE public.warehouse IS '仓库表实体(表 Warehouse),4 个 enable 开关注驱动出入库必填校验。';

COMMENT ON COLUMN public.warehouse.id IS '主键ID';
COMMENT ON COLUMN public.warehouse.warehouse_code IS '仓库编码(唯一)';
COMMENT ON COLUMN public.warehouse.warehouse_name IS '仓库名称';
COMMENT ON COLUMN public.warehouse.warehouse_type IS '仓库类型:raw / finished / hardware 等';
COMMENT ON COLUMN public.warehouse.enable_batch IS '启用批次管理';
COMMENT ON COLUMN public.warehouse.enable_expiry IS '启用保质期(要求同时启用批次)';
COMMENT ON COLUMN public.warehouse.enable_serial IS '启用序列号';
COMMENT ON COLUMN public.warehouse.enable_location IS '启用库位';
COMMENT ON COLUMN public.warehouse.status IS '状态:1 启用';
COMMENT ON COLUMN public.warehouse.creator IS '创建人';
COMMENT ON COLUMN public.warehouse.created_at IS '创建时间';
COMMENT ON COLUMN public.warehouse.updater IS '更新人';
COMMENT ON COLUMN public.warehouse.updated_at IS '更新时间';
COMMENT ON COLUMN public.warehouse.default_warehouse IS '默认仓(全表至多一个 true,可空)';

ALTER TABLE ONLY public.warehouse ADD CONSTRAINT warehouse_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.warehouse ADD CONSTRAINT "Warehouse_warehouseCode_key" UNIQUE (warehouse_code);


-- ============================================================
-- 表:location 库位
-- 说明:库位表实体(表 Location)。
-- ============================================================
CREATE TABLE public.location (
    id            serial NOT NULL                                      ,  -- 主键ID
    warehouse_id  integer NOT NULL                                     ,  -- 所属仓库 ID
    location_code text NOT NULL                                        ,  -- 库位编码(仓库内唯一)
    location_name text                                                 ,  -- 库位名称
    creator       character varying(64)                                ,  -- 创建人
    created_at    timestamp without time zone DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
    updater       character varying(64)                                ,  -- 更新人
    updated_at    timestamp without time zone                            -- 更新时间
);

COMMENT ON TABLE public.location IS '库位表实体(表 Location)。';

COMMENT ON COLUMN public.location.id IS '主键ID';
COMMENT ON COLUMN public.location.warehouse_id IS '所属仓库 ID';
COMMENT ON COLUMN public.location.location_code IS '库位编码(仓库内唯一)';
COMMENT ON COLUMN public.location.location_name IS '库位名称';
COMMENT ON COLUMN public.location.creator IS '创建人';
COMMENT ON COLUMN public.location.created_at IS '创建时间';
COMMENT ON COLUMN public.location.updater IS '更新人';
COMMENT ON COLUMN public.location.updated_at IS '更新时间';

ALTER TABLE ONLY public.location ADD CONSTRAINT location_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.location ADD CONSTRAINT "Location_warehouseId_locationCode_key" UNIQUE (warehouse_id, location_code);

ALTER TABLE ONLY public.location ADD CONSTRAINT "Location_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);


-- ============================================================
-- 表:item 物品
-- 说明:物品表实体(表 Item)。
-- ============================================================
CREATE TABLE public.item (
    id                       serial NOT NULL                                               ,  -- 主键ID
    item_code                text NOT NULL                                                 ,  -- 物品编码(唯一)
    item_name                text NOT NULL                                                 ,  -- 物品名称
    unit                     text NOT NULL                                                 ,  -- 单位
    spec                     text                                                          ,  -- 规格
    attributes               text                                                          ,  -- 扩展属性(JSON 字符串)
    status                   integer DEFAULT 1 NOT NULL                                    ,  -- 状态:1 启用
    creator                  character varying(64)                                         ,  -- 创建人
    created_at               timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,  -- 创建时间
    updater                  character varying(64)                                         ,  -- 更新人
    updated_at               timestamp without time zone                                   ,  -- 更新时间
    category                 text                                                          ,  -- 物料分类(轻量单级,可空)
    min_stock                numeric(18,4)                                                 ,  -- 最低库存预警线(非空才参与低库存预警)
    default_tax_rate         numeric(5,2) DEFAULT 13.00 NOT NULL                           ,  -- 默认税率(百分数,默认 13.00,单据行取默认可改)
    barcode                  text                                                          ,  -- 条码(唯一部分索引 uk_item_barcode,可空)
    second_unit              text                                                          ,  -- 辅助单位(如"箱";unit 保持基本单位)
    convert_factor           numeric                                                       ,  -- 换算率(1 辅助单位对应的基本单位数)
    brand                    text                                                          ,  -- 品牌
    reference_purchase_price numeric(14,4)                                                 ,  -- 参考采购价(建单预填)
    reference_sale_price     numeric(14,4)                                                 ,  -- 参考销售价(建单预填)
    origin                   character varying(100)                                          -- 产地
);

COMMENT ON TABLE public.item IS '物品表实体(表 Item)。';

COMMENT ON COLUMN public.item.id IS '主键ID';
COMMENT ON COLUMN public.item.item_code IS '物品编码(唯一)';
COMMENT ON COLUMN public.item.item_name IS '物品名称';
COMMENT ON COLUMN public.item.unit IS '单位';
COMMENT ON COLUMN public.item.spec IS '规格';
COMMENT ON COLUMN public.item.attributes IS '扩展属性(JSON 字符串)';
COMMENT ON COLUMN public.item.status IS '状态:1 启用';
COMMENT ON COLUMN public.item.creator IS '创建人';
COMMENT ON COLUMN public.item.created_at IS '创建时间';
COMMENT ON COLUMN public.item.updater IS '更新人';
COMMENT ON COLUMN public.item.updated_at IS '更新时间';
COMMENT ON COLUMN public.item.category IS '物料分类(轻量单级,可空)';
COMMENT ON COLUMN public.item.min_stock IS '最低库存预警线(非空才参与低库存预警)';
COMMENT ON COLUMN public.item.default_tax_rate IS '默认税率(百分数,默认 13.00,单据行取默认可改)';
COMMENT ON COLUMN public.item.barcode IS '条码(唯一部分索引 uk_item_barcode,可空)';
COMMENT ON COLUMN public.item.second_unit IS '辅助单位(如"箱";unit 保持基本单位)';
COMMENT ON COLUMN public.item.convert_factor IS '换算率(1 辅助单位对应的基本单位数)';
COMMENT ON COLUMN public.item.brand IS '品牌';
COMMENT ON COLUMN public.item.reference_purchase_price IS '参考采购价(建单预填)';
COMMENT ON COLUMN public.item.reference_sale_price IS '参考销售价(建单预填)';
COMMENT ON COLUMN public.item.origin IS '产地';

ALTER TABLE ONLY public.item ADD CONSTRAINT item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.item ADD CONSTRAINT "Item_itemCode_key" UNIQUE (item_code);

CREATE UNIQUE INDEX uk_item_barcode ON public.item USING btree (barcode) WHERE (barcode IS NOT NULL);


-- ============================================================
-- 表:batch 批次
-- 说明:批次表实体(表 Batch);唯一键 (itemId, batchNo);生产日期/保质期为 DATE 类型。
-- ============================================================
CREATE TABLE public.batch (
    id              serial NOT NULL                     ,  -- 主键ID
    item_id         integer NOT NULL                    ,  -- 物品 ID
    batch_no        text NOT NULL                       ,  -- 批次号
    production_date date                                ,  -- 生产日期
    expiry_date     date                                ,  -- 保质期到期日
    supplier        text                                ,  -- 供应商
    status          text DEFAULT 'active'::text NOT NULL  -- 批次状态:active 启用
);

COMMENT ON TABLE public.batch IS '批次表实体(表 Batch);唯一键 (itemId, batchNo);生产日期/保质期为 DATE 类型。';

COMMENT ON COLUMN public.batch.id IS '主键ID';
COMMENT ON COLUMN public.batch.item_id IS '物品 ID';
COMMENT ON COLUMN public.batch.batch_no IS '批次号';
COMMENT ON COLUMN public.batch.production_date IS '生产日期';
COMMENT ON COLUMN public.batch.expiry_date IS '保质期到期日';
COMMENT ON COLUMN public.batch.supplier IS '供应商';
COMMENT ON COLUMN public.batch.status IS '批次状态:active 启用';

ALTER TABLE ONLY public.batch ADD CONSTRAINT batch_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.batch ADD CONSTRAINT "Batch_itemId_batchNo_key" UNIQUE (item_id, batch_no);

ALTER TABLE ONLY public.batch ADD CONSTRAINT "Batch_itemId_fkey" FOREIGN KEY (item_id) REFERENCES public.item(id);


-- ============================================================
-- 表:serial 序列号
-- 说明:序列号表实体(表 Serial);唯一键 (itemId, serialNo);状态 in_stock / out。
-- ============================================================
CREATE TABLE public.serial (
    id             serial NOT NULL                       ,  -- 主键ID
    item_id        integer NOT NULL                      ,  -- 物品 ID
    serial_no      text NOT NULL                         ,  -- 序列号
    warehouse_id   integer                               ,  -- 所在仓库 ID(可空)
    status         text DEFAULT 'in_stock'::text NOT NULL,  -- 状态:in_stock 在库 / out 出库
    inbound_time   timestamp without time zone           ,  -- 入库时间
    outbound_time  timestamp without time zone           ,  -- 出库时间
    ref_doc_no     text                                  ,  -- 最近入库单号(追溯链,可空)
    ref_out_doc_no text                                    -- 最近出库单号(追溯链,可空)
);

COMMENT ON TABLE public.serial IS '序列号表实体(表 Serial);唯一键 (itemId, serialNo);状态 in_stock / out。';

COMMENT ON COLUMN public.serial.id IS '主键ID';
COMMENT ON COLUMN public.serial.item_id IS '物品 ID';
COMMENT ON COLUMN public.serial.serial_no IS '序列号';
COMMENT ON COLUMN public.serial.warehouse_id IS '所在仓库 ID(可空)';
COMMENT ON COLUMN public.serial.status IS '状态:in_stock 在库 / out 出库';
COMMENT ON COLUMN public.serial.inbound_time IS '入库时间';
COMMENT ON COLUMN public.serial.outbound_time IS '出库时间';
COMMENT ON COLUMN public.serial.ref_doc_no IS '最近入库单号(追溯链,可空)';
COMMENT ON COLUMN public.serial.ref_out_doc_no IS '最近出库单号(追溯链,可空)';

ALTER TABLE ONLY public.serial ADD CONSTRAINT serial_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.serial ADD CONSTRAINT "Serial_itemId_serialNo_key" UNIQUE (item_id, serial_no);

ALTER TABLE ONLY public.serial ADD CONSTRAINT "Serial_itemId_fkey" FOREIGN KEY (item_id) REFERENCES public.item(id);
ALTER TABLE ONLY public.serial ADD CONSTRAINT "Serial_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);


-- ######################################################################
-- 业务域:库存核心
-- ######################################################################

-- ============================================================
-- 表:stock 库存余额
-- 说明:库存余额表实体(表 Stock);唯一键 (warehouseId, itemId, batchId, locationId),无批次/库位时以 0 占位;quantity 为 DECIMAL(18,4)。
-- ============================================================
CREATE TABLE public.stock (
    id                serial NOT NULL                     ,  -- 主键ID
    warehouse_id      integer NOT NULL                    ,  -- 仓库 ID
    item_id           integer NOT NULL                    ,  -- 物品 ID
    batch_id          integer DEFAULT 0 NOT NULL          ,  -- 批次 ID(无批次为 0)
    location_id       integer DEFAULT 0 NOT NULL          ,  -- 库位 ID(无库位为 0)
    quantity          numeric(18,4) DEFAULT 0 NOT NULL    ,  -- 库存数量
    updated_at        timestamp without time zone NOT NULL,  -- 更新时间
    pre_allocated_qty numeric(18,4) DEFAULT 0 NOT NULL      -- 预占量(销售订单审批预占,可用=quantity-preAllocatedQty)
);

COMMENT ON TABLE public.stock IS '库存余额表实体(表 Stock);唯一键 (warehouseId, itemId, batchId, locationId),无批次/库位时以 0 占位;quantity 为 DECIMAL(18,4)。';

COMMENT ON COLUMN public.stock.id IS '主键ID';
COMMENT ON COLUMN public.stock.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN public.stock.item_id IS '物品 ID';
COMMENT ON COLUMN public.stock.batch_id IS '批次 ID(无批次为 0)';
COMMENT ON COLUMN public.stock.location_id IS '库位 ID(无库位为 0)';
COMMENT ON COLUMN public.stock.quantity IS '库存数量';
COMMENT ON COLUMN public.stock.updated_at IS '更新时间';
COMMENT ON COLUMN public.stock.pre_allocated_qty IS '预占量(销售订单审批预占,可用=quantity-preAllocatedQty)';

ALTER TABLE ONLY public.stock ADD CONSTRAINT stock_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.stock ADD CONSTRAINT "Stock_warehouseId_itemId_batchId_locationId_key" UNIQUE (warehouse_id, item_id, batch_id, location_id);


-- ============================================================
-- 表:stock_transaction 出入库流水
-- 说明:出入库流水表实体(表 StockTransaction);流水只插不改,必带 afterQty(事务内扣减/增加后回读)。
-- ============================================================
CREATE TABLE public.stock_transaction (
    id           serial NOT NULL                                               ,  -- 主键ID
    warehouse_id integer NOT NULL                                              ,  -- 仓库 ID
    item_id      integer NOT NULL                                              ,  -- 物品 ID
    batch_id     integer DEFAULT 0 NOT NULL                                    ,  -- 批次 ID(无批次为 0)
    location_id  integer DEFAULT 0 NOT NULL                                    ,  -- 库位 ID(无库位为 0)
    change_qty   numeric(18,4) NOT NULL                                        ,  -- 变动数量(入库为正,出库为负)
    after_qty    numeric(18,4) NOT NULL                                        ,  -- 变动后余额
    biz_code     text NOT NULL                                                 ,  -- 业务类型:inbound 入库 / outbound 出库
    doc_no       text                                                          ,  -- 关联单据号
    operator     text                                                          ,  -- 操作人
    created_at   timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL  -- 创建时间
);

COMMENT ON TABLE public.stock_transaction IS '出入库流水表实体(表 StockTransaction);流水只插不改,必带 afterQty(事务内扣减/增加后回读)。';

COMMENT ON COLUMN public.stock_transaction.id IS '主键ID';
COMMENT ON COLUMN public.stock_transaction.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN public.stock_transaction.item_id IS '物品 ID';
COMMENT ON COLUMN public.stock_transaction.batch_id IS '批次 ID(无批次为 0)';
COMMENT ON COLUMN public.stock_transaction.location_id IS '库位 ID(无库位为 0)';
COMMENT ON COLUMN public.stock_transaction.change_qty IS '变动数量(入库为正,出库为负)';
COMMENT ON COLUMN public.stock_transaction.after_qty IS '变动后余额';
COMMENT ON COLUMN public.stock_transaction.biz_code IS '业务类型:inbound 入库 / outbound 出库';
COMMENT ON COLUMN public.stock_transaction.doc_no IS '关联单据号';
COMMENT ON COLUMN public.stock_transaction.operator IS '操作人';
COMMENT ON COLUMN public.stock_transaction.created_at IS '创建时间';

ALTER TABLE ONLY public.stock_transaction ADD CONSTRAINT stock_transaction_pkey PRIMARY KEY (id);


-- ######################################################################
-- 业务域:采购域
-- ######################################################################

-- ============================================================
-- 表:supplier 供应商
-- 说明:供应商主数据实体(表 Supplier)。
-- ============================================================
CREATE TABLE public.supplier (
    id               serial NOT NULL                    ,  -- 主键ID
    supplier_code    text NOT NULL                      ,  -- 供应商编码(唯一)
    supplier_name    text NOT NULL                      ,  -- 供应商名称
    tax_no           text                               ,  -- 税号
    default_tax_rate numeric(5,2) DEFAULT 13.00 NOT NULL,  -- 默认税率(百分数)
    contact          text                               ,  -- 联系人
    phone            text                               ,  -- 联系电话
    address          text                               ,  -- 地址
    settle_method    text                               ,  -- 结算方式
    pay_term_days    integer                            ,  -- 付款账期天数
    status           integer DEFAULT 1 NOT NULL         ,  -- 状态:1 启用 0 停用
    remark           text                               ,  -- 备注
    creator          text                               ,  -- 创建人
    created_at       timestamp without time zone        ,  -- 创建时间
    updater          text                               ,  -- 更新人
    updated_at       timestamp without time zone        ,  -- 更新时间
    bank_name        text                               ,  -- 开户行
    bank_account     text                               ,  -- 银行账号
    credit_limit     numeric                            ,  -- 信用额度
    delivery_address text                               ,  -- 交货地址
    email            character varying(100)               -- 邮箱
);

COMMENT ON TABLE public.supplier IS '供应商主数据实体(表 Supplier)。';

COMMENT ON COLUMN public.supplier.id IS '主键ID';
COMMENT ON COLUMN public.supplier.supplier_code IS '供应商编码(唯一)';
COMMENT ON COLUMN public.supplier.supplier_name IS '供应商名称';
COMMENT ON COLUMN public.supplier.tax_no IS '税号';
COMMENT ON COLUMN public.supplier.default_tax_rate IS '默认税率(百分数)';
COMMENT ON COLUMN public.supplier.contact IS '联系人';
COMMENT ON COLUMN public.supplier.phone IS '联系电话';
COMMENT ON COLUMN public.supplier.address IS '地址';
COMMENT ON COLUMN public.supplier.settle_method IS '结算方式';
COMMENT ON COLUMN public.supplier.pay_term_days IS '付款账期天数';
COMMENT ON COLUMN public.supplier.status IS '状态:1 启用 0 停用';
COMMENT ON COLUMN public.supplier.remark IS '备注';
COMMENT ON COLUMN public.supplier.creator IS '创建人';
COMMENT ON COLUMN public.supplier.created_at IS '创建时间';
COMMENT ON COLUMN public.supplier.updater IS '更新人';
COMMENT ON COLUMN public.supplier.updated_at IS '更新时间';
COMMENT ON COLUMN public.supplier.bank_name IS '开户行';
COMMENT ON COLUMN public.supplier.bank_account IS '银行账号';
COMMENT ON COLUMN public.supplier.credit_limit IS '信用额度';
COMMENT ON COLUMN public.supplier.delivery_address IS '交货地址';
COMMENT ON COLUMN public.supplier.email IS '邮箱';

ALTER TABLE ONLY public.supplier ADD CONSTRAINT supplier_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.supplier ADD CONSTRAINT "Supplier_supplierCode_key" UNIQUE (supplier_code);


-- ============================================================
-- 表:purchase_order 采购订单主表
-- 说明:采购订单表头实体(表 PurchaseOrder,状态机 draft/pending/approved/completed/closed/rejected/voided)。
-- ============================================================
CREATE TABLE public.purchase_order (
    id                      serial NOT NULL                    ,  -- 主键ID
    doc_no                  text NOT NULL                      ,  -- 单据编号(全局唯一)
    doc_date                date NOT NULL                      ,  -- 单据日期
    supplier_id             integer NOT NULL                   ,  -- 供应商 ID
    buyer_id                integer NOT NULL                   ,  -- 采购员用户 ID
    allow_over_receipt_rate numeric(5,4) DEFAULT 0 NOT NULL    ,  -- 超收比例(%)
    total_amount            numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 整单不含税合计
    total_tax_amount        numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 整单税额合计
    total_tax_inclusive     numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 整单价税合计
    status                  text DEFAULT 'draft'::text NOT NULL,  -- 单据状态:draft/pending/approved/completed/closed/rejected/voided
    creator                 text                               ,  -- 制单人
    created_at              timestamp without time zone        ,  -- 制单时间
    updater                 text                               ,  -- 更新人
    updated_at              timestamp without time zone        ,  -- 更新时间
    approver                text                               ,  -- 审批人
    approved_at             timestamp without time zone        ,  -- 审批时间
    reject_reason           text                               ,  -- 驳回原因
    remark                  text                               ,  -- 备注
    contract_no             text                               ,  -- 合同号
    freight                 numeric                            ,  -- 运费
    shipping_address        text                               ,  -- 交货地址
    discount_amount         numeric(14,2)                      ,  -- 折扣额(不参与合计计算)
    currency_code           character varying(3)               ,  -- 币种(如 CNY)
    exchange_rate           numeric(14,6)                        -- 汇率
);

COMMENT ON TABLE public.purchase_order IS '采购订单表头实体(表 PurchaseOrder,状态机 draft/pending/approved/completed/closed/rejected/voided)。';

COMMENT ON COLUMN public.purchase_order.id IS '主键ID';
COMMENT ON COLUMN public.purchase_order.doc_no IS '单据编号(全局唯一)';
COMMENT ON COLUMN public.purchase_order.doc_date IS '单据日期';
COMMENT ON COLUMN public.purchase_order.supplier_id IS '供应商 ID';
COMMENT ON COLUMN public.purchase_order.buyer_id IS '采购员用户 ID';
COMMENT ON COLUMN public.purchase_order.allow_over_receipt_rate IS '超收比例(%)';
COMMENT ON COLUMN public.purchase_order.total_amount IS '整单不含税合计';
COMMENT ON COLUMN public.purchase_order.total_tax_amount IS '整单税额合计';
COMMENT ON COLUMN public.purchase_order.total_tax_inclusive IS '整单价税合计';
COMMENT ON COLUMN public.purchase_order.status IS '单据状态:draft/pending/approved/completed/closed/rejected/voided';
COMMENT ON COLUMN public.purchase_order.creator IS '制单人';
COMMENT ON COLUMN public.purchase_order.created_at IS '制单时间';
COMMENT ON COLUMN public.purchase_order.updater IS '更新人';
COMMENT ON COLUMN public.purchase_order.updated_at IS '更新时间';
COMMENT ON COLUMN public.purchase_order.approver IS '审批人';
COMMENT ON COLUMN public.purchase_order.approved_at IS '审批时间';
COMMENT ON COLUMN public.purchase_order.reject_reason IS '驳回原因';
COMMENT ON COLUMN public.purchase_order.remark IS '备注';
COMMENT ON COLUMN public.purchase_order.contract_no IS '合同号';
COMMENT ON COLUMN public.purchase_order.freight IS '运费';
COMMENT ON COLUMN public.purchase_order.shipping_address IS '交货地址';
COMMENT ON COLUMN public.purchase_order.discount_amount IS '折扣额(不参与合计计算)';
COMMENT ON COLUMN public.purchase_order.currency_code IS '币种(如 CNY)';
COMMENT ON COLUMN public.purchase_order.exchange_rate IS '汇率';

ALTER TABLE ONLY public.purchase_order ADD CONSTRAINT purchase_order_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.purchase_order ADD CONSTRAINT "PurchaseOrder_docNo_key" UNIQUE (doc_no);


-- ============================================================
-- 表:purchase_order_item 采购订单明细行
-- 说明:采购订单行实体(表 PurchaseOrderItem)。
-- ============================================================
CREATE TABLE public.purchase_order_item (
    id                     serial NOT NULL                    ,  -- 主键ID
    order_id               integer NOT NULL                   ,  -- 订单 ID
    line_no                integer NOT NULL                   ,  -- 行号
    item_id                integer NOT NULL                   ,  -- 物品 ID
    spec_snapshot          text                               ,  -- 规格快照
    unit                   text                               ,  -- 单位快照
    ordered_qty            numeric(18,4) NOT NULL             ,  -- 订购数量
    arrived_qty            numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 累计到货数量
    expected_delivery_date date                               ,  -- 计划交货日
    unit_price             numeric(18,4) NOT NULL             ,  -- 不含税单价
    tax_rate               numeric(5,2) DEFAULT 13.00 NOT NULL,  -- 税率(百分数)
    amount                 numeric(18,4) NOT NULL             ,  -- 不含税金额
    tax_amount             numeric(18,4) NOT NULL             ,  -- 税额
    tax_inclusive_total    numeric(18,4) NOT NULL             ,  -- 价税合计
    closed                 boolean DEFAULT false NOT NULL     ,  -- 行是否关闭
    line_remark            text                               ,  -- 行备注
    creator                character varying(64)              ,  -- 创建人
    returned_qty           numeric(16,4) DEFAULT 0 NOT NULL   ,  -- 累计退货数量(退货单过账回写,可退上限=arrived-returned)
    tax_price              numeric(18,4) DEFAULT 0 NOT NULL     -- 含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)
);

COMMENT ON TABLE public.purchase_order_item IS '采购订单行实体(表 PurchaseOrderItem)。';

COMMENT ON COLUMN public.purchase_order_item.id IS '主键ID';
COMMENT ON COLUMN public.purchase_order_item.order_id IS '订单 ID';
COMMENT ON COLUMN public.purchase_order_item.line_no IS '行号';
COMMENT ON COLUMN public.purchase_order_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.purchase_order_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.purchase_order_item.unit IS '单位快照';
COMMENT ON COLUMN public.purchase_order_item.ordered_qty IS '订购数量';
COMMENT ON COLUMN public.purchase_order_item.arrived_qty IS '累计到货数量';
COMMENT ON COLUMN public.purchase_order_item.expected_delivery_date IS '计划交货日';
COMMENT ON COLUMN public.purchase_order_item.unit_price IS '不含税单价';
COMMENT ON COLUMN public.purchase_order_item.tax_rate IS '税率(百分数)';
COMMENT ON COLUMN public.purchase_order_item.amount IS '不含税金额';
COMMENT ON COLUMN public.purchase_order_item.tax_amount IS '税额';
COMMENT ON COLUMN public.purchase_order_item.tax_inclusive_total IS '价税合计';
COMMENT ON COLUMN public.purchase_order_item.closed IS '行是否关闭';
COMMENT ON COLUMN public.purchase_order_item.line_remark IS '行备注';
COMMENT ON COLUMN public.purchase_order_item.creator IS '创建人';
COMMENT ON COLUMN public.purchase_order_item.returned_qty IS '累计退货数量(退货单过账回写,可退上限=arrived-returned)';
COMMENT ON COLUMN public.purchase_order_item.tax_price IS '含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)';

ALTER TABLE ONLY public.purchase_order_item ADD CONSTRAINT purchase_order_item_pkey PRIMARY KEY (id);


-- ============================================================
-- 表:purchase_return 采购退货单主表
-- 说明:采购退货单头实体(表 purchase_return)。
-- ============================================================
CREATE TABLE public.purchase_return (
    id                serial NOT NULL                                                     ,  -- 主键ID
    doc_no            text NOT NULL                                                       ,  -- 单据号(CT-YYYYMMDD-NNNN,唯一)
    doc_date          date NOT NULL                                                       ,  -- 单据日期
    purchase_order_id integer NOT NULL                                                    ,  -- 原采购订单 ID
    warehouse_id      integer NOT NULL                                                    ,  -- 退货仓库 ID
    total_amount      numeric(14,2)                                                       ,  -- 单据总金额(行金额合计,服务端落)
    remark            text                                                                ,  -- 备注
    status            character varying(20) DEFAULT 'finished'::character varying NOT NULL,  -- 单据状态:finished(create 即过账)
    creator           text                                                                ,  -- 创建人
    created_at        timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL      ,  -- 创建时间
    updater           text                                                                ,  -- 更新人
    updated_at        timestamp without time zone                                           -- 更新时间
);

COMMENT ON TABLE public.purchase_return IS '采购退货单头实体(表 purchase_return)。';

COMMENT ON COLUMN public.purchase_return.id IS '主键ID';
COMMENT ON COLUMN public.purchase_return.doc_no IS '单据号(CT-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN public.purchase_return.doc_date IS '单据日期';
COMMENT ON COLUMN public.purchase_return.purchase_order_id IS '原采购订单 ID';
COMMENT ON COLUMN public.purchase_return.warehouse_id IS '退货仓库 ID';
COMMENT ON COLUMN public.purchase_return.total_amount IS '单据总金额(行金额合计,服务端落)';
COMMENT ON COLUMN public.purchase_return.remark IS '备注';
COMMENT ON COLUMN public.purchase_return.status IS '单据状态:finished(create 即过账)';
COMMENT ON COLUMN public.purchase_return.creator IS '创建人';
COMMENT ON COLUMN public.purchase_return.created_at IS '创建时间';
COMMENT ON COLUMN public.purchase_return.updater IS '更新人';
COMMENT ON COLUMN public.purchase_return.updated_at IS '更新时间';

ALTER TABLE ONLY public.purchase_return ADD CONSTRAINT purchase_return_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.purchase_return ADD CONSTRAINT uk_purchase_return_doc_no UNIQUE (doc_no);

CREATE INDEX idx_purchase_return_order ON public.purchase_return USING btree (purchase_order_id);
CREATE INDEX idx_purchase_return_warehouse ON public.purchase_return USING btree (warehouse_id);

ALTER TABLE ONLY public.purchase_return ADD CONSTRAINT fk_purchase_return_order FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_order(id);
ALTER TABLE ONLY public.purchase_return ADD CONSTRAINT fk_purchase_return_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);


-- ============================================================
-- 表:purchase_return_item 采购退货单明细行
-- 说明:采购退货单行实体(表 purchase_return_item)。
-- ============================================================
CREATE TABLE public.purchase_return_item (
    id                     serial NOT NULL                 ,  -- 主键ID
    doc_id                 integer NOT NULL                ,  -- 退货单 ID
    line_no                integer                         ,  -- 行号(从 1 连号,服务端落)
    purchase_order_item_id integer NOT NULL                ,  -- 原采购订单行 ID
    item_id                integer NOT NULL                ,  -- 物品 ID
    spec_snapshot          text                            ,  -- 规格快照
    unit                   text                            ,  -- 单位快照
    quantity               numeric(16,4) NOT NULL          ,  -- 退货数量
    unit_price             numeric(14,4) NOT NULL          ,  -- 不含税单价(原行快照,服务端取)
    tax_rate               numeric(6,4)                    ,  -- 税率(原行快照,服务端取)
    amount                 numeric(14,2)                   ,  -- 行金额快照(数量×不含税单价)
    tax_amount             numeric(14,2)                   ,  -- 行税额快照(金额×税率/100)
    tax_inclusive_total    numeric(14,2)                   ,  -- 含税行金额快照(金额+税额)
    creator                text                            ,  -- 创建人
    tax_price              numeric(14,4) DEFAULT 0 NOT NULL  -- 含税单价(V20,与 unit_price 二选一录入;服务端价税重算后落库)
);

COMMENT ON TABLE public.purchase_return_item IS '采购退货单行实体(表 purchase_return_item)。';

COMMENT ON COLUMN public.purchase_return_item.id IS '主键ID';
COMMENT ON COLUMN public.purchase_return_item.doc_id IS '退货单 ID';
COMMENT ON COLUMN public.purchase_return_item.line_no IS '行号(从 1 连号,服务端落)';
COMMENT ON COLUMN public.purchase_return_item.purchase_order_item_id IS '原采购订单行 ID';
COMMENT ON COLUMN public.purchase_return_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.purchase_return_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.purchase_return_item.unit IS '单位快照';
COMMENT ON COLUMN public.purchase_return_item.quantity IS '退货数量';
COMMENT ON COLUMN public.purchase_return_item.unit_price IS '不含税单价(原行快照,服务端取)';
COMMENT ON COLUMN public.purchase_return_item.tax_rate IS '税率(原行快照,服务端取)';
COMMENT ON COLUMN public.purchase_return_item.amount IS '行金额快照(数量×不含税单价)';
COMMENT ON COLUMN public.purchase_return_item.tax_amount IS '行税额快照(金额×税率/100)';
COMMENT ON COLUMN public.purchase_return_item.tax_inclusive_total IS '含税行金额快照(金额+税额)';
COMMENT ON COLUMN public.purchase_return_item.creator IS '创建人';
COMMENT ON COLUMN public.purchase_return_item.tax_price IS '含税单价(V20,与 unit_price 二选一录入;服务端价税重算后落库)';

ALTER TABLE ONLY public.purchase_return_item ADD CONSTRAINT purchase_return_item_pkey PRIMARY KEY (id);

CREATE INDEX idx_purchase_return_item_doc ON public.purchase_return_item USING btree (doc_id);
CREATE INDEX idx_purchase_return_item_po_item ON public.purchase_return_item USING btree (purchase_order_item_id);

ALTER TABLE ONLY public.purchase_return_item ADD CONSTRAINT fk_purchase_return_item_doc FOREIGN KEY (doc_id) REFERENCES public.purchase_return(id);


-- ######################################################################
-- 业务域:销售域
-- ######################################################################

-- ============================================================
-- 表:customer 客户
-- 说明:客户主数据实体(表 Customer)。
-- ============================================================
CREATE TABLE public.customer (
    id               serial NOT NULL                    ,  -- 主键ID
    customer_code    text NOT NULL                      ,  -- 客户编码(唯一)
    customer_name    text NOT NULL                      ,  -- 客户名称
    tax_no           text                               ,  -- 税号
    default_tax_rate numeric(5,2) DEFAULT 13.00 NOT NULL,  -- 默认税率(百分数)
    contact          text                               ,  -- 联系人
    phone            text                               ,  -- 联系电话
    address          text                               ,  -- 地址
    settle_method    text                               ,  -- 结算方式
    pay_term_days    integer                            ,  -- 客户账期天数
    status           integer DEFAULT 1 NOT NULL         ,  -- 状态:1 启用 0 停用
    remark           text                               ,  -- 备注
    creator          text                               ,  -- 创建人
    created_at       timestamp without time zone        ,  -- 创建时间
    updater          text                               ,  -- 更新人
    updated_at       timestamp without time zone        ,  -- 更新时间
    bank_name        text                               ,  -- 开户行
    bank_account     text                               ,  -- 银行账号
    credit_limit     numeric                            ,  -- 信用额度
    delivery_address text                               ,  -- 交货地址
    email            character varying(100)               -- 邮箱
);

COMMENT ON TABLE public.customer IS '客户主数据实体(表 Customer)。';

COMMENT ON COLUMN public.customer.id IS '主键ID';
COMMENT ON COLUMN public.customer.customer_code IS '客户编码(唯一)';
COMMENT ON COLUMN public.customer.customer_name IS '客户名称';
COMMENT ON COLUMN public.customer.tax_no IS '税号';
COMMENT ON COLUMN public.customer.default_tax_rate IS '默认税率(百分数)';
COMMENT ON COLUMN public.customer.contact IS '联系人';
COMMENT ON COLUMN public.customer.phone IS '联系电话';
COMMENT ON COLUMN public.customer.address IS '地址';
COMMENT ON COLUMN public.customer.settle_method IS '结算方式';
COMMENT ON COLUMN public.customer.pay_term_days IS '客户账期天数';
COMMENT ON COLUMN public.customer.status IS '状态:1 启用 0 停用';
COMMENT ON COLUMN public.customer.remark IS '备注';
COMMENT ON COLUMN public.customer.creator IS '创建人';
COMMENT ON COLUMN public.customer.created_at IS '创建时间';
COMMENT ON COLUMN public.customer.updater IS '更新人';
COMMENT ON COLUMN public.customer.updated_at IS '更新时间';
COMMENT ON COLUMN public.customer.bank_name IS '开户行';
COMMENT ON COLUMN public.customer.bank_account IS '银行账号';
COMMENT ON COLUMN public.customer.credit_limit IS '信用额度';
COMMENT ON COLUMN public.customer.delivery_address IS '交货地址';
COMMENT ON COLUMN public.customer.email IS '邮箱';

ALTER TABLE ONLY public.customer ADD CONSTRAINT customer_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.customer ADD CONSTRAINT "Customer_customerCode_key" UNIQUE (customer_code);


-- ============================================================
-- 表:sales_order 销售订单主表
-- 说明:销售订单表头实体(表 SalesOrder,发货仓 warehouseId 用于审批时 FEFO 预占)。
-- ============================================================
CREATE TABLE public.sales_order (
    id                  serial NOT NULL                    ,  -- 主键ID
    doc_no              text NOT NULL                      ,  -- 单据编号(全局唯一)
    doc_date            date NOT NULL                      ,  -- 单据日期
    customer_id         integer NOT NULL                   ,  -- 客户 ID
    salesperson_id      integer NOT NULL                   ,  -- 销售员用户 ID
    warehouse_id        integer NOT NULL                   ,  -- 发货仓库 ID(审批时 FEFO 预占)
    total_amount        numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 整单不含税合计
    total_tax_amount    numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 整单税额合计
    total_tax_inclusive numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 整单价税合计
    status              text DEFAULT 'draft'::text NOT NULL,  -- 单据状态:draft/pending/approved/completed/closed/rejected/voided
    creator             text                               ,  -- 制单人
    created_at          timestamp without time zone        ,  -- 制单时间
    updater             text                               ,  -- 更新人
    updated_at          timestamp without time zone        ,  -- 更新时间
    approver            text                               ,  -- 审批人
    approved_at         timestamp without time zone        ,  -- 审批时间
    reject_reason       text                               ,  -- 驳回原因
    remark              text                               ,  -- 备注
    contract_no         text                               ,  -- 合同号
    freight             numeric                            ,  -- 运费
    shipping_address    text                               ,  -- 交货地址
    discount_amount     numeric(14,2)                      ,  -- 折扣额(不参与合计计算)
    currency_code       character varying(3)               ,  -- 币种(如 CNY)
    exchange_rate       numeric(14,6)                        -- 汇率
);

COMMENT ON TABLE public.sales_order IS '销售订单表头实体(表 SalesOrder,发货仓 warehouseId 用于审批时 FEFO 预占)。';

COMMENT ON COLUMN public.sales_order.id IS '主键ID';
COMMENT ON COLUMN public.sales_order.doc_no IS '单据编号(全局唯一)';
COMMENT ON COLUMN public.sales_order.doc_date IS '单据日期';
COMMENT ON COLUMN public.sales_order.customer_id IS '客户 ID';
COMMENT ON COLUMN public.sales_order.salesperson_id IS '销售员用户 ID';
COMMENT ON COLUMN public.sales_order.warehouse_id IS '发货仓库 ID(审批时 FEFO 预占)';
COMMENT ON COLUMN public.sales_order.total_amount IS '整单不含税合计';
COMMENT ON COLUMN public.sales_order.total_tax_amount IS '整单税额合计';
COMMENT ON COLUMN public.sales_order.total_tax_inclusive IS '整单价税合计';
COMMENT ON COLUMN public.sales_order.status IS '单据状态:draft/pending/approved/completed/closed/rejected/voided';
COMMENT ON COLUMN public.sales_order.creator IS '制单人';
COMMENT ON COLUMN public.sales_order.created_at IS '制单时间';
COMMENT ON COLUMN public.sales_order.updater IS '更新人';
COMMENT ON COLUMN public.sales_order.updated_at IS '更新时间';
COMMENT ON COLUMN public.sales_order.approver IS '审批人';
COMMENT ON COLUMN public.sales_order.approved_at IS '审批时间';
COMMENT ON COLUMN public.sales_order.reject_reason IS '驳回原因';
COMMENT ON COLUMN public.sales_order.remark IS '备注';
COMMENT ON COLUMN public.sales_order.contract_no IS '合同号';
COMMENT ON COLUMN public.sales_order.freight IS '运费';
COMMENT ON COLUMN public.sales_order.shipping_address IS '交货地址';
COMMENT ON COLUMN public.sales_order.discount_amount IS '折扣额(不参与合计计算)';
COMMENT ON COLUMN public.sales_order.currency_code IS '币种(如 CNY)';
COMMENT ON COLUMN public.sales_order.exchange_rate IS '汇率';

ALTER TABLE ONLY public.sales_order ADD CONSTRAINT sales_order_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sales_order ADD CONSTRAINT "SalesOrder_docNo_key" UNIQUE (doc_no);


-- ============================================================
-- 表:sales_order_item 销售订单明细行
-- 说明:销售订单行实体(表 SalesOrderItem)。
-- ============================================================
CREATE TABLE public.sales_order_item (
    id                     serial NOT NULL                    ,  -- 主键ID
    order_id               integer NOT NULL                   ,  -- 订单 ID
    line_no                integer NOT NULL                   ,  -- 行号
    item_id                integer NOT NULL                   ,  -- 物品 ID
    spec_snapshot          text                               ,  -- 规格快照
    unit                   text                               ,  -- 单位快照
    ordered_qty            numeric(18,4) NOT NULL             ,  -- 订购数量
    shipped_qty            numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 累计发货数量
    customer_delivery_date date                               ,  -- 客户要货日
    unit_price             numeric(18,4) NOT NULL             ,  -- 不含税单价
    tax_rate               numeric(5,2) DEFAULT 13.00 NOT NULL,  -- 税率(百分数)
    amount                 numeric(18,4) NOT NULL             ,  -- 不含税金额
    tax_amount             numeric(18,4) NOT NULL             ,  -- 税额
    tax_inclusive_total    numeric(18,4) NOT NULL             ,  -- 价税合计
    closed                 boolean DEFAULT false NOT NULL     ,  -- 行是否关闭
    line_remark            text                               ,  -- 行备注
    creator                character varying(64)              ,  -- 创建人
    returned_qty           numeric(16,4) DEFAULT 0 NOT NULL   ,  -- 累计退货数量(退货单过账回写,可退上限=shipped-returned)
    tax_price              numeric(18,4) DEFAULT 0 NOT NULL     -- 含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)
);

COMMENT ON TABLE public.sales_order_item IS '销售订单行实体(表 SalesOrderItem)。';

COMMENT ON COLUMN public.sales_order_item.id IS '主键ID';
COMMENT ON COLUMN public.sales_order_item.order_id IS '订单 ID';
COMMENT ON COLUMN public.sales_order_item.line_no IS '行号';
COMMENT ON COLUMN public.sales_order_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.sales_order_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.sales_order_item.unit IS '单位快照';
COMMENT ON COLUMN public.sales_order_item.ordered_qty IS '订购数量';
COMMENT ON COLUMN public.sales_order_item.shipped_qty IS '累计发货数量';
COMMENT ON COLUMN public.sales_order_item.customer_delivery_date IS '客户要货日';
COMMENT ON COLUMN public.sales_order_item.unit_price IS '不含税单价';
COMMENT ON COLUMN public.sales_order_item.tax_rate IS '税率(百分数)';
COMMENT ON COLUMN public.sales_order_item.amount IS '不含税金额';
COMMENT ON COLUMN public.sales_order_item.tax_amount IS '税额';
COMMENT ON COLUMN public.sales_order_item.tax_inclusive_total IS '价税合计';
COMMENT ON COLUMN public.sales_order_item.closed IS '行是否关闭';
COMMENT ON COLUMN public.sales_order_item.line_remark IS '行备注';
COMMENT ON COLUMN public.sales_order_item.creator IS '创建人';
COMMENT ON COLUMN public.sales_order_item.returned_qty IS '累计退货数量(退货单过账回写,可退上限=shipped-returned)';
COMMENT ON COLUMN public.sales_order_item.tax_price IS '含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)';

ALTER TABLE ONLY public.sales_order_item ADD CONSTRAINT sales_order_item_pkey PRIMARY KEY (id);


-- ============================================================
-- 表:sales_return 销售退货单主表
-- 说明:销售退货单头实体(表 sales_return)。
-- ============================================================
CREATE TABLE public.sales_return (
    id             serial NOT NULL                                                     ,  -- 主键ID
    doc_no         text NOT NULL                                                       ,  -- 单据号(XT-YYYYMMDD-NNNN,唯一)
    doc_date       date NOT NULL                                                       ,  -- 单据日期
    sales_order_id integer NOT NULL                                                    ,  -- 原销售订单 ID
    warehouse_id   integer NOT NULL                                                    ,  -- 退货仓库 ID
    total_amount   numeric(14,2)                                                       ,  -- 单据总金额(行金额合计,服务端落)
    remark         text                                                                ,  -- 备注
    status         character varying(20) DEFAULT 'finished'::character varying NOT NULL,  -- 单据状态:finished(create 即过账)
    creator        text                                                                ,  -- 创建人
    created_at     timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL      ,  -- 创建时间
    updater        text                                                                ,  -- 更新人
    updated_at     timestamp without time zone                                           -- 更新时间
);

COMMENT ON TABLE public.sales_return IS '销售退货单头实体(表 sales_return)。';

COMMENT ON COLUMN public.sales_return.id IS '主键ID';
COMMENT ON COLUMN public.sales_return.doc_no IS '单据号(XT-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN public.sales_return.doc_date IS '单据日期';
COMMENT ON COLUMN public.sales_return.sales_order_id IS '原销售订单 ID';
COMMENT ON COLUMN public.sales_return.warehouse_id IS '退货仓库 ID';
COMMENT ON COLUMN public.sales_return.total_amount IS '单据总金额(行金额合计,服务端落)';
COMMENT ON COLUMN public.sales_return.remark IS '备注';
COMMENT ON COLUMN public.sales_return.status IS '单据状态:finished(create 即过账)';
COMMENT ON COLUMN public.sales_return.creator IS '创建人';
COMMENT ON COLUMN public.sales_return.created_at IS '创建时间';
COMMENT ON COLUMN public.sales_return.updater IS '更新人';
COMMENT ON COLUMN public.sales_return.updated_at IS '更新时间';

ALTER TABLE ONLY public.sales_return ADD CONSTRAINT sales_return_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sales_return ADD CONSTRAINT uk_sales_return_doc_no UNIQUE (doc_no);

CREATE INDEX idx_sales_return_order ON public.sales_return USING btree (sales_order_id);
CREATE INDEX idx_sales_return_warehouse ON public.sales_return USING btree (warehouse_id);

ALTER TABLE ONLY public.sales_return ADD CONSTRAINT fk_sales_return_order FOREIGN KEY (sales_order_id) REFERENCES public.sales_order(id);
ALTER TABLE ONLY public.sales_return ADD CONSTRAINT fk_sales_return_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);


-- ============================================================
-- 表:sales_return_item 销售退货单明细行
-- 说明:销售退货单行实体(表 sales_return_item)。
-- ============================================================
CREATE TABLE public.sales_return_item (
    id                  serial NOT NULL                 ,  -- 主键ID
    doc_id              integer NOT NULL                ,  -- 退货单 ID
    line_no             integer                         ,  -- 行号(从 1 连号,服务端落)
    sales_order_item_id integer NOT NULL                ,  -- 原销售订单行 ID
    item_id             integer NOT NULL                ,  -- 物品 ID
    spec_snapshot       text                            ,  -- 规格快照
    unit                text                            ,  -- 单位快照
    quantity            numeric(16,4) NOT NULL          ,  -- 退货数量
    unit_price          numeric(14,4) NOT NULL          ,  -- 不含税单价(原行快照,服务端取)
    tax_rate            numeric(6,4)                    ,  -- 税率(原行快照,服务端取)
    amount              numeric(14,2)                   ,  -- 行金额快照(数量×不含税单价)
    tax_amount          numeric(14,2)                   ,  -- 行税额快照(金额×税率/100)
    tax_inclusive_total numeric(14,2)                   ,  -- 含税行金额快照(金额+税额)
    creator             text                            ,  -- 创建人
    tax_price           numeric(14,4) DEFAULT 0 NOT NULL  -- 含税单价(V20,与 unit_price 二选一录入;服务端价税重算后落库)
);

COMMENT ON TABLE public.sales_return_item IS '销售退货单行实体(表 sales_return_item)。';

COMMENT ON COLUMN public.sales_return_item.id IS '主键ID';
COMMENT ON COLUMN public.sales_return_item.doc_id IS '退货单 ID';
COMMENT ON COLUMN public.sales_return_item.line_no IS '行号(从 1 连号,服务端落)';
COMMENT ON COLUMN public.sales_return_item.sales_order_item_id IS '原销售订单行 ID';
COMMENT ON COLUMN public.sales_return_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.sales_return_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.sales_return_item.unit IS '单位快照';
COMMENT ON COLUMN public.sales_return_item.quantity IS '退货数量';
COMMENT ON COLUMN public.sales_return_item.unit_price IS '不含税单价(原行快照,服务端取)';
COMMENT ON COLUMN public.sales_return_item.tax_rate IS '税率(原行快照,服务端取)';
COMMENT ON COLUMN public.sales_return_item.amount IS '行金额快照(数量×不含税单价)';
COMMENT ON COLUMN public.sales_return_item.tax_amount IS '行税额快照(金额×税率/100)';
COMMENT ON COLUMN public.sales_return_item.tax_inclusive_total IS '含税行金额快照(金额+税额)';
COMMENT ON COLUMN public.sales_return_item.creator IS '创建人';
COMMENT ON COLUMN public.sales_return_item.tax_price IS '含税单价(V20,与 unit_price 二选一录入;服务端价税重算后落库)';

ALTER TABLE ONLY public.sales_return_item ADD CONSTRAINT sales_return_item_pkey PRIMARY KEY (id);

CREATE INDEX idx_sales_return_item_doc ON public.sales_return_item USING btree (doc_id);
CREATE INDEX idx_sales_return_item_so_item ON public.sales_return_item USING btree (sales_order_item_id);

ALTER TABLE ONLY public.sales_return_item ADD CONSTRAINT fk_sales_return_item_doc FOREIGN KEY (doc_id) REFERENCES public.sales_return(id);


-- ######################################################################
-- 业务域:出入库
-- ######################################################################

-- ============================================================
-- 表:inbound_doc 入库单主表
-- 说明:入库单表实体(表 InboundDoc)。
-- ============================================================
CREATE TABLE public.inbound_doc (
    id           serial NOT NULL                                               ,  -- 主键ID
    doc_no       text NOT NULL                                                 ,  -- 单据号(RK-YYYYMMDD-NNNN,唯一)
    warehouse_id integer NOT NULL                                              ,  -- 仓库 ID
    status       text DEFAULT 'finished'::text NOT NULL                        ,  -- 单据状态:finished
    remark       text                                                          ,  -- 备注
    creator      text                                                          ,  -- 创建人
    created_at   timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,  -- 创建时间
    updater      text                                                          ,  -- 更新人
    updated_at   timestamp without time zone                                   ,  -- 更新时间
    ref_type     text                                                          ,  -- 关联单据类型(purchase=采购到货,空=手工入库)
    ref_doc_id   integer                                                       ,  -- 关联采购订单 ID
    doc_date     date                                                          ,  -- 单据日期
    carrier      text                                                          ,  -- 承运商
    vehicle_no   text                                                          ,  -- 车牌
    freight      numeric                                                       ,  -- 运费
    total_amount numeric(14,2)                                                 ,  -- 单据总金额(行金额合计,服务端落)
    doc_type     character varying(30)                                         ,  -- 单据类型(自由文本,如 采购入库/退货入库)
    handler      character varying(50)                                           -- 经办人
);

COMMENT ON TABLE public.inbound_doc IS '入库单表实体(表 InboundDoc)。';

COMMENT ON COLUMN public.inbound_doc.id IS '主键ID';
COMMENT ON COLUMN public.inbound_doc.doc_no IS '单据号(RK-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN public.inbound_doc.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN public.inbound_doc.status IS '单据状态:finished';
COMMENT ON COLUMN public.inbound_doc.remark IS '备注';
COMMENT ON COLUMN public.inbound_doc.creator IS '创建人';
COMMENT ON COLUMN public.inbound_doc.created_at IS '创建时间';
COMMENT ON COLUMN public.inbound_doc.updater IS '更新人';
COMMENT ON COLUMN public.inbound_doc.updated_at IS '更新时间';
COMMENT ON COLUMN public.inbound_doc.ref_type IS '关联单据类型(purchase=采购到货,空=手工入库)';
COMMENT ON COLUMN public.inbound_doc.ref_doc_id IS '关联采购订单 ID';
COMMENT ON COLUMN public.inbound_doc.doc_date IS '单据日期';
COMMENT ON COLUMN public.inbound_doc.carrier IS '承运商';
COMMENT ON COLUMN public.inbound_doc.vehicle_no IS '车牌';
COMMENT ON COLUMN public.inbound_doc.freight IS '运费';
COMMENT ON COLUMN public.inbound_doc.total_amount IS '单据总金额(行金额合计,服务端落)';
COMMENT ON COLUMN public.inbound_doc.doc_type IS '单据类型(自由文本,如 采购入库/退货入库)';
COMMENT ON COLUMN public.inbound_doc.handler IS '经办人';

ALTER TABLE ONLY public.inbound_doc ADD CONSTRAINT inbound_doc_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.inbound_doc ADD CONSTRAINT "InboundDoc_docNo_key" UNIQUE (doc_no);

ALTER TABLE ONLY public.inbound_doc ADD CONSTRAINT "InboundDoc_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);


-- ============================================================
-- 表:inbound_doc_item 入库单明细行
-- 说明:入库单行表实体(表 InboundDocItem)。
-- ============================================================
CREATE TABLE public.inbound_doc_item (
    id                  serial NOT NULL           ,  -- 主键ID
    doc_id              integer NOT NULL          ,  -- 单据 ID
    item_id             integer NOT NULL          ,  -- 物品 ID
    quantity            numeric(18,4) NOT NULL    ,  -- 数量
    batch_id            integer DEFAULT 0 NOT NULL,  -- 批次 ID(无批次为 0)
    location_id         integer DEFAULT 0 NOT NULL,  -- 库位 ID(无库位为 0)
    serial_nos          text                      ,  -- 序列号列表(JSON 字符串)
    unit_price          numeric(18,4)             ,  -- 入库不含税单价(采购到货携带订单行单价)
    tax_rate            numeric(5,2)              ,  -- 税率(百分数)
    batch_no            text                      ,  -- 批次号(新批次或已有批次)
    production_date     date                      ,  -- 生产日期(建批次用)
    expiry_date         date                      ,  -- 到期日期(建批次用)
    ref_line_id         integer                   ,  -- 关联采购订单行 ID
    line_no             integer                   ,  -- 行号(从 1 连号,服务端落)
    amount              numeric(14,2)             ,  -- 行金额快照(数量×不含税单价,服务端落)
    tax_amount          numeric(14,2)             ,  -- 行税额快照(金额×税率/100,服务端落)
    tax_inclusive_total numeric(14,2)             ,  -- 含税行金额快照(金额+税额,服务端落)
    tax_price           numeric(18,4)               -- 含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)
);

COMMENT ON TABLE public.inbound_doc_item IS '入库单行表实体(表 InboundDocItem)。';

COMMENT ON COLUMN public.inbound_doc_item.id IS '主键ID';
COMMENT ON COLUMN public.inbound_doc_item.doc_id IS '单据 ID';
COMMENT ON COLUMN public.inbound_doc_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.inbound_doc_item.quantity IS '数量';
COMMENT ON COLUMN public.inbound_doc_item.batch_id IS '批次 ID(无批次为 0)';
COMMENT ON COLUMN public.inbound_doc_item.location_id IS '库位 ID(无库位为 0)';
COMMENT ON COLUMN public.inbound_doc_item.serial_nos IS '序列号列表(JSON 字符串)';
COMMENT ON COLUMN public.inbound_doc_item.unit_price IS '入库不含税单价(采购到货携带订单行单价)';
COMMENT ON COLUMN public.inbound_doc_item.tax_rate IS '税率(百分数)';
COMMENT ON COLUMN public.inbound_doc_item.batch_no IS '批次号(新批次或已有批次)';
COMMENT ON COLUMN public.inbound_doc_item.production_date IS '生产日期(建批次用)';
COMMENT ON COLUMN public.inbound_doc_item.expiry_date IS '到期日期(建批次用)';
COMMENT ON COLUMN public.inbound_doc_item.ref_line_id IS '关联采购订单行 ID';
COMMENT ON COLUMN public.inbound_doc_item.line_no IS '行号(从 1 连号,服务端落)';
COMMENT ON COLUMN public.inbound_doc_item.amount IS '行金额快照(数量×不含税单价,服务端落)';
COMMENT ON COLUMN public.inbound_doc_item.tax_amount IS '行税额快照(金额×税率/100,服务端落)';
COMMENT ON COLUMN public.inbound_doc_item.tax_inclusive_total IS '含税行金额快照(金额+税额,服务端落)';
COMMENT ON COLUMN public.inbound_doc_item.tax_price IS '含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)';

ALTER TABLE ONLY public.inbound_doc_item ADD CONSTRAINT inbound_doc_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inbound_doc_item ADD CONSTRAINT "InboundDocItem_docId_fkey" FOREIGN KEY (doc_id) REFERENCES public.inbound_doc(id);


-- ============================================================
-- 表:outbound_doc 出库单主表
-- 说明:出库单表实体(表 OutboundDoc)。
-- ============================================================
CREATE TABLE public.outbound_doc (
    id           serial NOT NULL                                               ,  -- 主键ID
    doc_no       text NOT NULL                                                 ,  -- 单据号(CK-YYYYMMDD-NNNN,唯一)
    warehouse_id integer NOT NULL                                              ,  -- 仓库 ID
    status       text DEFAULT 'finished'::text NOT NULL                        ,  -- 单据状态:finished
    remark       text                                                          ,  -- 备注
    creator      text                                                          ,  -- 创建人
    created_at   timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,  -- 创建时间
    updater      text                                                          ,  -- 更新人
    updated_at   timestamp without time zone                                   ,  -- 更新时间
    ref_type     text                                                          ,  -- 关联单据类型(sales=销售发货,空=手工出库)
    ref_doc_id   integer                                                       ,  -- 关联销售订单 ID
    doc_date     date                                                          ,  -- 单据日期
    carrier      text                                                          ,  -- 承运商
    vehicle_no   text                                                          ,  -- 车牌
    freight      numeric                                                       ,  -- 运费
    total_amount numeric(14,2)                                                 ,  -- 单据总金额(行金额合计,服务端落)
    doc_type     character varying(30)                                         ,  -- 单据类型(自由文本,如 销售出库/领用出库)
    handler      character varying(50)                                           -- 经办人
);

COMMENT ON TABLE public.outbound_doc IS '出库单表实体(表 OutboundDoc)。';

COMMENT ON COLUMN public.outbound_doc.id IS '主键ID';
COMMENT ON COLUMN public.outbound_doc.doc_no IS '单据号(CK-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN public.outbound_doc.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN public.outbound_doc.status IS '单据状态:finished';
COMMENT ON COLUMN public.outbound_doc.remark IS '备注';
COMMENT ON COLUMN public.outbound_doc.creator IS '创建人';
COMMENT ON COLUMN public.outbound_doc.created_at IS '创建时间';
COMMENT ON COLUMN public.outbound_doc.updater IS '更新人';
COMMENT ON COLUMN public.outbound_doc.updated_at IS '更新时间';
COMMENT ON COLUMN public.outbound_doc.ref_type IS '关联单据类型(sales=销售发货,空=手工出库)';
COMMENT ON COLUMN public.outbound_doc.ref_doc_id IS '关联销售订单 ID';
COMMENT ON COLUMN public.outbound_doc.doc_date IS '单据日期';
COMMENT ON COLUMN public.outbound_doc.carrier IS '承运商';
COMMENT ON COLUMN public.outbound_doc.vehicle_no IS '车牌';
COMMENT ON COLUMN public.outbound_doc.freight IS '运费';
COMMENT ON COLUMN public.outbound_doc.total_amount IS '单据总金额(行金额合计,服务端落)';
COMMENT ON COLUMN public.outbound_doc.doc_type IS '单据类型(自由文本,如 销售出库/领用出库)';
COMMENT ON COLUMN public.outbound_doc.handler IS '经办人';

ALTER TABLE ONLY public.outbound_doc ADD CONSTRAINT outbound_doc_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.outbound_doc ADD CONSTRAINT "OutboundDoc_docNo_key" UNIQUE (doc_no);

ALTER TABLE ONLY public.outbound_doc ADD CONSTRAINT "OutboundDoc_warehouseId_fkey" FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);


-- ============================================================
-- 表:outbound_doc_item 出库单明细行
-- 说明:出库单行表实体(表 OutboundDocItem)。
-- ============================================================
CREATE TABLE public.outbound_doc_item (
    id                  serial NOT NULL           ,  -- 主键ID
    doc_id              integer NOT NULL          ,  -- 单据 ID
    item_id             integer NOT NULL          ,  -- 物品 ID
    quantity            numeric(18,4) NOT NULL    ,  -- 数量
    batch_id            integer DEFAULT 0 NOT NULL,  -- 批次 ID(无批次为 0)
    location_id         integer DEFAULT 0 NOT NULL,  -- 库位 ID(无库位为 0)
    serial_nos          text                      ,  -- 序列号列表(JSON 字符串)
    unit_price          numeric(18,4)             ,  -- 出库参考单价(销售发货携带订单行单价)
    ref_line_id         integer                   ,  -- 关联销售订单行 ID
    line_no             integer                   ,  -- 行号(从 1 连号,服务端落)
    tax_rate            numeric(6,4)              ,  -- 税率(百分数,空按 0 算)
    amount              numeric(14,2)             ,  -- 行金额快照(数量×不含税单价,服务端落)
    tax_amount          numeric(14,2)             ,  -- 行税额快照(金额×税率/100,服务端落)
    tax_inclusive_total numeric(14,2)             ,  -- 含税行金额快照(金额+税额,服务端落)
    tax_price           numeric(18,4)               -- 含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)
);

COMMENT ON TABLE public.outbound_doc_item IS '出库单行表实体(表 OutboundDocItem)。';

COMMENT ON COLUMN public.outbound_doc_item.id IS '主键ID';
COMMENT ON COLUMN public.outbound_doc_item.doc_id IS '单据 ID';
COMMENT ON COLUMN public.outbound_doc_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.outbound_doc_item.quantity IS '数量';
COMMENT ON COLUMN public.outbound_doc_item.batch_id IS '批次 ID(无批次为 0)';
COMMENT ON COLUMN public.outbound_doc_item.location_id IS '库位 ID(无库位为 0)';
COMMENT ON COLUMN public.outbound_doc_item.serial_nos IS '序列号列表(JSON 字符串)';
COMMENT ON COLUMN public.outbound_doc_item.unit_price IS '出库参考单价(销售发货携带订单行单价)';
COMMENT ON COLUMN public.outbound_doc_item.ref_line_id IS '关联销售订单行 ID';
COMMENT ON COLUMN public.outbound_doc_item.line_no IS '行号(从 1 连号,服务端落)';
COMMENT ON COLUMN public.outbound_doc_item.tax_rate IS '税率(百分数,空按 0 算)';
COMMENT ON COLUMN public.outbound_doc_item.amount IS '行金额快照(数量×不含税单价,服务端落)';
COMMENT ON COLUMN public.outbound_doc_item.tax_amount IS '行税额快照(金额×税率/100,服务端落)';
COMMENT ON COLUMN public.outbound_doc_item.tax_inclusive_total IS '含税行金额快照(金额+税额,服务端落)';
COMMENT ON COLUMN public.outbound_doc_item.tax_price IS '含税单价(V20,与 unit_price 二选一录入,服务端价税重算后落库)';

ALTER TABLE ONLY public.outbound_doc_item ADD CONSTRAINT outbound_doc_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.outbound_doc_item ADD CONSTRAINT "OutboundDocItem_docId_fkey" FOREIGN KEY (doc_id) REFERENCES public.outbound_doc(id);


-- ######################################################################
-- 业务域:调拨/盘点/调整
-- ######################################################################

-- ============================================================
-- 表:transfer_doc 调拨单主表
-- 说明:调拨单表头实体(表 TransferDoc)。
-- ============================================================
CREATE TABLE public.transfer_doc (
    id                serial NOT NULL                    ,  -- 主键ID
    doc_no            text NOT NULL                      ,  -- 单据编号(全局唯一)
    doc_date          date NOT NULL                      ,  -- 单据日期
    from_warehouse_id integer NOT NULL                   ,  -- 源仓库 ID
    to_warehouse_id   integer NOT NULL                   ,  -- 目的仓库 ID
    total_amount      numeric(18,4) DEFAULT 0 NOT NULL   ,  -- 成本参考合计
    status            text DEFAULT 'draft'::text NOT NULL,  -- 单据状态:draft/pending/approved/completed/closed/rejected/voided
    creator           text                               ,  -- 制单人
    created_at        timestamp without time zone        ,  -- 制单时间
    updater           text                               ,  -- 更新人
    updated_at        timestamp without time zone        ,  -- 更新时间
    approver          text                               ,  -- 审批人
    approved_at       timestamp without time zone        ,  -- 审批时间
    reject_reason     text                               ,  -- 驳回原因
    remark            text                               ,  -- 备注
    carrier           character varying(50)                -- 承运商
);

COMMENT ON TABLE public.transfer_doc IS '调拨单表头实体(表 TransferDoc)。';

COMMENT ON COLUMN public.transfer_doc.id IS '主键ID';
COMMENT ON COLUMN public.transfer_doc.doc_no IS '单据编号(全局唯一)';
COMMENT ON COLUMN public.transfer_doc.doc_date IS '单据日期';
COMMENT ON COLUMN public.transfer_doc.from_warehouse_id IS '源仓库 ID';
COMMENT ON COLUMN public.transfer_doc.to_warehouse_id IS '目的仓库 ID';
COMMENT ON COLUMN public.transfer_doc.total_amount IS '成本参考合计';
COMMENT ON COLUMN public.transfer_doc.status IS '单据状态:draft/pending/approved/completed/closed/rejected/voided';
COMMENT ON COLUMN public.transfer_doc.creator IS '制单人';
COMMENT ON COLUMN public.transfer_doc.created_at IS '制单时间';
COMMENT ON COLUMN public.transfer_doc.updater IS '更新人';
COMMENT ON COLUMN public.transfer_doc.updated_at IS '更新时间';
COMMENT ON COLUMN public.transfer_doc.approver IS '审批人';
COMMENT ON COLUMN public.transfer_doc.approved_at IS '审批时间';
COMMENT ON COLUMN public.transfer_doc.reject_reason IS '驳回原因';
COMMENT ON COLUMN public.transfer_doc.remark IS '备注';
COMMENT ON COLUMN public.transfer_doc.carrier IS '承运商';

ALTER TABLE ONLY public.transfer_doc ADD CONSTRAINT transfer_doc_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.transfer_doc ADD CONSTRAINT "TransferDoc_docNo_key" UNIQUE (doc_no);


-- ============================================================
-- 表:transfer_doc_item 调拨单明细行
-- 说明:调拨单行实体(表 TransferDocItem)。
-- ============================================================
CREATE TABLE public.transfer_doc_item (
    id               serial NOT NULL       ,  -- 主键ID
    doc_id           integer NOT NULL      ,  -- 调拨单 ID
    line_no          integer NOT NULL      ,  -- 行号
    item_id          integer NOT NULL      ,  -- 物品 ID
    spec_snapshot    text                  ,  -- 规格快照
    unit             text                  ,  -- 单位快照
    qty              numeric(18,4) NOT NULL,  -- 调拨数量
    unit_price       numeric(18,4) NOT NULL,  -- 成本参考单价
    from_location_id integer               ,  -- 源库位 ID
    to_location_id   integer               ,  -- 目的库位 ID
    line_remark      text                  ,  -- 行备注
    creator          character varying(64) ,  -- 创建人
    vehicle_no       text                    -- 车牌
);

COMMENT ON TABLE public.transfer_doc_item IS '调拨单行实体(表 TransferDocItem)。';

COMMENT ON COLUMN public.transfer_doc_item.id IS '主键ID';
COMMENT ON COLUMN public.transfer_doc_item.doc_id IS '调拨单 ID';
COMMENT ON COLUMN public.transfer_doc_item.line_no IS '行号';
COMMENT ON COLUMN public.transfer_doc_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.transfer_doc_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.transfer_doc_item.unit IS '单位快照';
COMMENT ON COLUMN public.transfer_doc_item.qty IS '调拨数量';
COMMENT ON COLUMN public.transfer_doc_item.unit_price IS '成本参考单价';
COMMENT ON COLUMN public.transfer_doc_item.from_location_id IS '源库位 ID';
COMMENT ON COLUMN public.transfer_doc_item.to_location_id IS '目的库位 ID';
COMMENT ON COLUMN public.transfer_doc_item.line_remark IS '行备注';
COMMENT ON COLUMN public.transfer_doc_item.creator IS '创建人';
COMMENT ON COLUMN public.transfer_doc_item.vehicle_no IS '车牌';

ALTER TABLE ONLY public.transfer_doc_item ADD CONSTRAINT transfer_doc_item_pkey PRIMARY KEY (id);


-- ============================================================
-- 表:stocktake_doc 盘点单主表
-- 说明:盘点单表头实体(表 StocktakeDoc,scopeType all=全仓 item=指定物品)。
-- ============================================================
CREATE TABLE public.stocktake_doc (
    id            serial NOT NULL                    ,  -- 主键ID
    doc_no        text NOT NULL                      ,  -- 单据编号(全局唯一)
    doc_date      date NOT NULL                      ,  -- 单据日期
    warehouse_id  integer NOT NULL                   ,  -- 仓库 ID
    scope_type    text DEFAULT 'all'::text NOT NULL  ,  -- 盘点范围:all 全仓 / item 指定物品
    status        text DEFAULT 'draft'::text NOT NULL,  -- 单据状态:draft/pending/approved/completed/closed/rejected/voided
    creator       text                               ,  -- 制单人
    created_at    timestamp without time zone        ,  -- 制单时间
    updater       text                               ,  -- 更新人
    updated_at    timestamp without time zone        ,  -- 更新时间
    approver      text                               ,  -- 审批人
    approved_at   timestamp without time zone        ,  -- 审批时间
    reject_reason text                               ,  -- 驳回原因
    remark        text                                 -- 备注
);

COMMENT ON TABLE public.stocktake_doc IS '盘点单表头实体(表 StocktakeDoc,scopeType all=全仓 item=指定物品)。';

COMMENT ON COLUMN public.stocktake_doc.id IS '主键ID';
COMMENT ON COLUMN public.stocktake_doc.doc_no IS '单据编号(全局唯一)';
COMMENT ON COLUMN public.stocktake_doc.doc_date IS '单据日期';
COMMENT ON COLUMN public.stocktake_doc.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN public.stocktake_doc.scope_type IS '盘点范围:all 全仓 / item 指定物品';
COMMENT ON COLUMN public.stocktake_doc.status IS '单据状态:draft/pending/approved/completed/closed/rejected/voided';
COMMENT ON COLUMN public.stocktake_doc.creator IS '制单人';
COMMENT ON COLUMN public.stocktake_doc.created_at IS '制单时间';
COMMENT ON COLUMN public.stocktake_doc.updater IS '更新人';
COMMENT ON COLUMN public.stocktake_doc.updated_at IS '更新时间';
COMMENT ON COLUMN public.stocktake_doc.approver IS '审批人';
COMMENT ON COLUMN public.stocktake_doc.approved_at IS '审批时间';
COMMENT ON COLUMN public.stocktake_doc.reject_reason IS '驳回原因';
COMMENT ON COLUMN public.stocktake_doc.remark IS '备注';

ALTER TABLE ONLY public.stocktake_doc ADD CONSTRAINT stocktake_doc_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.stocktake_doc ADD CONSTRAINT "StocktakeDoc_docNo_key" UNIQUE (doc_no);


-- ============================================================
-- 表:stocktake_doc_item 盘点单明细行
-- 说明:盘点单行实体(表 StocktakeDocItem,bookQty 为系统快照,actualQty 为空=未盘)。
-- ============================================================
CREATE TABLE public.stocktake_doc_item (
    id            serial NOT NULL           ,  -- 主键ID
    doc_id        integer NOT NULL          ,  -- 盘点单 ID
    line_no       integer NOT NULL          ,  -- 行号
    item_id       integer NOT NULL          ,  -- 物品 ID
    spec_snapshot text                      ,  -- 规格快照
    unit          text                      ,  -- 单位快照
    batch_id      integer DEFAULT 0 NOT NULL,  -- 批次 ID
    location_id   integer DEFAULT 0 NOT NULL,  -- 库位 ID
    book_qty      numeric(18,4) NOT NULL    ,  -- 账面快照数量
    actual_qty    numeric(18,4)             ,  -- 实盘数量(空=未盘)
    diff_qty      numeric(18,4)             ,  -- 差异数量
    creator       character varying(64)     ,  -- 创建人
    checker_name  text                      ,  -- 盘点人
    check_date    date                        -- 盘点日期
);

COMMENT ON TABLE public.stocktake_doc_item IS '盘点单行实体(表 StocktakeDocItem,bookQty 为系统快照,actualQty 为空=未盘)。';

COMMENT ON COLUMN public.stocktake_doc_item.id IS '主键ID';
COMMENT ON COLUMN public.stocktake_doc_item.doc_id IS '盘点单 ID';
COMMENT ON COLUMN public.stocktake_doc_item.line_no IS '行号';
COMMENT ON COLUMN public.stocktake_doc_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.stocktake_doc_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.stocktake_doc_item.unit IS '单位快照';
COMMENT ON COLUMN public.stocktake_doc_item.batch_id IS '批次 ID';
COMMENT ON COLUMN public.stocktake_doc_item.location_id IS '库位 ID';
COMMENT ON COLUMN public.stocktake_doc_item.book_qty IS '账面快照数量';
COMMENT ON COLUMN public.stocktake_doc_item.actual_qty IS '实盘数量(空=未盘)';
COMMENT ON COLUMN public.stocktake_doc_item.diff_qty IS '差异数量';
COMMENT ON COLUMN public.stocktake_doc_item.creator IS '创建人';
COMMENT ON COLUMN public.stocktake_doc_item.checker_name IS '盘点人';
COMMENT ON COLUMN public.stocktake_doc_item.check_date IS '盘点日期';

ALTER TABLE ONLY public.stocktake_doc_item ADD CONSTRAINT stocktake_doc_item_pkey PRIMARY KEY (id);


-- ============================================================
-- 表:stock_adjust_doc 库存调整单主表
-- 说明:库存调整单表头实体(表 StockAdjustDoc,adjustType gain/loss/scrap)。
-- ============================================================
CREATE TABLE public.stock_adjust_doc (
    id            serial NOT NULL                    ,  -- 主键ID
    doc_no        text NOT NULL                      ,  -- 单据编号(全局唯一)
    doc_date      date NOT NULL                      ,  -- 单据日期
    warehouse_id  integer NOT NULL                   ,  -- 仓库 ID
    adjust_type   text NOT NULL                      ,  -- 调整类型:gain 盘盈 / loss 盘亏 / scrap 报废
    ref_doc_no    text                               ,  -- 来源盘点单号
    status        text DEFAULT 'draft'::text NOT NULL,  -- 单据状态:draft/pending/approved/completed/closed/rejected/voided
    creator       text                               ,  -- 制单人
    created_at    timestamp without time zone        ,  -- 制单时间
    updater       text                               ,  -- 更新人
    updated_at    timestamp without time zone        ,  -- 更新时间
    approver      text                               ,  -- 审批人
    approved_at   timestamp without time zone        ,  -- 审批时间
    reject_reason text                               ,  -- 驳回原因
    remark        text                                 -- 备注
);

COMMENT ON TABLE public.stock_adjust_doc IS '库存调整单表头实体(表 StockAdjustDoc,adjustType gain/loss/scrap)。';

COMMENT ON COLUMN public.stock_adjust_doc.id IS '主键ID';
COMMENT ON COLUMN public.stock_adjust_doc.doc_no IS '单据编号(全局唯一)';
COMMENT ON COLUMN public.stock_adjust_doc.doc_date IS '单据日期';
COMMENT ON COLUMN public.stock_adjust_doc.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN public.stock_adjust_doc.adjust_type IS '调整类型:gain 盘盈 / loss 盘亏 / scrap 报废';
COMMENT ON COLUMN public.stock_adjust_doc.ref_doc_no IS '来源盘点单号';
COMMENT ON COLUMN public.stock_adjust_doc.status IS '单据状态:draft/pending/approved/completed/closed/rejected/voided';
COMMENT ON COLUMN public.stock_adjust_doc.creator IS '制单人';
COMMENT ON COLUMN public.stock_adjust_doc.created_at IS '制单时间';
COMMENT ON COLUMN public.stock_adjust_doc.updater IS '更新人';
COMMENT ON COLUMN public.stock_adjust_doc.updated_at IS '更新时间';
COMMENT ON COLUMN public.stock_adjust_doc.approver IS '审批人';
COMMENT ON COLUMN public.stock_adjust_doc.approved_at IS '审批时间';
COMMENT ON COLUMN public.stock_adjust_doc.reject_reason IS '驳回原因';
COMMENT ON COLUMN public.stock_adjust_doc.remark IS '备注';

ALTER TABLE ONLY public.stock_adjust_doc ADD CONSTRAINT stock_adjust_doc_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.stock_adjust_doc ADD CONSTRAINT "StockAdjustDoc_docNo_key" UNIQUE (doc_no);

CREATE UNIQUE INDEX uk_adjust_ref_doc_type ON public.stock_adjust_doc USING btree (ref_doc_no, adjust_type) WHERE ((ref_doc_no IS NOT NULL) AND (status <> 'voided'::text));


-- ============================================================
-- 表:stock_adjust_doc_item 库存调整单明细行
-- 说明:库存调整单行实体(表 StockAdjustDocItem,qty 为绝对值,方向由 adjustType 决定)。
-- ============================================================
CREATE TABLE public.stock_adjust_doc_item (
    id            serial NOT NULL           ,  -- 主键ID
    doc_id        integer NOT NULL          ,  -- 调整单 ID
    line_no       integer NOT NULL          ,  -- 行号
    item_id       integer NOT NULL          ,  -- 物品 ID
    spec_snapshot text                      ,  -- 规格快照
    unit          text                      ,  -- 单位快照
    batch_id      integer DEFAULT 0 NOT NULL,  -- 批次 ID
    location_id   integer DEFAULT 0 NOT NULL,  -- 库位 ID
    qty           numeric(18,4) NOT NULL    ,  -- 调整数量(绝对值,方向由 adjustType 决定)
    unit_price    numeric(18,4)             ,  -- 成本参考价
    reason        text                      ,  -- 原因
    creator       character varying(64)       -- 创建人
);

COMMENT ON TABLE public.stock_adjust_doc_item IS '库存调整单行实体(表 StockAdjustDocItem,qty 为绝对值,方向由 adjustType 决定)。';

COMMENT ON COLUMN public.stock_adjust_doc_item.id IS '主键ID';
COMMENT ON COLUMN public.stock_adjust_doc_item.doc_id IS '调整单 ID';
COMMENT ON COLUMN public.stock_adjust_doc_item.line_no IS '行号';
COMMENT ON COLUMN public.stock_adjust_doc_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.stock_adjust_doc_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.stock_adjust_doc_item.unit IS '单位快照';
COMMENT ON COLUMN public.stock_adjust_doc_item.batch_id IS '批次 ID';
COMMENT ON COLUMN public.stock_adjust_doc_item.location_id IS '库位 ID';
COMMENT ON COLUMN public.stock_adjust_doc_item.qty IS '调整数量(绝对值,方向由 adjustType 决定)';
COMMENT ON COLUMN public.stock_adjust_doc_item.unit_price IS '成本参考价';
COMMENT ON COLUMN public.stock_adjust_doc_item.reason IS '原因';
COMMENT ON COLUMN public.stock_adjust_doc_item.creator IS '创建人';

ALTER TABLE ONLY public.stock_adjust_doc_item ADD CONSTRAINT stock_adjust_doc_item_pkey PRIMARY KEY (id);


-- ######################################################################
-- 业务域:期初
-- ######################################################################

-- ============================================================
-- 表:opening_stock_doc 期初库存单主表
-- 说明:期初单头表实体(表 opening_stock_doc,create 即过账,status 恒 finished)。
-- ============================================================
CREATE TABLE public.opening_stock_doc (
    id           bigserial NOT NULL                                            ,  -- 主键ID
    doc_no       text NOT NULL                                                 ,  -- 单据号(QC-YYYYMMDD-NNNN,唯一)
    doc_date     date NOT NULL                                                 ,  -- 单据日期
    warehouse_id bigint NOT NULL                                               ,  -- 仓库 ID
    total_qty    numeric(18,4) DEFAULT 0 NOT NULL                              ,  -- 总数量(行数量合计,服务端落)
    remark       text                                                          ,  -- 备注
    status       character varying(20) NOT NULL                                ,  -- 单据状态:finished(create 即过账)
    creator      text                                                          ,  -- 创建人
    created_at   timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,  -- 创建时间
    updater      text                                                          ,  -- 更新人
    updated_at   timestamp without time zone                                     -- 更新时间
);

COMMENT ON TABLE public.opening_stock_doc IS '期初单头表实体(表 opening_stock_doc,create 即过账,status 恒 finished)。';

COMMENT ON COLUMN public.opening_stock_doc.id IS '主键ID';
COMMENT ON COLUMN public.opening_stock_doc.doc_no IS '单据号(QC-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN public.opening_stock_doc.doc_date IS '单据日期';
COMMENT ON COLUMN public.opening_stock_doc.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN public.opening_stock_doc.total_qty IS '总数量(行数量合计,服务端落)';
COMMENT ON COLUMN public.opening_stock_doc.remark IS '备注';
COMMENT ON COLUMN public.opening_stock_doc.status IS '单据状态:finished(create 即过账)';
COMMENT ON COLUMN public.opening_stock_doc.creator IS '创建人';
COMMENT ON COLUMN public.opening_stock_doc.created_at IS '创建时间';
COMMENT ON COLUMN public.opening_stock_doc.updater IS '更新人';
COMMENT ON COLUMN public.opening_stock_doc.updated_at IS '更新时间';

ALTER TABLE ONLY public.opening_stock_doc ADD CONSTRAINT opening_stock_doc_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.opening_stock_doc ADD CONSTRAINT uk_opening_stock_doc_doc_no UNIQUE (doc_no);

CREATE INDEX idx_opening_stock_doc_warehouse ON public.opening_stock_doc USING btree (warehouse_id);


-- ============================================================
-- 表:opening_stock_doc_item 期初库存单明细行
-- 说明:期初单行表实体(表 opening_stock_doc_item,数量/单价/批次等期初快照)。
-- ============================================================
CREATE TABLE public.opening_stock_doc_item (
    id              bigserial NOT NULL    ,  -- 主键ID
    doc_id          bigint NOT NULL       ,  -- 单据 ID
    line_no         integer NOT NULL      ,  -- 行号(从 1 连号,服务端落)
    item_id         bigint NOT NULL       ,  -- 物品 ID
    spec_snapshot   character varying(200),  -- 规格快照(取物品当前规格)
    unit            character varying(50) ,  -- 单位快照(取物品当前单位)
    quantity        numeric(18,4) NOT NULL,  -- 期初数量
    unit_price      numeric(14,4)         ,  -- 期初成本参考单价(仅快照,不涉及库存表)
    batch_no        character varying(50) ,  -- 批次号(批次/保质期仓必填)
    production_date date                  ,  -- 生产日期
    expiry_date     date                  ,  -- 保质期到期日
    location_id     bigint                ,  -- 库位 ID(库位仓必填)
    creator         text                    -- 创建人
);

COMMENT ON TABLE public.opening_stock_doc_item IS '期初单行表实体(表 opening_stock_doc_item,数量/单价/批次等期初快照)。';

COMMENT ON COLUMN public.opening_stock_doc_item.id IS '主键ID';
COMMENT ON COLUMN public.opening_stock_doc_item.doc_id IS '单据 ID';
COMMENT ON COLUMN public.opening_stock_doc_item.line_no IS '行号(从 1 连号,服务端落)';
COMMENT ON COLUMN public.opening_stock_doc_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.opening_stock_doc_item.spec_snapshot IS '规格快照(取物品当前规格)';
COMMENT ON COLUMN public.opening_stock_doc_item.unit IS '单位快照(取物品当前单位)';
COMMENT ON COLUMN public.opening_stock_doc_item.quantity IS '期初数量';
COMMENT ON COLUMN public.opening_stock_doc_item.unit_price IS '期初成本参考单价(仅快照,不涉及库存表)';
COMMENT ON COLUMN public.opening_stock_doc_item.batch_no IS '批次号(批次/保质期仓必填)';
COMMENT ON COLUMN public.opening_stock_doc_item.production_date IS '生产日期';
COMMENT ON COLUMN public.opening_stock_doc_item.expiry_date IS '保质期到期日';
COMMENT ON COLUMN public.opening_stock_doc_item.location_id IS '库位 ID(库位仓必填)';
COMMENT ON COLUMN public.opening_stock_doc_item.creator IS '创建人';

ALTER TABLE ONLY public.opening_stock_doc_item ADD CONSTRAINT opening_stock_doc_item_pkey PRIMARY KEY (id);

CREATE INDEX idx_opening_stock_doc_item_doc ON public.opening_stock_doc_item USING btree (doc_id);

ALTER TABLE ONLY public.opening_stock_doc_item ADD CONSTRAINT fk_opening_stock_doc_item_doc FOREIGN KEY (doc_id) REFERENCES public.opening_stock_doc(id);


-- ######################################################################
-- 业务域:结算域
-- ######################################################################

-- ============================================================
-- 表:invoice 发票头
-- 说明:发票头:采购票(invoice_type=purchase,对方=供应商)/销售票(sales,对方=客户);负票 sign=negative 为退货生成的红字凭单,确认后核减应付/应收;仅 confirmed 进台账。
-- ============================================================
CREATE TABLE public.invoice (
    id            bigserial NOT NULL                                                 ,  -- 主键ID
    doc_no        text NOT NULL                                                      ,  -- 发票号(FP-YYYYMMDD-NNNN,唯一)
    invoice_type  character varying(16) NOT NULL                                     ,  -- 发票类型:purchase 采购票 / sales 销售票
    party_id      integer NOT NULL                                                   ,  -- 对方 ID:采购票=供应商 ID,销售票=客户 ID
    invoice_date  date NOT NULL                                                      ,  -- 发票日期
    total_amount  numeric(14,2) NOT NULL                                             ,  -- 发票总额(行开票额合计,负票为负)
    status        character varying(16) NOT NULL                                     ,  -- 发票状态:draft 草稿(平账) / mismatch 差异(挂起,存在|差异|>0.01 的行) / confirmed 已确认(进台账) / voided 已作废(头表留痕,行释放源行占用)
    sign          character varying(8) DEFAULT 'positive'::character varying NOT NULL,  -- 正负号:positive 正票 / negative 负票(红字凭单,退货生成)
    source_type   character varying(16) DEFAULT 'manual'::character varying NOT NULL ,  -- 来源:manual 手工登记 / return_gen 退货过账自动生成
    ref_return_id integer                                                            ,  -- return_gen 时指退货单 ID(采购退货单/销售退货单)
    remark        text                                                               ,  -- 备注
    creator       text                                                               ,  -- 创建人
    created_at    timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL     ,  -- 创建时间
    updater       text                                                               ,  -- 更新人
    updated_at    timestamp without time zone                                          -- 更新时间
);

COMMENT ON TABLE public.invoice IS '发票头:采购票(invoice_type=purchase,对方=供应商)/销售票(sales,对方=客户);负票 sign=negative 为退货生成的红字凭单,确认后核减应付/应收;仅 confirmed 进台账。';

COMMENT ON COLUMN public.invoice.id IS '主键ID';
COMMENT ON COLUMN public.invoice.doc_no IS '发票号(FP-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN public.invoice.invoice_type IS '发票类型:purchase 采购票 / sales 销售票';
COMMENT ON COLUMN public.invoice.party_id IS '对方 ID:采购票=供应商 ID,销售票=客户 ID';
COMMENT ON COLUMN public.invoice.invoice_date IS '发票日期';
COMMENT ON COLUMN public.invoice.total_amount IS '发票总额(行开票额合计,负票为负)';
COMMENT ON COLUMN public.invoice.status IS '发票状态:draft 草稿(平账) / mismatch 差异(挂起,存在|差异|>0.01 的行) / confirmed 已确认(进台账) / voided 已作废(头表留痕,行释放源行占用)';
COMMENT ON COLUMN public.invoice.sign IS '正负号:positive 正票 / negative 负票(红字凭单,退货生成)';
COMMENT ON COLUMN public.invoice.source_type IS '来源:manual 手工登记 / return_gen 退货过账自动生成';
COMMENT ON COLUMN public.invoice.ref_return_id IS 'return_gen 时指退货单 ID(采购退货单/销售退货单)';
COMMENT ON COLUMN public.invoice.remark IS '备注';
COMMENT ON COLUMN public.invoice.creator IS '创建人';
COMMENT ON COLUMN public.invoice.created_at IS '创建时间';
COMMENT ON COLUMN public.invoice.updater IS '更新人';
COMMENT ON COLUMN public.invoice.updated_at IS '更新时间';

ALTER TABLE ONLY public.invoice ADD CONSTRAINT invoice_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.invoice ADD CONSTRAINT uk_invoice_doc_no UNIQUE (doc_no);

CREATE INDEX idx_invoice_date ON public.invoice USING btree (invoice_date);
CREATE INDEX idx_invoice_party ON public.invoice USING btree (party_id, invoice_type);
CREATE INDEX idx_invoice_status ON public.invoice USING btree (status);


-- ============================================================
-- 表:invoice_item 发票行
-- 说明:发票行:行级匹配挂源单据行;src=采购关联入库行/销售关联出库行(正票)或退货单行(负票);variance=开票额-源行含税额。
-- ============================================================
CREATE TABLE public.invoice_item (
    id              bigserial NOT NULL                                                 ,  -- 主键ID
    invoice_id      bigint NOT NULL                                                    ,  -- 发票 ID
    line_no         integer                                                            ,  -- 行号(从 1 连号)
    src_doc_type    character varying(16) NOT NULL                                     ,  -- 源单据类型:inbound 入库行 / outbound 出库行 / purchase_return 采购退货行 / sales_return 销售退货行
    src_doc_id      integer NOT NULL                                                   ,  -- 源单据 ID
    src_doc_item_id integer NOT NULL                                                   ,  -- 源单据行 ID
    item_id         integer NOT NULL                                                   ,  -- 物品 ID
    spec_snapshot   text                                                               ,  -- 规格快照
    unit            text                                                               ,  -- 单位
    quantity        numeric(18,4)                                                      ,  -- 数量(源行数量,正数;正负号由 sign/金额体现)
    invoiced_amount numeric(14,2) NOT NULL                                             ,  -- 开票额(负票为负数)
    src_amount      numeric(14,2)                                                      ,  -- 源单据行含税额快照(负票为负数)
    variance        numeric(14,2) DEFAULT 0 NOT NULL                                   ,  -- 差异额=开票额-源行含税额(负票两者均负);|差异|>0.01 时发票挂 mismatch
    sign            character varying(8) DEFAULT 'positive'::character varying NOT NULL,  -- 正负号(冗余头表,参与唯一约束防同一行同向重复挂票)
    batch_no        text                                                                 -- 批次号
);

COMMENT ON TABLE public.invoice_item IS '发票行:行级匹配挂源单据行;src=采购关联入库行/销售关联出库行(正票)或退货单行(负票);variance=开票额-源行含税额。';

COMMENT ON COLUMN public.invoice_item.id IS '主键ID';
COMMENT ON COLUMN public.invoice_item.invoice_id IS '发票 ID';
COMMENT ON COLUMN public.invoice_item.line_no IS '行号(从 1 连号)';
COMMENT ON COLUMN public.invoice_item.src_doc_type IS '源单据类型:inbound 入库行 / outbound 出库行 / purchase_return 采购退货行 / sales_return 销售退货行';
COMMENT ON COLUMN public.invoice_item.src_doc_id IS '源单据 ID';
COMMENT ON COLUMN public.invoice_item.src_doc_item_id IS '源单据行 ID';
COMMENT ON COLUMN public.invoice_item.item_id IS '物品 ID';
COMMENT ON COLUMN public.invoice_item.spec_snapshot IS '规格快照';
COMMENT ON COLUMN public.invoice_item.unit IS '单位';
COMMENT ON COLUMN public.invoice_item.quantity IS '数量(源行数量,正数;正负号由 sign/金额体现)';
COMMENT ON COLUMN public.invoice_item.invoiced_amount IS '开票额(负票为负数)';
COMMENT ON COLUMN public.invoice_item.src_amount IS '源单据行含税额快照(负票为负数)';
COMMENT ON COLUMN public.invoice_item.variance IS '差异额=开票额-源行含税额(负票两者均负);|差异|>0.01 时发票挂 mismatch';
COMMENT ON COLUMN public.invoice_item.sign IS '正负号(冗余头表,参与唯一约束防同一行同向重复挂票)';
COMMENT ON COLUMN public.invoice_item.batch_no IS '批次号';

ALTER TABLE ONLY public.invoice_item ADD CONSTRAINT invoice_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.invoice_item ADD CONSTRAINT uk_invoice_item_src UNIQUE (src_doc_type, src_doc_id, src_doc_item_id, sign);

CREATE INDEX idx_invoice_item_invoice ON public.invoice_item USING btree (invoice_id);
CREATE INDEX idx_invoice_item_src ON public.invoice_item USING btree (src_doc_type, src_doc_id, src_doc_item_id);

ALTER TABLE ONLY public.invoice_item ADD CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES public.invoice(id);


-- ============================================================
-- 表:payment_doc 付款/收款单主表
-- 说明:付款单(pay_type=payment,对方=供应商,前缀 FK-)/收款单(receipt,对方=客户,前缀 SK-);create 即生效,仅 confirmed/voided 两态。
-- ============================================================
CREATE TABLE public.payment_doc (
    id           bigserial NOT NULL                                                   ,  -- 主键ID
    doc_no       text NOT NULL                                                        ,  -- 单号(付款 FK-YYYYMMDD-NNNN / 收款 SK-YYYYMMDD-NNNN,唯一)
    pay_type     character varying(16) NOT NULL                                       ,  -- 单据类型:payment 付款 / receipt 收款
    party_id     integer NOT NULL                                                     ,  -- 对方 ID:付款=供应商 ID,收款=客户 ID
    pay_date     date                                                                 ,  -- 付款/收款日期
    total_amount numeric(14,2) NOT NULL                                               ,  -- 总额(核销行合计)
    status       character varying(16) DEFAULT 'confirmed'::character varying NOT NULL,  -- 状态:confirmed 已确认 / voided 已作废
    remark       text                                                                 ,  -- 备注
    creator      text                                                                 ,  -- 创建人
    created_at   timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL       ,  -- 创建时间
    updater      text                                                                 ,  -- 更新人
    updated_at   timestamp without time zone                                            -- 更新时间
);

COMMENT ON TABLE public.payment_doc IS '付款单(pay_type=payment,对方=供应商,前缀 FK-)/收款单(receipt,对方=客户,前缀 SK-);create 即生效,仅 confirmed/voided 两态。';

COMMENT ON COLUMN public.payment_doc.id IS '主键ID';
COMMENT ON COLUMN public.payment_doc.doc_no IS '单号(付款 FK-YYYYMMDD-NNNN / 收款 SK-YYYYMMDD-NNNN,唯一)';
COMMENT ON COLUMN public.payment_doc.pay_type IS '单据类型:payment 付款 / receipt 收款';
COMMENT ON COLUMN public.payment_doc.party_id IS '对方 ID:付款=供应商 ID,收款=客户 ID';
COMMENT ON COLUMN public.payment_doc.pay_date IS '付款/收款日期';
COMMENT ON COLUMN public.payment_doc.total_amount IS '总额(核销行合计)';
COMMENT ON COLUMN public.payment_doc.status IS '状态:confirmed 已确认 / voided 已作废';
COMMENT ON COLUMN public.payment_doc.remark IS '备注';
COMMENT ON COLUMN public.payment_doc.creator IS '创建人';
COMMENT ON COLUMN public.payment_doc.created_at IS '创建时间';
COMMENT ON COLUMN public.payment_doc.updater IS '更新人';
COMMENT ON COLUMN public.payment_doc.updated_at IS '更新时间';

ALTER TABLE ONLY public.payment_doc ADD CONSTRAINT payment_doc_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment_doc ADD CONSTRAINT uk_payment_doc_no UNIQUE (doc_no);

CREATE INDEX idx_payment_party ON public.payment_doc USING btree (pay_type, party_id);
CREATE INDEX idx_payment_status ON public.payment_doc USING btree (status);


-- ============================================================
-- 表:payment_line 核销行
-- 说明:核销行:一笔付款可核多张票,一票可被多笔付款分次部分核销;只允许挂 confirmed 正票,防超核由服务端硬校验(累计已核销+本次≤票额)。
-- ============================================================
CREATE TABLE public.payment_line (
    id         bigserial NOT NULL    ,  -- 主键ID
    payment_id bigint NOT NULL       ,  -- 付款/收款单 ID
    invoice_id bigint NOT NULL       ,  -- 发票 ID(仅正票)
    amount     numeric(14,2) NOT NULL  -- 核销额(正数)
);

COMMENT ON TABLE public.payment_line IS '核销行:一笔付款可核多张票,一票可被多笔付款分次部分核销;只允许挂 confirmed 正票,防超核由服务端硬校验(累计已核销+本次≤票额)。';

COMMENT ON COLUMN public.payment_line.id IS '主键ID';
COMMENT ON COLUMN public.payment_line.payment_id IS '付款/收款单 ID';
COMMENT ON COLUMN public.payment_line.invoice_id IS '发票 ID(仅正票)';
COMMENT ON COLUMN public.payment_line.amount IS '核销额(正数)';

ALTER TABLE ONLY public.payment_line ADD CONSTRAINT payment_line_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment_line ADD CONSTRAINT uk_payment_line UNIQUE (invoice_id, payment_id);

CREATE INDEX idx_payment_line_invoice ON public.payment_line USING btree (invoice_id);
CREATE INDEX idx_payment_line_payment ON public.payment_line USING btree (payment_id);

ALTER TABLE ONLY public.payment_line ADD CONSTRAINT fk_payment_line_invoice FOREIGN KEY (invoice_id) REFERENCES public.invoice(id);
ALTER TABLE ONLY public.payment_line ADD CONSTRAINT fk_payment_line_payment FOREIGN KEY (payment_id) REFERENCES public.payment_doc(id);


-- ######################################################################
-- 业务域:系统/RBAC
-- ######################################################################

-- ============================================================
-- 表:sys_user 用户
-- 说明:用户表实体(表 User)。
-- ============================================================
CREATE TABLE public.sys_user (
    id            serial NOT NULL                                               ,  -- 主键ID
    username      text NOT NULL                                                 ,  -- 用户名(唯一)
    password_hash text NOT NULL                                                 ,  -- 密码哈希(BCrypt)
    name          text NOT NULL                                                 ,  -- 姓名
    role          text NOT NULL                                                 ,  -- 角色:admin / operator / viewer(RBAC 改造后已弃用,仅存量兼容保留)
    status        integer DEFAULT 1 NOT NULL                                    ,  -- 状态:1 启用
    creator       character varying(64)                                         ,  -- 创建人
    created_at    timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,  -- 创建时间
    updater       character varying(64)                                         ,  -- 更新人
    updated_at    timestamp without time zone                                     -- 更新时间
);

COMMENT ON TABLE public.sys_user IS '用户表实体(表 User)。';

COMMENT ON COLUMN public.sys_user.id IS '主键ID';
COMMENT ON COLUMN public.sys_user.username IS '用户名(唯一)';
COMMENT ON COLUMN public.sys_user.password_hash IS '密码哈希(BCrypt)';
COMMENT ON COLUMN public.sys_user.name IS '姓名';
COMMENT ON COLUMN public.sys_user.role IS '角色:admin / operator / viewer(RBAC 改造后已弃用,仅存量兼容保留)';
COMMENT ON COLUMN public.sys_user.status IS '状态:1 启用';
COMMENT ON COLUMN public.sys_user.creator IS '创建人';
COMMENT ON COLUMN public.sys_user.created_at IS '创建时间';
COMMENT ON COLUMN public.sys_user.updater IS '更新人';
COMMENT ON COLUMN public.sys_user.updated_at IS '更新时间';

ALTER TABLE ONLY public.sys_user ADD CONSTRAINT sys_user_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_user ADD CONSTRAINT "User_username_key" UNIQUE (username);


-- ============================================================
-- 表:sys_role 角色
-- 说明:角色表实体(表 sys_role)。
-- ============================================================
CREATE TABLE public.sys_role (
    id         serial NOT NULL                                      ,  -- 主键ID
    role_code  text NOT NULL                                        ,  -- 角色编码(唯一,如 admin)
    role_name  text NOT NULL                                        ,  -- 角色名称
    remark     text                                                 ,  -- 备注
    is_builtin boolean DEFAULT false NOT NULL                       ,  -- 是否内置角色(禁删,禁改 role_code)
    status     integer DEFAULT 1 NOT NULL                           ,  -- 状态:1 启用
    creator    character varying(64)                                ,  -- 创建人
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
    updater    character varying(64)                                ,  -- 更新人
    updated_at timestamp without time zone                            -- 更新时间
);

COMMENT ON TABLE public.sys_role IS '角色表实体(表 sys_role)。';

COMMENT ON COLUMN public.sys_role.id IS '主键ID';
COMMENT ON COLUMN public.sys_role.role_code IS '角色编码(唯一,如 admin)';
COMMENT ON COLUMN public.sys_role.role_name IS '角色名称';
COMMENT ON COLUMN public.sys_role.remark IS '备注';
COMMENT ON COLUMN public.sys_role.is_builtin IS '是否内置角色(禁删,禁改 role_code)';
COMMENT ON COLUMN public.sys_role.status IS '状态:1 启用';
COMMENT ON COLUMN public.sys_role.creator IS '创建人';
COMMENT ON COLUMN public.sys_role.created_at IS '创建时间';
COMMENT ON COLUMN public.sys_role.updater IS '更新人';
COMMENT ON COLUMN public.sys_role.updated_at IS '更新时间';

ALTER TABLE ONLY public.sys_role ADD CONSTRAINT sys_role_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_role ADD CONSTRAINT uk_sys_role_code UNIQUE (role_code);


-- ============================================================
-- 表:sys_menu 菜单
-- 说明:菜单表实体(表 sys_menu,目录/菜单/按钮三型合一)。
-- ============================================================
CREATE TABLE public.sys_menu (
    id         serial NOT NULL                                      ,  -- 主键ID
    parent_id  integer DEFAULT 0 NOT NULL                           ,  -- 父节点 ID(0 表示顶级)
    menu_code  text NOT NULL                                        ,  -- 菜单编码(唯一,button 类型即权限码)
    menu_name  text NOT NULL                                        ,  -- 菜单名称
    type       text NOT NULL                                        ,  -- 类型:directory 目录 / menu 菜单 / button 按钮
    path       text                                                 ,  -- 前端路由(目录/菜单有,button 为空)
    sort       integer DEFAULT 0 NOT NULL                           ,  -- 排序值(越小越靠前)
    status     integer DEFAULT 1 NOT NULL                           ,  -- 状态:1 启用
    creator    character varying(64)                                ,  -- 创建人
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
    updater    character varying(64)                                ,  -- 更新人
    updated_at timestamp without time zone                            -- 更新时间
);

COMMENT ON TABLE public.sys_menu IS '菜单表实体(表 sys_menu,目录/菜单/按钮三型合一)。';

COMMENT ON COLUMN public.sys_menu.id IS '主键ID';
COMMENT ON COLUMN public.sys_menu.parent_id IS '父节点 ID(0 表示顶级)';
COMMENT ON COLUMN public.sys_menu.menu_code IS '菜单编码(唯一,button 类型即权限码)';
COMMENT ON COLUMN public.sys_menu.menu_name IS '菜单名称';
COMMENT ON COLUMN public.sys_menu.type IS '类型:directory 目录 / menu 菜单 / button 按钮';
COMMENT ON COLUMN public.sys_menu.path IS '前端路由(目录/菜单有,button 为空)';
COMMENT ON COLUMN public.sys_menu.sort IS '排序值(越小越靠前)';
COMMENT ON COLUMN public.sys_menu.status IS '状态:1 启用';
COMMENT ON COLUMN public.sys_menu.creator IS '创建人';
COMMENT ON COLUMN public.sys_menu.created_at IS '创建时间';
COMMENT ON COLUMN public.sys_menu.updater IS '更新人';
COMMENT ON COLUMN public.sys_menu.updated_at IS '更新时间';

ALTER TABLE ONLY public.sys_menu ADD CONSTRAINT sys_menu_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_menu ADD CONSTRAINT uk_sys_menu_code UNIQUE (menu_code);
ALTER TABLE ONLY public.sys_menu ADD CONSTRAINT ck_sys_menu_type CHECK ((type = ANY (ARRAY['directory'::text, 'menu'::text, 'button'::text])));

CREATE INDEX idx_sys_menu_parent ON public.sys_menu USING btree (parent_id);


-- ============================================================
-- 表:sys_role_menu 角色-菜单绑定
-- 说明:角色-菜单绑定实体(表 sys_role_menu,联合主键无审计字段)。
-- ============================================================
CREATE TABLE public.sys_role_menu (
    role_id integer NOT NULL,  -- 角色 ID
    menu_id integer NOT NULL  -- 菜单 ID
);

COMMENT ON TABLE public.sys_role_menu IS '角色-菜单绑定实体(表 sys_role_menu,联合主键无审计字段)。';

COMMENT ON COLUMN public.sys_role_menu.role_id IS '角色 ID';
COMMENT ON COLUMN public.sys_role_menu.menu_id IS '菜单 ID';

ALTER TABLE ONLY public.sys_role_menu ADD CONSTRAINT sys_role_menu_pkey PRIMARY KEY (role_id, menu_id);

ALTER TABLE ONLY public.sys_role_menu ADD CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (menu_id) REFERENCES public.sys_menu(id);
ALTER TABLE ONLY public.sys_role_menu ADD CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (role_id) REFERENCES public.sys_role(id);


-- ============================================================
-- 表:sys_user_role 用户-角色绑定
-- 说明:用户-角色绑定实体(表 sys_user_role,联合主键无审计字段,多角色权限取并集)。
-- ============================================================
CREATE TABLE public.sys_user_role (
    user_id integer NOT NULL,  -- 用户 ID
    role_id integer NOT NULL  -- 角色 ID
);

COMMENT ON TABLE public.sys_user_role IS '用户-角色绑定实体(表 sys_user_role,联合主键无审计字段,多角色权限取并集)。';

COMMENT ON COLUMN public.sys_user_role.user_id IS '用户 ID';
COMMENT ON COLUMN public.sys_user_role.role_id IS '角色 ID';

ALTER TABLE ONLY public.sys_user_role ADD CONSTRAINT sys_user_role_pkey PRIMARY KEY (user_id, role_id);

ALTER TABLE ONLY public.sys_user_role ADD CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES public.sys_role(id);
ALTER TABLE ONLY public.sys_user_role ADD CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


-- ============================================================
-- 表:sys_user_warehouse 用户-仓库授权
-- 说明:用户-仓库授权实体(表 sys_user_warehouse,数据权限,admin 豁免)。
-- ============================================================
CREATE TABLE public.sys_user_warehouse (
    user_id      integer NOT NULL,  -- 用户 ID
    warehouse_id integer NOT NULL  -- 仓库 ID
);

COMMENT ON TABLE public.sys_user_warehouse IS '用户-仓库授权实体(表 sys_user_warehouse,数据权限,admin 豁免)。';

COMMENT ON COLUMN public.sys_user_warehouse.user_id IS '用户 ID';
COMMENT ON COLUMN public.sys_user_warehouse.warehouse_id IS '仓库 ID';

ALTER TABLE ONLY public.sys_user_warehouse ADD CONSTRAINT sys_user_warehouse_pkey PRIMARY KEY (user_id, warehouse_id);

ALTER TABLE ONLY public.sys_user_warehouse ADD CONSTRAINT fk_sys_user_warehouse_user FOREIGN KEY (user_id) REFERENCES public.sys_user(id);
ALTER TABLE ONLY public.sys_user_warehouse ADD CONSTRAINT fk_sys_user_warehouse_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.warehouse(id);


-- ============================================================
-- 表:operation_log 操作日志
-- 说明:操作日志:全部写操作(POST/PUT/DELETE)执行流水,由切面记录,日志功能故障不影响业务主流程。
-- ============================================================
CREATE TABLE public.operation_log (
    id          bigserial NOT NULL                                ,  -- 主键ID(BIGSERIAL 自增,纯日志表量大)
    username    character varying(64) NOT NULL                    ,  -- 操作用户名
    ip          character varying(64)                             ,  -- 请求 IP(X-Forwarded-For 首段或 remoteAddr)
    module      character varying(64) NOT NULL                    ,  -- 模块中文名(由 Controller 类名映射)
    action      character varying(32) NOT NULL                    ,  -- HTTP 方法:POST / PUT / DELETE
    path        character varying(255) NOT NULL                   ,  -- 请求路径(如 /api/v1/items)
    target_type character varying(64)                             ,  -- 操作对象类型中文名
    target_id   bigint                                            ,  -- 操作对象 ID(从路径变量取)
    target_no   character varying(64)                             ,  -- 操作对象单号/编码
    success     smallint NOT NULL                                 ,  -- 结果:1 成功 0 失败
    error_msg   character varying(500)                            ,  -- 失败时的异常消息(截断 500 字符)
    cost_ms     integer                                           ,  -- 接口耗时毫秒
    created_at  timestamp without time zone DEFAULT now() NOT NULL  -- 创建时间
);

COMMENT ON TABLE public.operation_log IS '操作日志:全部写操作(POST/PUT/DELETE)执行流水,由切面记录,日志功能故障不影响业务主流程。';

COMMENT ON COLUMN public.operation_log.id IS '主键ID(BIGSERIAL 自增,纯日志表量大)';
COMMENT ON COLUMN public.operation_log.username IS '操作用户名';
COMMENT ON COLUMN public.operation_log.ip IS '请求 IP(X-Forwarded-For 首段或 remoteAddr)';
COMMENT ON COLUMN public.operation_log.module IS '模块中文名(由 Controller 类名映射)';
COMMENT ON COLUMN public.operation_log.action IS 'HTTP 方法:POST / PUT / DELETE';
COMMENT ON COLUMN public.operation_log.path IS '请求路径(如 /api/v1/items)';
COMMENT ON COLUMN public.operation_log.target_type IS '操作对象类型中文名';
COMMENT ON COLUMN public.operation_log.target_id IS '操作对象 ID(从路径变量取)';
COMMENT ON COLUMN public.operation_log.target_no IS '操作对象单号/编码';
COMMENT ON COLUMN public.operation_log.success IS '结果:1 成功 0 失败';
COMMENT ON COLUMN public.operation_log.error_msg IS '失败时的异常消息(截断 500 字符)';
COMMENT ON COLUMN public.operation_log.cost_ms IS '接口耗时毫秒';
COMMENT ON COLUMN public.operation_log.created_at IS '创建时间';

ALTER TABLE ONLY public.operation_log ADD CONSTRAINT operation_log_pkey PRIMARY KEY (id);

CREATE INDEX idx_operation_log_created_at ON public.operation_log USING btree (created_at);
CREATE INDEX idx_operation_log_module_ct ON public.operation_log USING btree (module, created_at);
CREATE INDEX idx_operation_log_username ON public.operation_log USING btree (username);


-- ######################################################################
-- 业务域:字典
-- ######################################################################

-- ============================================================
-- 表:dict_type 字典类型
-- 说明:字典类型表实体(表 DictType):管理字典分类(仓库类型/物品分类等)。
-- ============================================================
CREATE TABLE public.dict_type (
    id         bigserial NOT NULL                                  ,  -- 主键ID
    type_code  character varying(64) NOT NULL                      ,  -- 类型编码(唯一,不可改)
    type_name  character varying(64) NOT NULL                      ,  -- 类型名称
    remark     character varying(255) DEFAULT ''::character varying,  -- 备注
    status     smallint DEFAULT 1 NOT NULL                         ,  -- 状态:1 启用 / 0 停用
    created_at timestamp without time zone DEFAULT now() NOT NULL  ,  -- 创建时间
    updated_at timestamp without time zone DEFAULT now() NOT NULL  ,  -- 更新时间
    creator    character varying(64)                               ,  -- 创建人
    updater    character varying(64)                                 -- 更新人
);

COMMENT ON TABLE public.dict_type IS '字典类型表实体(表 DictType):管理字典分类(仓库类型/物品分类等)。';

COMMENT ON COLUMN public.dict_type.id IS '主键ID';
COMMENT ON COLUMN public.dict_type.type_code IS '类型编码(唯一,不可改)';
COMMENT ON COLUMN public.dict_type.type_name IS '类型名称';
COMMENT ON COLUMN public.dict_type.remark IS '备注';
COMMENT ON COLUMN public.dict_type.status IS '状态:1 启用 / 0 停用';
COMMENT ON COLUMN public.dict_type.created_at IS '创建时间';
COMMENT ON COLUMN public.dict_type.updated_at IS '更新时间';
COMMENT ON COLUMN public.dict_type.creator IS '创建人';
COMMENT ON COLUMN public.dict_type.updater IS '更新人';

ALTER TABLE ONLY public.dict_type ADD CONSTRAINT dict_type_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.dict_type ADD CONSTRAINT uk_dict_type_code UNIQUE (type_code);


-- ============================================================
-- 表:dict 字典项
-- 说明:字典表实体(表 Dict):主数据下拉枚举(仓库类型/物品分类/结算方式等);状态机枚举(单据 status/bizCode/adjustType)不进字典,保持常量类。
-- ============================================================
CREATE TABLE public.dict (
    id         serial NOT NULL                                      ,  -- 主键ID
    dict_type  text NOT NULL                                        ,  -- 字典类型:warehouseType / itemCategory / settleMethod
    dict_key   text NOT NULL                                        ,  -- 字典键值(下拉 value,如 raw/finished)
    dict_label text NOT NULL                                        ,  -- 字典标签(下拉显示文案,如 原材料仓)
    sort_order integer DEFAULT 0 NOT NULL                           ,  -- 排序号(升序)
    status     integer DEFAULT 1 NOT NULL                           ,  -- 状态:1 启用 / 0 停用
    creator    character varying(64)                                ,  -- 创建人
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,  -- 创建时间
    updater    character varying(64)                                ,  -- 更新人
    updated_at timestamp without time zone                            -- 更新时间
);

COMMENT ON TABLE public.dict IS '字典表实体(表 Dict):主数据下拉枚举(仓库类型/物品分类/结算方式等);状态机枚举(单据 status/bizCode/adjustType)不进字典,保持常量类。';

COMMENT ON COLUMN public.dict.id IS '主键ID';
COMMENT ON COLUMN public.dict.dict_type IS '字典类型:warehouseType / itemCategory / settleMethod';
COMMENT ON COLUMN public.dict.dict_key IS '字典键值(下拉 value,如 raw/finished)';
COMMENT ON COLUMN public.dict.dict_label IS '字典标签(下拉显示文案,如 原材料仓)';
COMMENT ON COLUMN public.dict.sort_order IS '排序号(升序)';
COMMENT ON COLUMN public.dict.status IS '状态:1 启用 / 0 停用';
COMMENT ON COLUMN public.dict.creator IS '创建人';
COMMENT ON COLUMN public.dict.created_at IS '创建时间';
COMMENT ON COLUMN public.dict.updater IS '更新人';
COMMENT ON COLUMN public.dict.updated_at IS '更新时间';

ALTER TABLE ONLY public.dict ADD CONSTRAINT dict_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.dict ADD CONSTRAINT "Dict_type_key_unique" UNIQUE (dict_type, dict_key);


-- ============================================================
-- schema.sql end
-- ============================================================
