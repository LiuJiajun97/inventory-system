// 新建销售报价单(V25,无审批)
// 镜像 SalesOrderNewPage,移除审批相关操作;行明细保留价税六列与双向联动;
// 销售员可选不 required;新增"报价有效期"日期项;底栏"保存草稿 / 发送并保存"。
// 详情模式:id 非空且 docStatus 非 draft → 只读。

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
  ProFormTextArea,
} from "@ant-design/pro-components";
import { ArrowLeftOutlined, ExclamationCircleFilled } from "@ant-design/icons";
import { Link, useNavigate, useParams } from "react-router-dom";
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, quotationApi, customerApi, userApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { Item, UserInfo } from "../../types";
import type { Customer } from "../../types/phase1";
import { fmtMoney } from "../../utils/format";
import { priceMismatchHint, previewLineMoney, recalcPricePair } from "../../utils/lineMoney";

interface LineRow {
  key: number;
  itemId?: number;
  quantity?: number;
  unitPrice?: number;
  taxPrice?: number;
  taxRate?: number;
  remark?: string;
}

let lineSeq = 1;

function isEditable(status: string | undefined): boolean {
  // V25 报价单无驳回,仅 draft 可编辑;其余(sent/converted/voided)只读
  return status === "draft" || status === undefined;
}

