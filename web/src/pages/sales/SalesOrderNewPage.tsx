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
  Tooltip,
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
import { ArrowLeftOutlined, ExclamationCircleFilled } from "@ant-design/icons";
import { Link, useNavigate, useParams } from "react-router-dom";
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, salesApi, customerApi, userApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { Item, UserInfo } from "../../types";
import type { Customer } from "../../types/phase1";
import { fmtMoney } from "../../utils/format";
import { priceMismatchHint, previewLineMoney, recalcPricePair } from "../../utils/lineMoney";

interface LineRow {
  key: number;
  itemId?: number;
  orderedQty?: number;
  customerDeliveryDate?: Dayjs;
  unitPrice?: number;
  // V20 含税单价:与不含税单价二选一,都填时服务端按不含税优先
  taxPrice?: number;
  taxRate?: number;
  lineRemark?: string;
}

let lineSeq = 1;

// 可编辑状态:与后端 PUT 口径一致(仅草稿/已驳回可保存,其余终态只读)
function isEditable(status: string): boolean {
  return status === "draft" || status === "rejected";
}

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
  // 编辑单据状态:非 draft/rejected(后端 PUT 拒绝态)为终态只读
  const [docStatus, setDocStatus] = useState<string | undefined>();
  const readonly = editId != null && docStatus != null && !isEditable(docStatus);
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
        setDocStatus(doc.status);
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
          taxPrice: l.taxPrice != null ? Number(l.taxPrice) : undefined,
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
        // V23.1:预填了不含税 → 按带出的税率联动含税(与手动填不含税同口径)
        const taxRate = it ? Number(it.defaultTaxRate ?? 13) : l.taxRate;
        const taxPrice =
          unitPrice != null
            ? recalcPricePair(unitPrice, l.taxPrice, taxRate, "unit").taxPrice ?? l.taxPrice
            : l.taxPrice;
        return { ...l, itemId, taxRate, unitPrice, taxPrice };
      }),
    );
  };

  const onSubmit = async () => {
    const v = await form.validateFields();
    // V20:不含税单价/含税单价至少填一个(都填时服务端按不含税优先)
    const validLines = lines.filter(
      (l) => l.itemId && l.orderedQty && (l.unitPrice != null || l.taxPrice != null),
    );
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
          unitPrice: l.unitPrice ?? undefined,
          taxPrice: l.taxPrice ?? undefined,
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
          disabled={readonly}
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
          disabled={readonly}
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
          disabled={readonly}
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
          disabled={readonly}
          onChange={(v) => {
            // V23.1:改不含税 → 实时联动重算含税(清空不触发)
            const next = recalcPricePair(v ?? undefined, l.taxPrice, l.taxRate, "unit");
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, unitPrice: v ?? undefined, ...next } : x)));
          }}
        />
      ),
    },
    {
      // V20 含税单价:与不含税单价二选一(都填时服务端按不含税优先)
      // V23.1:两单价按税率推算不一致时输入框右侧同行加小图标 + Tooltip 悬浮展示完整文案(纯展示,不阻断提交)
      title: "含税单价",
      width: 120,
      render: (_v: unknown, l: LineRow) => {
        const hint = priceMismatchHint(l.unitPrice, l.taxPrice, l.taxRate);
        return (
          <span style={{ display: "inline-flex", alignItems: "center", width: "100%" }}>
            <InputNumber
              min={0}
              step={0.01}
              style={{ width: "calc(100% - 22px)" }}
              value={l.taxPrice}
              disabled={readonly}
              onChange={(v) => {
                // V23.1:改含税 → 实时联动重算不含税(清空不触发)
                const next = recalcPricePair(l.unitPrice, v ?? undefined, l.taxRate, "tax");
                setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, taxPrice: v ?? undefined, ...next } : x)));
              }}
            />
            {hint != null ? (
              <Tooltip title={hint}>
                <ExclamationCircleFilled
                  style={{ color: "#fa8c16", fontSize: 14, marginLeft: 6, cursor: "help" }}
                />
              </Tooltip>
            ) : null}
          </span>
        );
      },
    },
    {
      // V20 金额三列:只读预览(与后端同口径),提交后以服务端重算为准
      title: "不含税金额",
      width: 110,
      align: "right" as const,
      className: "num-cell",
      render: (_v: unknown, l: LineRow) => {
        const pm = previewLineMoney(l.orderedQty ?? 0, l.unitPrice, l.taxPrice, l.taxRate);
        return pm ? fmtMoney(pm.amount) : "-";
      },
    },
    {
      title: "税额",
      width: 100,
      align: "right" as const,
      className: "num-cell",
      render: (_v: unknown, l: LineRow) => {
        const pm = previewLineMoney(l.orderedQty ?? 0, l.unitPrice, l.taxPrice, l.taxRate);
        return pm ? fmtMoney(pm.tax) : "-";
      },
    },
    {
      title: "含税金额",
      width: 110,
      align: "right" as const,
      className: "num-cell",
      render: (_v: unknown, l: LineRow) => {
        const pm = previewLineMoney(l.orderedQty ?? 0, l.unitPrice, l.taxPrice, l.taxRate);
        return pm ? fmtMoney(pm.inclusive) : "-";
      },
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
          disabled={readonly}
          onChange={(v) => {
            // V23.1:改税率 → 按不含税正算联动含税(与后端同口径),只填了含税则反算
            const next = v != null ? recalcPricePair(l.unitPrice, l.taxPrice, v, "rate") : {};
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, taxRate: v ?? undefined, ...next } : x)));
          }}
          />
      ),
    },
    {
      title: "行备注",
      render: (_v: unknown, l: LineRow) => (
        <Input
          value={l.lineRemark}
          disabled={readonly}
          onChange={(e) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, lineRemark: e.target.value } : x)))
          }
        />
      ),
    },
    // 终态只读不渲染删除列
    ...(readonly
      ? []
      : [
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
        ]),
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
          <ProForm form={form} layout="vertical" grid submitter={false} disabled={readonly}>
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
            scroll={{ x: 1560, y: "calc(100vh - 520px)" }}
          />
          {/* 添加行按钮:明细表正下方,左对齐(终态只读不渲染) */}
          {!readonly && (
            <div className="doc-form-line-adder">
              <Button onClick={() => setLines((ls) => [...ls, { key: lineSeq++ }])}>添加行</Button>
            </div>
          )}
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
            {readonly ? (
              <Button onClick={() => navigate("/sales-orders")}>返回</Button>
            ) : (
              <Button type="primary" loading={saving} onClick={onSubmit}>
                {editId != null ? "保存" : "保存为草稿"}
              </Button>
            )}
          </div>
        </div>
      </ProCard>
    </>
  );
}
