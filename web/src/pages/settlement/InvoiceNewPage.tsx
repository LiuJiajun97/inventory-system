// 新建/编辑发票(V18 结算域):全宽卡片页(与单据新建统一风格)
// 步骤:选类型 → 选对方 → 勾选"未开票/部分开票"单据行 → 开票额(默认剩余可开,可改) → 保存
// 编辑模式(/invoices/new/:id):仅 draft/mismatch 可改;正票行可改/删/加,负票(退货生成)行只能改金额/删除
// 差异:|开票额 - 源行含税额| > 0.01 前端红字提示(服务端落 mismatch)

import { useEffect, useMemo, useState } from "react";
import { Form, Row, Col, Select, Input, InputNumber, Button, Table, DatePicker, App, Space } from "antd";
import { PlusOutlined, ArrowLeftOutlined } from "@ant-design/icons";
import { ProCard } from "@ant-design/pro-components";
import { Link, useNavigate, useParams } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { invoiceApi, supplierApi, customerApi } from "../../api";
import { fmtMoney, fmtQty } from "../../utils/format";
import type { Invoice, InvoiceableLine } from "../../types/phase1";

interface LineRow {
  key: string;
  srcDocType: string;
  srcDocId: number;
  srcDocItemId: number;
  srcDocNo?: string | null;
  itemName: string;
  itemCode: string;
  spec?: string | null;
  unit?: string | null;
  quantity?: number | null;
  srcAmount: number;
  invoicedAmount?: number;
  locked?: boolean; // 负票行:源锁定,仅金额可改
}

