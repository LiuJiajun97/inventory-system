// 库存调整单(一期新增)
// gain 盘盈入库 / loss 盘亏出库;审批即执行库存动作;盘点差异生成 + 手工调整共用

import { useEffect, useState } from "react";
import { Button, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { adjustApi, itemApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StockAdjustDoc } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";
import { DocStatusTag } from "../../components/DocStatusTag";
import { fmtDate } from "../../utils/format";

const TYPE_LABEL: Record<string, string> = { gain: "盘盈(入库)", loss: "盘亏(出库)" };

export function AdjustPage() {
  const user = getUser();
  const isAdmin = user?.role === "admin";
  const isWriter = user?.role === "admin" || user?.role === "operator";
  const [rows, setRows] = useState<StockAdjustDoc[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<StockAdjustDoc | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState<StockAdjustDoc | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [adjustType, setAdjustType] = useState<"gain" | "loss">("loss");
  const [lines, setLines] = useState<Array<{ key: number; itemId?: number; qty?: number; unitPrice?: number; reason?: string }>>([{ key: 1 }]);

  const whName = (id: number) => warehouses.find((w) => w.id === id)?.warehouseName ?? `#${id}`;

  const onSearch = async (values: Record<string, unknown>, pg = 1, ps = 20) => {
    setLoading(true);
    try {
      const res = await adjustApi.list({
        warehouseId: values.warehouseId as number | undefined,
        adjustType: values.adjustType as string | undefined,
        docNo: values.docNo as string | undefined,
        status: values.status as string | undefined,
        page: pg,
        pageSize: ps,
      });
      setRows(res.rows);
      setTotal(res.total);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
    onSearch({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refresh = () => onSearch(form.getFieldsValue(), page, pageSize);

  const doAction = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      Modal.success({ content: msg });
      refresh();
    } catch {
      // 拦截器已提示
    }
  };

  const openCreate = () => {
    setAdjustType("loss");
    setLines([{ key: 1 }]);
    setCreateOpen(true);
  };

  const onCreate = async () => {
    const head = form.getFieldsValue(["docDate", "warehouseId", "remark"]);
    if (!head.docDate || !head.warehouseId) {
      Modal.error({ content: "请选择调整日期和仓库" });
      return;
    }
    const valid = lines.filter((l) => l.itemId && l.qty);
    if (valid.length === 0) {
      Modal.error({ content: "请至少填写一行调整明细" });
      return;
    }
    setSaving(true);
    try {
      await adjustApi.create({
        warehouseId: head.warehouseId,
        docDate: (head.docDate as Dayjs).format("YYYY-MM-DD"),
        adjustType,
        remark: head.remark,
        items: valid.map((l) => ({
          itemId: l.itemId!,
          qty: l.qty!,
          unitPrice: l.unitPrice,
          reason: l.reason,
        })),
      });
      Modal.success({ content: "调整单已创建(草稿)" });
      setCreateOpen(false);
      refresh();
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<StockAdjustDoc> = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      render: (v: string, row) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => setDetail(row)}>
          {v}
        </a>
      ),
    },
    { title: "调整日期", dataIndex: "docDate", width: 110, render: (v: string) => fmtDate(v) },
    { title: "仓库", width: 130, ellipsis: true, render: (_v, r) => whName(r.warehouseId) },
    {
      title: "类型",
      dataIndex: "adjustType",
      width: 110,
      render: (v: string) => (
        <span style={{ color: v === "gain" ? "#389e0d" : "#cf1322" }}>
          {TYPE_LABEL[v] ?? v}
        </span>
      ),
    },
    {
      title: "来源",
      dataIndex: "refDocNo",
      width: 150,
      render: (v: string | null) => v ?? "-",
    },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      render: (v: string) => <DocStatusTag status={v} />,
    },
    {
      title: "操作",
      width: 220,
      fixed: "right" as const,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (isWriter && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="submit" className="action-submit" onClick={() => doAction(() => adjustApi.submit(row.id), "已提交审批")}>
              提交
            </a>,
            <Popconfirm key="void" title="确认作废该调整单?" onConfirm={() => doAction(() => adjustApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (isWriter && s === "pending") {
          btns.push(
            <Popconfirm
              key="approve"
              title={row.adjustType === "gain" ? "审批即执行盘盈入库。确认?" : "审批即执行盘亏出库(库存不足将回滚)。确认?"}
              onConfirm={() => doAction(() => adjustApi.approve(row.id), "调整已执行完成")}
            >
              <a className="action-approve">审批执行</a>
            </Popconfirm>,
            <a key="reject" className="action-reject" onClick={() => setRejectTarget(row)}>
              驳回
            </a>,
          );
        }
        return <Space size={12}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  const statusOptions = [
    { label: "草稿", value: "draft" },
    { label: "待审批", value: "pending" },
    { label: "已完成", value: "completed" },
    { label: "已驳回", value: "rejected" },
    { label: "已作废", value: "voided" },
  ];

  const itemOptions = items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }));

  return (
    <>
      <ListPageShell
        extra={
          isWriter && (
            <Button type="primary" onClick={openCreate}>
              新建调整单
            </Button>
          )
        }
        filter={
          <Form form={form} layout="inline" onFinish={(v) => { setPage(1); onSearch(v); }}>
            <Form.Item label="单号" name="docNo">
              <Input allowClear style={{ width: 150 }} />
            </Form.Item>
            <Form.Item label="仓库" name="warehouseId">
              <Select allowClear placeholder="全部" style={{ width: 160 }} options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))} />
            </Form.Item>
            <Form.Item label="类型" name="adjustType">
              <Select
                allowClear
                placeholder="全部"
                style={{ width: 130 }}
                options={[
                  { label: "盘盈(入库)", value: "gain" },
                  { label: "盘亏(出库)", value: "loss" },
                ]}
              />
            </Form.Item>
            <Form.Item label="状态" name="status">
              <Select allowClear placeholder="全部" style={{ width: 120 }} options={statusOptions} />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">查询</Button>
                <Button onClick={() => { form.resetFields(); setPage(1); onSearch({}); }}>重置</Button>
              </Space>
            </Form.Item>
          </Form>
        }
        tableProps={{
          rowKey: "id",
          loading,
          columns,
          dataSource: rows,
          scroll: { x: 1050 },
          pagination: {
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
              onSearch(form.getFieldsValue(), p, ps);
            },
            showTotal: (t) => `共 ${t} 条`,
          },
        }}
      />

      <Drawer
        title="新建库存调整单"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={640}
        extra={
          <Button type="primary" loading={saving} onClick={onCreate}>
            保存为草稿
          </Button>
        }
      >
        <Form form={form} layout="vertical">
          <Space wrap size={24}>
            <Form.Item label="调整类型" required>
              <Select
                value={adjustType}
                onChange={setAdjustType}
                style={{ width: 160 }}
                options={[
                  { label: "盘盈(入库)", value: "gain" },
                  { label: "盘亏(出库)", value: "loss" },
                ]}
              />
            </Form.Item>
            <Form.Item label="调整日期" name="docDate" initialValue={dayjs()}>
              <DatePicker style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="仓库" name="warehouseId">
              <Select
                placeholder="选择仓库"
                style={{ width: 180 }}
                options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
              />
            </Form.Item>
          </Space>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
        <div style={{ fontWeight: 600, marginBottom: 8 }}>调整明细</div>
        <Table
          rowKey="key"
          size="small"
          dataSource={lines}
          pagination={false}
          columns={[
            {
              title: "物品",
              width: 200,
              render: (_v: unknown, l) => (
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择物品"
                  style={{ width: "100%" }}
                  options={itemOptions}
                  value={l.itemId}
                  onChange={(v) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, itemId: v } : x)))}
                />
              ),
            },
            {
              title: "数量",
              width: 110,
              render: (_v: unknown, l) => (
                <InputNumber
                  min={0.0001}
                  step={1}
                  style={{ width: "100%" }}
                  value={l.qty}
                  onChange={(v) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, qty: v ?? undefined } : x)))}
                />
              ),
            },
            {
              title: "参考单价",
              width: 110,
              render: (_v: unknown, l) => (
                <InputNumber
                  min={0}
                  step={0.01}
                  style={{ width: "100%" }}
                  value={l.unitPrice}
                  onChange={(v) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, unitPrice: v ?? undefined } : x)))}
                />
              ),
            },
            {
              title: "原因",
              render: (_v: unknown, l) => (
                <Input
                  value={l.reason}
                  placeholder="如:破损报废"
                  onChange={(e) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, reason: e.target.value } : x)))}
                />
              ),
            },
            {
              title: "",
              width: 60,
              render: (_v: unknown, l) => (
                <a onClick={() => setLines((ls) => (ls.length > 1 ? ls.filter((x) => x.key !== l.key) : ls))}>
                  删除
                </a>
              ),
            },
          ]}
        />
        <Space style={{ marginTop: 12 }}>
          <Button onClick={() => setLines((ls) => [...ls, { key: Math.max(...ls.map((x) => x.key)) + 1 }])}>
            添加行
          </Button>
        </Space>
      </Drawer>

      <Modal
        title={`调整单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={760}
      >
        {detail && (
          <>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="调整日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="仓库">{whName(detail.warehouseId)}</Descriptions.Item>
              <Descriptions.Item label="类型">{TYPE_LABEL[detail.adjustType] ?? detail.adjustType}</Descriptions.Item>
              <Descriptions.Item label="来源单">{detail.refDocNo ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <DocStatusTag status={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="驳回原因" span={3}>{detail.rejectReason ?? "-"}</Descriptions.Item>
            </Descriptions>
            <Table
              style={{ marginTop: 12 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                { title: "行号", dataIndex: "lineNo", width: 50 },
                { title: "物品", dataIndex: "itemName", render: (v: string, r) => `${r.itemCode} ${v}` },
                { title: "数量", dataIndex: "qty", width: 90, align: "right", className: "num-cell" },
                {
                  title: "参考单价",
                  dataIndex: "unitPrice",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v) => (v == null ? "-" : Number(v).toFixed(2)),
                },
                { title: "原因", dataIndex: "reason", render: (v) => v ?? "-" },
              ]}
            />
          </>
        )}
      </Modal>

      <Modal
        title={`驳回调整单 - ${rejectTarget?.docNo ?? ""}`}
        open={!!rejectTarget}
        onCancel={() => { setRejectTarget(null); form.setFieldValue("reason", ""); }}
        onOk={() => {
          const v = form.getFieldValue("reason") as string;
          if (!v?.trim()) return;
          doAction(() => adjustApi.reject(rejectTarget!.id, v.trim()), "已驳回");
          setRejectTarget(null);
          form.setFieldValue("reason", "");
        }}
        okButtonProps={{ disabled: !form.getFieldValue("reason")?.trim() }}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="驳回原因(必填)" name="reason">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
