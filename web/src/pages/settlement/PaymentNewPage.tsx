// 新建付款单/收款单(V18 结算域):选对方 → 勾选 confirmed 正票未核销行 → 核销额(默认全额) → 提交
// 布局与单据新建统一:页头 + 全宽卡片 + 底部固定操作条

import { useEffect, useMemo, useState } from "react";
import { Form, Row, Col, Select, Input, InputNumber, Button, Table, DatePicker, App } from "antd";
import { PlusOutlined, ArrowLeftOutlined } from "@ant-design/icons";
import { ProCard } from "@ant-design/pro-components";
import { Link, useNavigate, useParams } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { paymentApi, supplierApi, customerApi } from "../../api";
import { fmtMoney } from "../../utils/format";
import type { UnsettledInvoice } from "../../types/phase1";

interface LineRow {
  key: number;
  invoiceId: number;
  docNo: string;
  invoiceDate: string;
  totalAmount: number;
  settledAmount: number;
  remaining: number;
  amount?: number;
}

export function PaymentNewPage({ mode }: { mode: "payment" | "receipt" }) {
  const isPayment = mode === "payment";
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  // 路由 /payments|receipts/new/:id? 携带 id 时为只读详情模式(复用新建表单回填)
  const { id: viewIdParam } = useParams<{ id?: string }>();
  const readonly = viewIdParam != null;
  const viewId = readonly ? Number(viewIdParam) : null;

  const [parties, setParties] = useState<{ label: string; value: number }[]>([]);
  const [partyId, setPartyId] = useState<number | undefined>();
  const [candidates, setCandidates] = useState<UnsettledInvoice[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [rows, setRows] = useState<LineRow[]>([]);
  const [remark, setRemark] = useState<string | undefined>(undefined);
  // 日期受控(只读回填用)
  const [payDateVal, setPayDateVal] = useState<Dayjs | null>(null);

  useEffect(() => {
    if (isPayment) {
      supplierApi
        .list({ page: 1, pageSize: 200 })
        .then((r) => setParties(r.rows.map((s) => ({ label: s.supplierName, value: s.id }))))
        .catch(() => undefined);
    } else {
      customerApi
        .list({ page: 1, pageSize: 200 })
        .then((r) => setParties(r.rows.map((c) => ({ label: c.customerName, value: c.id }))))
        .catch(() => undefined);
    }
  }, [isPayment]);

  useEffect(() => {
    if (readonly || !partyId) {
      setCandidates([]);
      return;
    }
    setLoading(true);
    paymentApi
      .unsettledInvoices(mode, partyId)
      .then(setCandidates)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, [mode, partyId, readonly]);

  // 只读详情:GET 回填表头 + 核销行(历史核销行无剩余可核/票额等字段,只展示发票号+核销额)
  useEffect(() => {
    if (viewId == null) return;
    let cancelled = false;
    paymentApi
      .get(viewId, { silent: true })
      .then((d) => {
        if (cancelled) return;
        setPartyId(d.partyId);
        setPayDateVal(d.payDate ? dayjs(d.payDate) : null);
        setRemark(d.remark ?? undefined);
        setRows(
          (d.lines ?? []).map((l) => ({
            key: l.invoiceId,
            invoiceId: l.invoiceId,
            docNo: l.invoiceNo ?? String(l.invoiceId),
            invoiceDate: "",
            totalAmount: 0,
            settledAmount: 0,
            remaining: 0,
            amount: Number(l.amount),
          })),
        );
      })
      .catch(() => {
        if (cancelled) return;
        message.error(`加载${isPayment ? "付款" : "收款"}单详情失败`);
        navigate(isPayment ? "/payments" : "/receipts");
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewId]);

  const onCheck = (keys: React.Key[]) => {
    const checked = new Set(keys.map((k) => String(k)));
    setRows((prev) => {
      const kept = prev.filter((r) => !checked.has(String(r.invoiceId)));
      const added = candidates
        .filter((c) => checked.has(String(c.invoiceId)))
        .map((c) => ({
          key: c.invoiceId,
          invoiceId: c.invoiceId,
          docNo: c.docNo,
          invoiceDate: c.invoiceDate,
          totalAmount: Number(c.totalAmount),
          settledAmount: Number(c.settledAmount),
          remaining: Number(c.remainingAmount),
          amount: Number(c.remainingAmount),
        }));
      return [...kept, ...added];
    });
  };

  const updateRow = (key: number, patch: Partial<LineRow>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)));

  const removeRow = (key: number) => setRows((prev) => prev.filter((r) => r.key !== key));

  const onSubmit = async () => {
    if (!partyId) return message.warning("请选择对方");
    if (rows.length === 0) return message.warning("请至少勾选一张发票核销");
    for (const r of rows) {
      if (r.amount == null || r.amount <= 0) {
        return message.warning(`发票 ${r.docNo}:核销额必须为正数`);
      }
      if (r.amount > r.remaining + 0.009) {
        return message.warning(`发票 ${r.docNo}:核销额 ${r.amount} 超过剩余可核 ${r.remaining.toFixed(2)}`);
      }
    }
    const date = dayjs(payDateVal ?? form.getFieldValue("payDate") ?? new Date()).format("YYYY-MM-DD");
    setSubmitting(true);
    try {
      await paymentApi.create({
        payType: mode,
        partyId: partyId,
        payDate: date,
        remark: remark || undefined,
        lines: rows.map((r) => ({ invoiceId: r.invoiceId, amount: r.amount! })),
      });
      message.success(`${isPayment ? "付款" : "收款"}单已提交`);
      navigate(isPayment ? "/payments?refresh=" + Date.now() : "/receipts?refresh=" + Date.now());
    } catch {
      // 拦截器已处理
    } finally {
      setSubmitting(false);
    }
  };

  const total = useMemo(() => rows.reduce((s, r) => s + (r.amount ?? 0), 0), [rows]);

  const candidateColumns: ColumnsType<UnsettledInvoice> = [
    { title: "发票号", dataIndex: "docNo", width: 170, render: (v) => <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> },
    { title: "日期", dataIndex: "invoiceDate", width: 100, render: (v) => (v ? dayjs(v).format("YYYY-MM-DD") : "-") },
    { title: "票额", dataIndex: "totalAmount", width: 100, align: "right", className: "num-cell", render: (v) => fmtMoney(v) },
    { title: "已核销", dataIndex: "settledAmount", width: 100, align: "right", className: "num-cell", render: (v) => fmtMoney(v) },
    { title: "剩余可核", dataIndex: "remainingAmount", width: 100, align: "right", className: "num-cell", render: (v) => <b>{fmtMoney(v)}</b> },
  ];

  const lineColumns: ColumnsType<LineRow> = [
    { title: "发票号", dataIndex: "docNo", width: 170, render: (v) => <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> },
    { title: "剩余可核", dataIndex: "remaining", width: 110, align: "right", className: "num-cell", render: (v) => fmtMoney(v) },
    {
      title: "核销额",
      dataIndex: "amount",
      width: 160,
      render: (_v, r) => (
        <div>
          <InputNumber
            min={0}
            max={Number(r.remaining.toFixed(2))}
            step={1}
            style={{ width: "100%" }}
            value={r.amount}
            onChange={(v) => updateRow(r.key, { amount: v == null ? undefined : Number(v) })}
          />
          {r.amount != null && r.amount > r.remaining + 0.009 && (
            <div className="serial-error-text">超过剩余可核 {r.remaining.toFixed(2)}</div>
          )}
        </div>
      ),
    },
    {
      title: "操作",
      width: 60,
      render: (_v, r) => (
        <a style={{ color: "#dc2626" }} onClick={() => removeRow(r.key)}>
          删除
        </a>
      ),
    },
  ];

  const readonlyLineColumns: ColumnsType<LineRow> = [
    { title: "发票号", dataIndex: "docNo", width: 190, render: (v) => <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> },
    { title: "核销额", dataIndex: "amount", align: "right", className: "num-cell", render: (v) => fmtMoney(v) },
  ];

  return (
    <>
      <div className="doc-page-head">
        <Link className="doc-page-back" to={isPayment ? "/payments" : "/receipts"}>
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">
          {readonly ? (isPayment ? "付款单详情" : "收款单详情") : isPayment ? "新建付款单" : "新建收款单"}
        </h1>
      </div>

      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
          <Form form={form} layout="vertical" disabled={readonly}>
            <Row gutter={16}>
              <Col span={6}>
                <Form.Item label={isPayment ? "供应商" : "客户"} required>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择对方"
                    value={partyId}
                    options={parties}
                    disabled={readonly}
                    onChange={setPartyId}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="日期" required>
                  <DatePicker
                    style={{ width: "100%" }}
                    value={payDateVal ?? undefined}
                    defaultValue={dayjs()}
                    disabled={readonly}
                    onChange={(v: Dayjs | null) => {
                      setPayDateVal(v);
                      form.setFieldValue("payDate", v ?? undefined);
                    }}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="备注">
                  <Input placeholder="可选" allowClear value={remark} onChange={(e) => setRemark(e.target.value || undefined)} />
                </Form.Item>
              </Col>
            </Row>
          </Form>

          {!readonly && (
            <>
              <div className="doc-form-section-title">
                可核销发票{partyId ? "(已确认正票,含部分核销剩余)" : "(先选对方)"}
              </div>
              <Table
                rowKey="invoiceId"
                size="small"
                rowSelection={{
                  type: "checkbox",
                  selectedRowKeys: rows.map((r) => r.key),
                  onChange: onCheck,
                }}
                columns={candidateColumns}
                dataSource={candidates}
                loading={loading}
                pagination={false}
                scroll={{ x: 700, y: 220 }}
                locale={{ emptyText: partyId ? "该对方暂无可核销发票" : "选择对方后加载" }}
              />
            </>
          )}

          <div className="doc-form-section-title" style={{ marginTop: 12 }}>
            核销行
          </div>
          <Table
            rowKey="key"
            size="small"
            columns={readonly ? readonlyLineColumns : lineColumns}
            dataSource={rows}
            pagination={false}
            locale={{ emptyText: readonly ? "暂无核销行" : "勾选上方发票后自动生成核销行" }}
          />
        </div>

        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {rows.length} 张发票
              {rows.length > 0 && (
                <span className="doc-form-footer-muted">合计核销 {fmtMoney(total)}</span>
              )}
            </div>
            {!readonly && (
              <Button onClick={() => navigate(isPayment ? "/payments" : "/receipts")}>取消</Button>
            )}
          </div>
          <div className="doc-form-footer-main">
            {readonly ? (
              <Button onClick={() => navigate(isPayment ? "/payments" : "/receipts")}>返回</Button>
            ) : (
              <Button type="primary" loading={submitting} onClick={() => void onSubmit()} icon={<PlusOutlined />}>
                {isPayment ? "提交付款" : "提交收款"}
              </Button>
            )}
          </div>
        </div>
      </ProCard>
    </>
  );
}