export function InvoiceNewPage() {
  const navigate = useNavigate();
  const { id } = useParams<{ id?: string }>();
  const editing = id != null;
  const { message } = App.useApp();
  const [form] = Form.useForm();

  const [invoiceType, setInvoiceType] = useState<"purchase" | "sales" | undefined>();
  const [parties, setParties] = useState<{ label: string; value: number }[]>([]);
  const [partyId, setPartyId] = useState<number | undefined>();
  const [candidates, setCandidates] = useState<InvoiceableLine[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [rows, setRows] = useState<LineRow[]>([]);
  const [remark, setRemark] = useState<string | undefined>(undefined);
  const [editHead, setEditHead] = useState<Invoice | null>(null);
  // 发票状态机 draft/confirmed/mismatch/voided:confirmed/voided 为终态只读(回填表单无保存入口)
  const [readonly, setReadonly] = useState(false);
  // 发票日期受控(只读回填用)
  const [invoiceDateVal, setInvoiceDateVal] = useState<Dayjs | null>(null);

  useEffect(() => {
    loadParties(invoiceType);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [invoiceType]);

  useEffect(() => {
    if (!invoiceType || !partyId) {
      setCandidates([]);
      return;
    }
    setLoading(true);
    invoiceApi
      .invoiceableLines(invoiceType, partyId)
      .then(setCandidates)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, [invoiceType, partyId]);

  // 编辑模式:加载详情
  useEffect(() => {
    if (!editing) return;
    invoiceApi
      .get(Number(id))
      .then((inv) => {
        // confirmed/voided 终态:不再跳转列表,改为只读回填(隐藏保存 + 禁用控件)
        setReadonly(inv.status !== "draft" && inv.status !== "mismatch");
        setEditHead(inv);
        setInvoiceType(inv.invoiceType);
        setPartyId(inv.partyId);
        setInvoiceDateVal(dayjs(inv.invoiceDate));
        setRemark(inv.remark ?? undefined);
        setRows(
          (inv.items ?? []).map((it) => ({
            key: `${it.srcDocType}-${it.srcDocId}-${it.srcDocItemId}`,
            srcDocType: it.srcDocType,
            srcDocId: it.srcDocId,
            srcDocItemId: it.srcDocItemId,
            srcDocNo: it.srcDocNo,
            itemName: it.itemName ?? String(it.itemId),
            itemCode: it.itemCode ?? "",
            spec: it.specSnapshot,
            unit: it.unit,
            quantity: it.quantity,
            srcAmount: Number(it.srcAmount ?? 0),
            invoicedAmount: Number(it.invoicedAmount),
            locked: inv.sign === "negative",
          })),
        );
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const loadParties = async (type?: "purchase" | "sales") => {
    if (!type) {
      setParties([]);
      return;
    }
    try {
      if (type === "purchase") {
        const res = await supplierApi.list({ page: 1, pageSize: 200 });
        setParties(res.rows.map((s) => ({ label: s.supplierName, value: s.id })));
      } else {
        const res = await customerApi.list({ page: 1, pageSize: 200 });
        setParties(res.rows.map((c) => ({ label: c.customerName, value: c.id })));
      }
    } catch {
      // 拦截器已提示
    }
  };

  // 勾选候选行 → 进入发票行(开票额默认剩余可开额)
  const onCheckRows = (keys: React.Key[]) => {
    const checked = new Set(keys.map((k) => String(k)));
    setRows((prev) => {
      const kept = prev.filter((r) => !checked.has(r.key));
      const added = candidates
        .filter((c) => checked.has(`${c.srcDocType}-${c.srcDocId}-${c.srcDocItemId}`))
        .map((c) => ({
          key: `${c.srcDocType}-${c.srcDocId}-${c.srcDocItemId}`,
          srcDocType: c.srcDocType,
          srcDocId: c.srcDocId,
          srcDocItemId: c.srcDocItemId,
          srcDocNo: c.srcDocNo,
          itemName: c.itemName,
          itemCode: c.itemCode,
          spec: c.spec,
          unit: c.unit,
          quantity: c.quantity,
          srcAmount: Number(c.srcAmount),
          invoicedAmount: Number(c.remaining),
        }));
      return [...kept, ...added];
    });
  };

  const onTypeChange = (t: "purchase" | "sales") => {
    setInvoiceType(t);
    setPartyId(undefined);
    setRows([]);
  };

  const updateRow = (key: string, patch: Partial<LineRow>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)));

  const removeRow = (key: string) => setRows((prev) => prev.filter((r) => r.key !== key));

  const onSubmit = async () => {
    if (!editing) {
      if (!invoiceType) return message.warning("请选择发票类型");
      if (!partyId) return message.warning("请选择对方");
    }
    if (rows.length === 0) return message.warning("请至少挂一行");
    for (const r of rows) {
      const v = r.invoicedAmount;
      if (v == null || (r.locked ? v >= 0 : v <= 0)) {
        return message.warning(`行 ${r.itemCode || r.itemName}:开票额必须${r.locked ? "为负数" : "为正数"}`);
      }
    }
    const date = dayjs(invoiceDateVal ?? form.getFieldValue("invoiceDate") ?? editHead?.invoiceDate ?? new Date()).format(
      "YYYY-MM-DD",
    );
    setSubmitting(true);
    try {
      const items = rows.map((r) => ({
        srcDocType: r.srcDocType,
        srcDocId: r.srcDocId,
        srcDocItemId: r.srcDocItemId,
        invoicedAmount: r.invoicedAmount!,
      }));
      const remarkVal = remark || undefined;
      if (editing) {
        await invoiceApi.update(Number(id), {
          partyId,
          invoiceDate: date,
          remark: remarkVal,
          items,
        });
        message.success("发票已保存");
      } else {
        await invoiceApi.create({
          invoiceType: invoiceType!,
          partyId: partyId!,
          invoiceDate: date,
          remark: remarkVal,
          items,
        });
        message.success("发票已登记");
      }
      navigate("/invoices?refresh=" + Date.now());
    } catch {
      // 拦截器已处理
    } finally {
      setSubmitting(false);
    }
  };

  const total = useMemo(
    () => rows.reduce((s, r) => s + (r.invoicedAmount ?? 0), 0),
    [rows],
  );

  const candidateColumns: ColumnsType<InvoiceableLine> = [
    { title: "单号", dataIndex: "srcDocNo", width: 150, render: (v) => <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> },
    { title: "日期", dataIndex: "docDate", width: 100, render: (v) => (v ? dayjs(v).format("YYYY-MM-DD") : "-") },
    { title: "物品", width: 180, ellipsis: true, render: (_v, r) => `${r.itemCode} ${r.itemName}` },
    { title: "规格", dataIndex: "spec", width: 90, ellipsis: true, render: (v) => v ?? "-" },
    { title: "数量", dataIndex: "quantity", width: 80, align: "right", className: "num-cell", render: (v) => fmtQty(v) },
    { title: "含税额", dataIndex: "srcAmount", width: 100, align: "right", className: "num-cell", render: (v) => fmtMoney(v) },
    { title: "已开票", dataIndex: "invoicedSoFar", width: 100, align: "right", className: "num-cell", render: (v) => fmtMoney(v) },
    { title: "剩余可开", dataIndex: "remaining", width: 100, align: "right", className: "num-cell", render: (v) => <b>{fmtMoney(v)}</b> },
  ];

  const lineColumns: ColumnsType<LineRow> = [
    { title: "行号", width: 50, render: (_v, _r, idx) => idx + 1 },
    { title: "源单号", dataIndex: "srcDocNo", width: 150, render: (v) => (v ? <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> : "-") },
    { title: "物品", width: 180, ellipsis: true, render: (_v, r) => `${r.itemCode} ${r.itemName}`.trim() || "-" },
    { title: "规格", dataIndex: "spec", width: 90, ellipsis: true, render: (v) => v ?? "-" },
    { title: "数量", dataIndex: "quantity", width: 80, align: "right", className: "num-cell", render: (v) => fmtQty(v) },
    { title: "源行含税额", dataIndex: "srcAmount", width: 110, align: "right", className: "num-cell", render: (v) => fmtMoney(v) },
    {
      title: "开票额",
      dataIndex: "invoicedAmount",
      width: 150,
      render: (_v, r) => (
        <div>
          <InputNumber
            style={{ width: "100%" }}
            min={r.locked ? undefined : 0}
            max={r.locked ? 0 : undefined}
            step={1}
            value={r.invoicedAmount}
            disabled={readonly}
            onChange={(v) => updateRow(r.key, { invoicedAmount: v == null ? undefined : Number(v) })}
          />
          {r.invoicedAmount != null && Math.abs(Number(r.invoicedAmount) - r.srcAmount) > 0.01 && (
            <div className="serial-error-text">差异 {fmtMoney(Number(r.invoicedAmount) - Number(r.srcAmount))}(将挂差异)</div>
          )}
        </div>
      ),
    },
    {
      title: "操作",
      width: 60,
      render: (_v, r) =>
        readonly ? (
          <span>-</span>
        ) : r.locked ? (
          <span style={{ color: "#9ca3af", fontSize: 12 }}>源锁定</span>
        ) : (
          <a style={{ color: "#dc2626" }} onClick={() => removeRow(r.key)}>删除</a>
        ),
    },
  ];

  return (
    <>
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/invoices">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">{editing ? "编辑发票" : "新建发票"}</h1>
      </div>

      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
          <Form form={form} layout="vertical">
            <Row gutter={16}>
              <Col span={6}>
                <Form.Item label="发票类型" required={!editing}>
                  <Select
                    placeholder="采购票 / 销售票"
                    disabled={editing}
                    value={invoiceType}
                    onChange={onTypeChange}
                    options={[
                      { label: "采购票(对方=供应商)", value: "purchase" },
                      { label: "销售票(对方=客户)", value: "sales" },
                    ]}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="对方" required={!editing}>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder={invoiceType ? "选择对方" : "先选发票类型"}
                    value={partyId}
                    options={parties}
                    disabled={readonly}
                    onChange={setPartyId}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="发票日期" required>
                  <DatePicker
                    style={{ width: "100%" }}
                    value={invoiceDateVal ?? undefined}
                    defaultValue={dayjs()}
                    disabled={readonly}
                    onChange={(v: Dayjs | null) => {
                      setInvoiceDateVal(v);
                      form.setFieldValue("invoiceDate", v ?? undefined);
                    }}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="备注">
                  <Input placeholder="可选" allowClear value={remark} onChange={(e) => setRemark(e.target.value || undefined)} />
                </Form.Item>
              </Col>
            </Row>
          </Form>

          {!editing && (
            <>
              <div className="doc-form-section-title">
                可挂票单据行{invoiceType && partyId ? "(未开票/部分开票)" : "(先选类型与对方)"}
              </div>
              <Table
                rowKey={(r) => `${r.srcDocType}-${r.srcDocId}-${r.srcDocItemId}`}
                size="small"
                rowSelection={{
                  type: "checkbox",
                  selectedRowKeys: rows.map((r) => r.key),
                  onChange: (keys) => onCheckRows(keys),
                }}
                columns={candidateColumns}
                dataSource={candidates}
                loading={loading}
                pagination={false}
                scroll={{ x: 900, y: 220 }}
                locale={{ emptyText: invoiceType && partyId ? "该对方暂无可挂票单据行" : "选择发票类型与对方后加载" }}
              />
            </>
          )}

          <div className="doc-form-section-title" style={{ marginTop: 12 }}>
            发票行{editing && editHead?.sign === "negative" ? "(负票:仅可改金额/删除行)" : ""}
          </div>
          <Table
            rowKey="key"
            size="small"
            columns={lineColumns}
            dataSource={rows}
            pagination={false}
            locale={{ emptyText: "勾选上方可挂票单据行后自动生成行" }}
          />
        </div>

        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {rows.length} 行
              {rows.length > 0 && (
                <span className="doc-form-footer-muted">
                  合计 {fmtMoney(total)}(以服务端重算为准)
                </span>
              )}
            </div>
            <Button onClick={() => navigate("/invoices")}>取消</Button>
          </div>
          <div className="doc-form-footer-main">
            <Space>
              {readonly ? (
                <Button onClick={() => navigate("/invoices")}>返回</Button>
              ) : (
                <Button type="primary" loading={submitting} onClick={() => void onSubmit()} icon={<PlusOutlined />}>
                  {editing ? "保存" : "登记发票"}
                </Button>
              )}
            </Space>
          </div>
        </div>
      </ProCard>
    </>
  );
}