export function SalesQuotationNewPage() {
  const navigate = useNavigate();
  const { id: editIdParam } = useParams<{ id?: string }>();
  const editId = editIdParam ? Number(editIdParam) : null;
  const [docNo, setDocNo] = useState("");
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [lines, setLines] = useState<LineRow[]>([{ key: lineSeq++ }]);
  const [saving, setSaving] = useState(false);
  const [docStatus, setDocStatus] = useState<string | undefined>();
  const readonly = editId != null && docStatus != null && !isEditable(docStatus);
  const [form] = ProForm.useForm();

  useEffect(() => {
    customerApi.list({ page: 1, pageSize: 200 }).then((r) => setCustomers(r.rows)).catch(() => undefined);
    itemApi.list({ page: 1, pageSize: 200 }).then((r) => setItems(r.rows)).catch(() => undefined);
    userApi.list({ page: 1, pageSize: 200 }).then((r) => setUsers(r.rows)).catch(() => undefined);
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (editId == null) return;
    quotationApi.get(editId).then((doc) => {
      setDocNo(doc.docNo);
      setDocStatus(doc.status);
      form.setFieldsValue({
        docDate: dayjs(doc.docDate),
        customerId: doc.customerId,
        salespersonId: doc.salespersonId ?? undefined,
        warehouseId: doc.warehouseId,
        quoteValidUntil: doc.quoteValidUntil ? dayjs(doc.quoteValidUntil) : undefined,
        remark: doc.remark ?? undefined,
      });
      const rows: LineRow[] = (doc.items ?? []).map((l, i) => ({
        key: i + 1,
        itemId: l.itemId,
        quantity: Number(l.quantity),
        unitPrice: l.unitPrice != null ? Number(l.unitPrice) : undefined,
        taxPrice: l.taxPrice != null ? Number(l.taxPrice) : undefined,
        taxRate: Number(l.taxRate),
        remark: l.remark ?? undefined,
      }));
      setLines(rows);
      lineSeq = Math.max(lineSeq, rows.length + 1);
    }).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId]);

  const itemOptions = items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }));

  const onItemChange = (key: number, itemId: number) => {
    const it = items.find((x) => x.id === itemId);
    setLines((ls) =>
      ls.map((l) => {
        if (l.key !== key) return l;
        const unitPrice =
          l.unitPrice == null && it?.referenceSalePrice != null
            ? Number(it.referenceSalePrice)
            : l.unitPrice;
        const taxRate = it ? Number(it.defaultTaxRate ?? 13) : l.taxRate;
        const taxPrice =
          unitPrice != null
            ? recalcPricePair(unitPrice, l.taxPrice, taxRate, "unit").taxPrice ?? l.taxPrice
            : l.taxPrice;
        return { ...l, itemId, taxRate, unitPrice, taxPrice };
      }),
    );
  };

  const doSave = async (markSent: boolean) => {
    const v = await form.validateFields();
    const validLines = lines.filter(
      (l) => l.itemId && l.quantity && (l.unitPrice != null || l.taxPrice != null),
    );
    if (validLines.length === 0) {
      message.error("请至少填写一行报价明细");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        docDate: v.docDate.format("YYYY-MM-DD"),
        customerId: v.customerId,
        salespersonId: v.salespersonId,
        warehouseId: v.warehouseId,
        quoteValidUntil: v.quoteValidUntil?.format("YYYY-MM-DD"),
        remark: v.remark,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          quantity: l.quantity!,
          unitPrice: l.unitPrice ?? undefined,
          taxPrice: l.taxPrice ?? undefined,
          taxRate: l.taxRate ?? 13,
          remark: l.remark,
        })),
      };
      let id = editId;
      if (editId != null) {
        await quotationApi.update(editId, payload);
        message.success("报价单已保存");
      } else {
        const created = await quotationApi.create(payload);
        id = created.id;
        message.success("报价单已创建(草稿)");
      }
      if (markSent && id != null) {
        await quotationApi.send(id);
        message.success("已标记发送");
      }
      navigate("/sales-quotations");
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const lineColumns = [
    {
      title: "物品",
      width: 250,
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
      title: "数量",
      width: 100,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0.0001}
          step={1}
          style={{ width: "100%" }}
          value={l.quantity}
          disabled={readonly}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, quantity: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "不含税单价",
      width: 110,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0}
          step={0.01}
          style={{ width: "100%" }}
          value={l.unitPrice}
          disabled={readonly}
          onChange={(v) => {
            const next = recalcPricePair(v ?? undefined, l.taxPrice, l.taxRate, "unit");
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, unitPrice: v ?? undefined, ...next } : x)));
          }}
        />
      ),
    },
    {
      title: "含税单价",
      width: 130,
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
                const next = recalcPricePair(l.unitPrice, v ?? undefined, l.taxRate, "tax");
                setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, taxPrice: v ?? undefined, ...next } : x)));
              }}
            />
            {hint != null && (
              <Tooltip title={hint}>
                <ExclamationCircleFilled style={{ color: "#fa8c16", fontSize: 14, marginLeft: 6, cursor: "help" }} />
              </Tooltip>
            )}
          </span>
        );
      },
    },
    {
      title: "不含税金额",
      width: 90,
      align: "right" as const,
      className: "num-cell",
      render: (_v: unknown, l: LineRow) => {
        const pm = previewLineMoney(l.quantity ?? 0, l.unitPrice, l.taxPrice, l.taxRate);
        return pm ? fmtMoney(pm.amount) : "-";
      },
    },
    {
      title: "税额",
      width: 90,
      align: "right" as const,
      className: "num-cell",
      render: (_v: unknown, l: LineRow) => {
        const pm = previewLineMoney(l.quantity ?? 0, l.unitPrice, l.taxPrice, l.taxRate);
        return pm ? fmtMoney(pm.tax) : "-";
      },
    },
    {
      title: "含税金额",
      width: 90,
      align: "right" as const,
      className: "num-cell",
      render: (_v: unknown, l: LineRow) => {
        const pm = previewLineMoney(l.quantity ?? 0, l.unitPrice, l.taxPrice, l.taxRate);
        return pm ? fmtMoney(pm.inclusive) : "-";
      },
    },
    {
      title: "税率(%)",
      width: 90,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0}
          max={100}
          step={0.01}
          style={{ width: "100%" }}
          value={l.taxRate}
          disabled={readonly}
          onChange={(v) => {
            const next = v != null ? recalcPricePair(l.unitPrice, l.taxPrice, v, "rate") : {};
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, taxRate: v ?? undefined, ...next } : x)));
          }}
        />
      ),
    },
    {
      title: "行备注",
      width: 120,
      render: (_v: unknown, l: LineRow) => (
        <Input
          value={l.remark}
          disabled={readonly}
          onChange={(e) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, remark: e.target.value } : x)))
          }
        />
      ),
    },
    ...(readonly
      ? []
      : [
          {
            title: "",
            width: 50,
            render: (_v: unknown, l: LineRow) => (
              <a onClick={() => setLines((ls) => (ls.length > 1 ? ls.filter((x) => x.key !== l.key) : ls))}>
                删除
              </a>
            ),
          },
        ]),
  ];

  return (
    <>
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/sales-quotations">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">
          {editId != null ? "编辑销售报价单" : "新建销售报价单"}
        </h1>
        {editId != null && <span className="doc-page-meta">单号 {docNo || "..."}</span>}
      </div>

      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
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
              options={customers.map((s) => ({ label: `${s.customerCode} ${s.customerName}`, value: s.id }))}
              fieldProps={{ optionFilterProp: "label" }}
              rules={[{ required: true, message: "请选择客户" }]}
            />
            <ProFormSelect
              name="salespersonId"
              label="销售员"
              colProps={{ span: 6 }}
              placeholder="选择销售员(可空)"
              options={users.map((u) => ({ label: u.name, value: u.id }))}
            />
            <ProFormSelect
              name="warehouseId"
              label="发货仓库"
              colProps={{ span: 6 }}
              placeholder="选择发货仓库"
              options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
              rules={[{ required: true, message: "请选择发货仓库" }]}
            />
            <ProFormDatePicker
              name="quoteValidUntil"
              label="报价有效期"
              colProps={{ span: 6 }}
              tooltip="过期仅展示橙色'已过期'标记(状态不变);转换订单时拦截"
            />
            <ProFormDigit
              name="_unused"
              label=""
              colProps={{ span: 18 }}
              hidden
            />
            <ProFormTextArea
              name="remark"
              label="备注"
              colProps={{ span: 24 }}
              fieldProps={{ rows: 2 }}
            />
          </ProForm>

          <div className="doc-form-section-title">报价明细</div>
          <Table
            rowKey="key"
            size="small"
            dataSource={lines}
            pagination={false}
            columns={lineColumns}
            scroll={{ x: 1250, y: "calc(100vh - 520px)" }}
          />
          {!readonly && (
            <div className="doc-form-line-adder">
              <Button onClick={() => setLines((ls) => [...ls, { key: lineSeq++ }])}>添加行</Button>
            </div>
          )}
        </div>
        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {lines.length} 行明细
              <span className="doc-form-footer-muted">金额以服务端价税重算为准</span>
            </div>
          </div>
          <div className="doc-form-footer-main">
            {readonly ? (
              <Button onClick={() => navigate("/sales-quotations")}>返回</Button>
            ) : (
              <div style={{ display: "flex", gap: 12 }}>
                <Button loading={saving} onClick={() => doSave(false)}>
                  保存草稿
                </Button>
                <Button type="primary" loading={saving} onClick={() => doSave(true)}>
                  发送并保存
                </Button>
              </div>
            )}
          </div>
        </div>
      </ProCard>
    </>
  );
}