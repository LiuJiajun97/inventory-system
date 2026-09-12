// 新建销售订单(一期新增)
// 服务端重算价税三列,前端仅展示输入;行级:物品/数量/单价/税率
// 布局与采购页统一:页面头部 + 单卡片(ProForm 表头 + 明细表 + 底部固定操作条)
// 行明细保留原 antd Table 手工编辑机制(物品→默认税率联动在 onItemChange,不改)

import { useEffect, useState } from "react";
import {
  Button,
  DatePicker,
  Input,
  InputNumber,
  message,
  Select,
  Table,
} from "antd";
import {
  ProCard,
  ProForm,
  ProFormDatePicker,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from "@ant-design/pro-components";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { Link, useNavigate, useParams } from "react-router-dom";
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, salesApi, customerApi, userApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { Item, UserInfo } from "../../types";
import type { Customer } from "../../types/phase1";

interface LineRow {
  key: number;
  itemId?: number;
  orderedQty?: number;
  customerDeliveryDate?: Dayjs;
  unitPrice?: number;
  taxRate?: number;
  lineRemark?: string;
}

let lineSeq = 1;

export function SalesOrderNewPage() {
  const navigate = useNavigate();
  const { id: editIdParam } = useParams<{ id?: string }>();
  // 路由 /sales-orders/new/:id? 携带 id 时为编辑模式(草稿/已驳回单)
  const editId = editIdParam ? Number(editIdParam) : null;
  const [docNo, setDocNo] = useState("");
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [lines, setLines] = useState<LineRow[]>([{ key: lineSeq++ }]);
  const [saving, setSaving] = useState(false);
  const [form] = ProForm.useForm();

  useEffect(() => {
    customerApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setCustomers(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
    userApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setUsers(r.rows))
      .catch(() => undefined);
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 编辑模式:GET 详情回填表头 + 行明细(行 key 用 1..n,与新建的自增 key 规则一致)
  useEffect(() => {
    if (editId == null) return;
    salesApi
      .get(editId)
      .then((doc) => {
        setDocNo(doc.docNo);
        form.setFieldsValue({
          docDate: dayjs(doc.docDate),
          customerId: doc.customerId,
          salespersonId: doc.salespersonId,
          warehouseId: doc.warehouseId,
          contractNo: doc.contractNo ?? undefined,
          freight: doc.freight == null ? undefined : Number(doc.freight),
          shippingAddress: doc.shippingAddress ?? undefined,
          // V10 通用字段(可空):币种/汇率/折扣额
          currencyCode: doc.currencyCode ?? undefined,
          exchangeRate: doc.exchangeRate == null ? undefined : Number(doc.exchangeRate),
          discountAmount: doc.discountAmount == null ? undefined : Number(doc.discountAmount),
          remark: doc.remark ?? undefined,
        });
        const rows: LineRow[] = (doc.items ?? []).map((l, i) => ({
          key: i + 1,
          itemId: l.itemId,
          orderedQty: Number(l.orderedQty),
          customerDeliveryDate: l.customerDeliveryDate ? dayjs(l.customerDeliveryDate) : undefined,
          unitPrice: Number(l.unitPrice),
          taxRate: Number(l.taxRate),
          lineRemark: l.lineRemark ?? undefined,
        }));
        setLines(rows);
        // 防止新建时"添加行"自增 key 与回填行 key 冲突
        lineSeq = Math.max(lineSeq, rows.length + 1);
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId]);

  const itemOptions = items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }));

  const onItemChange = (key: number, itemId: number) => {
    const it = items.find((x) => x.id === itemId);
    setLines((ls) =>
      ls.map((l) => {
        if (l.key !== key) return l;
        // V10:选物品且单价未填且物品有参考销售价 → 预填(仅预填,用户可改)
        const unitPrice =
          l.unitPrice == null && it?.referenceSalePrice != null
            ? Number(it.referenceSalePrice)
            : l.unitPrice;
        return { ...l, itemId, taxRate: it ? Number(it.defaultTaxRate ?? 13) : l.taxRate, unitPrice };
      }),
    );
  };

  const onSubmit = async () => {
    const v = await form.validateFields();
    const validLines = lines.filter((l) => l.itemId && l.orderedQty && l.unitPrice != null);
    if (validLines.length === 0) {
      message.error("请至少填写一行订单明细");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        docDate: v.docDate.format("YYYY-MM-DD"),
        customerId: v.customerId,
        salespersonId: v.salespersonId,
        warehouseId: v.warehouseId,
        contractNo: v.contractNo,
        freight: v.freight,
        shippingAddress: v.shippingAddress,
        // V10 通用字段(可空)
        currencyCode: v.currencyCode,
        exchangeRate: v.exchangeRate,
        discountAmount: v.discountAmount,
        remark: v.remark,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          orderedQty: l.orderedQty!,
          customerDeliveryDate: l.customerDeliveryDate?.format("YYYY-MM-DD"),
          unitPrice: l.unitPrice!,
          taxRate: l.taxRate ?? 13,
          lineRemark: l.lineRemark,
        })),
      };
      if (editId != null) {
        await salesApi.update(editId, payload);
        message.success("销售订单已保存(仍为可编辑状态)");
      } else {
        await salesApi.create(payload);
        message.success("销售订单已创建(草稿)");
      }
      navigate("/sales-orders");
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const lineColumns = [
    {
      title: "物品",
      width: 240,
      render: (_v: unknown, l: LineRow) => (
        <Select
          showSearch
          placeholder="选择物品"
          optionFilterProp="label"
          style={{ width: "100%" }}
          options={itemOptions}
          value={l.itemId}
          onChange={(v) => onItemChange(l.key, v)}
        />
      ),
    },
    {
      title: "订购数量",
      width: 120,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0.0001}
          step={1}
          style={{ width: "100%" }}
          value={l.orderedQty}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, orderedQty: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "要求交付日",
      width: 160,
      render: (_v: unknown, l: LineRow) => (
        <DatePicker
          style={{ width: "100%" }}
          value={l.customerDeliveryDate}
          onChange={(d) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, customerDeliveryDate: d ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "不含税单价",
      width: 120,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0}
          step={0.01}
          style={{ width: "100%" }}
          value={l.unitPrice}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, unitPrice: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "税率(%)",
      width: 110,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0}
          max={100}
          step={0.01}
          style={{ width: "100%" }}
          value={l.taxRate}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, taxRate: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "行备注",
      render: (_v: unknown, l: LineRow) => (
        <Input
          value={l.lineRemark}
          onChange={(e) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, lineRemark: e.target.value } : x)))
          }
        />
      ),
    },
    {
      title: "",
      width: 60,
      render: (_v: unknown, l: LineRow) => (
        <a
          onClick={() => setLines((ls) => (ls.length > 1 ? ls.filter((x) => x.key !== l.key) : ls))}
        >
          删除
        </a>
      ),
    },
  ];

  return (
    <>
      {/* 页面头部:返回 + 标题 + 编辑态单号摘要 */}
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/sales-orders">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">
          {editId != null ? "编辑销售订单" : "新建销售订单"}
        </h1>
        {editId != null && <span className="doc-page-meta">单号 {docNo || "..."}</span>}
      </div>

      {/* 单卡片布局:表头 + 明细 + 底部固定操作条 */}
      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
          {/* 表头:统一 grid + md 宽度,四列对齐 */}
          <ProForm form={form} layout="vertical" grid submitter={false}>
            <ProFormDatePicker
              name="docDate"
              label="开单日期"
              colProps={{ span: 6 }}
              initialValue={dayjs()}
              rules={[{ required: true, message: "请选择开单日期" }]}
            />
            <ProFormSelect
              name="customerId"
              label="客户"
              colProps={{ span: 6 }}
              showSearch
              placeholder="选择客户"
              options={customers.map((s) => ({
                label: `${s.customerCode} ${s.customerName}`,
                value: s.id,
              }))}
              fieldProps={{ optionFilterProp: "label" }}
              rules={[{ required: true, message: "请选择客户" }]}
            />
            <ProFormSelect
              name="salespersonId"
              label="销售员"
              colProps={{ span: 6 }}
              placeholder="选择销售员"
              options={users.map((u) => ({ label: u.name, value: u.id }))}
              rules={[{ required: true, message: "请选择销售员" }]}
            />
            <ProFormSelect
              name="warehouseId"
              label="发货仓库"
              colProps={{ span: 6 }}
              placeholder="选择发货仓库"
              options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
              rules={[{ required: true, message: "请选择发货仓库" }]}
            />
            {/* V9 通用字段(可空):合同号/运费/交货地址 */}
            <ProFormText
              name="contractNo"
              label="合同号"
              colProps={{ span: 6 }}
            />
            <ProFormDigit
              name="freight"
              label="运费"
              colProps={{ span: 6 }}
              min={0}
              fieldProps={{ step: 0.01 }}
            />
            <ProFormText
              name="shippingAddress"
              label="交货地址"
              colProps={{ span: 12 }}
            />
            {/* V10 通用字段(可空):币种(默认 CNY)/汇率(默认 1)/折扣额 */}
            <ProFormText
              name="currencyCode"
              label="币种"
              colProps={{ span: 6 }}
              initialValue="CNY"
              fieldProps={{ placeholder: "默认 CNY" }}
            />
            <ProFormDigit
              name="exchangeRate"
              label="汇率"
              colProps={{ span: 6 }}
              initialValue={1}
              min={0}
              fieldProps={{ step: 0.000001 }}
            />
            <ProFormDigit
              name="discountAmount"
              label="折扣额"
              colProps={{ span: 6 }}
              min={0}
              fieldProps={{ step: 0.01 }}
              tooltip="仅存字段,不参与合计计算"
            />
            <ProFormTextArea
              name="remark"
              label="备注"
              colProps={{ span: 24 }}
              fieldProps={{ rows: 2 }}
            />
          </ProForm>

          <div className="doc-form-section-title">订单明细</div>
          {/* 明细保留原手工编辑 Table(物品→税率联动走 onItemChange) */}
          <Table
            rowKey="key"
            size="small"
            dataSource={lines}
            pagination={false}
            columns={lineColumns}
            scroll={{ x: 900, y: "calc(100vh - 520px)" }}
          />
          {/* 添加行按钮:明细表正下方,左对齐 */}
          <div className="doc-form-line-adder">
            <Button onClick={() => setLines((ls) => [...ls, { key: lineSeq++ }])}>添加行</Button>
          </div>
        </div>
        {/* 底部固定操作条:左摘要,中间主按钮居中 */}
        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {lines.length} 行明细
              <span className="doc-form-footer-muted">金额以服务端价税重算为准</span>
            </div>
          </div>
          <div className="doc-form-footer-main">
            <Button type="primary" loading={saving} onClick={onSubmit}>
              {editId != null ? "保存" : "保存为草稿"}
            </Button>
          </div>
        </div>
      </ProCard>
    </>
  );
}
