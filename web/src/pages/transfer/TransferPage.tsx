// 调拨单(一期新增)
// 列表 + 新建 Drawer + 详情 + 状态机操作(提交/审批执行/驳回/作废)
// 审批即执行:同一事务源仓扣减 + 目的仓入库

import { useEffect, useState } from "react";
import { Button, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { fmtDate } from "../../utils/format";
import { itemApi, transferApi, warehouseApi } from "../../api";
import type { Item, Location, Warehouse } from "../../types";
import type { TransferDoc } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";
import { DocStatusTag } from "../../components/DocStatusTag";

export function TransferPage() {
  const user = getUser();
  const isAdmin = user?.role === "admin";
  const isWriter = user?.role === "admin" || user?.role === "operator";
  const [rows, setRows] = useState<TransferDoc[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<TransferDoc | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState<TransferDoc | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [lines, setLines] = useState<Array<{ key: number; itemId?: number; qty?: number; unitPrice?: number; fromLocationId?: number; toLocationId?: number }>>([{ key: 1 }]);
  const [fromWh, setFromWh] = useState<number>();
  const [toWh, setToWh] = useState<number>();
  const [fromLocations, setFromLocations] = useState<Location[]>([]);
  const [toLocations, setToLocations] = useState<Location[]>([]);
  const [docDate, setDocDate] = useState<Dayjs>(dayjs());

  const fromWarehouse = warehouses.find((w) => w.id === fromWh);
  const toWarehouse = warehouses.find((w) => w.id === toWh);

  const whName = (id: number) => warehouses.find((w) => w.id === id)?.warehouseName ?? `#${id}`;

  const onSearch = async (values: Record<string, unknown>, pg = 1, ps = 20) => {
    setLoading(true);
    try {
      const res = await transferApi.list({
        fromWarehouseId: values.fromWarehouseId as number | undefined,
        toWarehouseId: values.toWarehouseId as number | undefined,
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

  useEffect(() => {
    if (fromWh) {
      warehouseApi.listLocations({ warehouseId: fromWh, page: 1, pageSize: 200 })
        .then((r) => setFromLocations(r.rows)).catch(() => setFromLocations([]));
    } else {
      setFromLocations([]);
    }
  }, [fromWh]);

  useEffect(() => {
    if (toWh) {
      warehouseApi.listLocations({ warehouseId: toWh, page: 1, pageSize: 200 })
        .then((r) => setToLocations(r.rows)).catch(() => setToLocations([]));
    } else {
      setToLocations([]);
    }
  }, [toWh]);

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
    setLines([{ key: 1 }]);
    setFromWh(undefined);
    setToWh(undefined);
    setFromLocations([]);
    setToLocations([]);
    setDocDate(dayjs());
    setCreateOpen(true);
  };

  const onCreate = async () => {
    if (!fromWh || !toWh) {
      Modal.error({ content: "请选择源仓库和目的仓库" });
      return;
    }
    if (fromWh === toWh) {
      Modal.error({ content: "源仓与目的仓不能相同" });
      return;
    }
    const head = form.getFieldsValue(["docDate", "fromWarehouseId", "toWarehouseId", "remark"]);
    const valid = lines.filter((l) => l.itemId && l.qty);
    if (valid.length === 0) {
      Modal.error({ content: "请至少填写一行调拨明细" });
      return;
    }
    // 启用库位的仓:行必填对应库位(与后端 400 校验对齐)
    if (fromWarehouse?.enableLocation && valid.some((l) => !l.fromLocationId)) {
      Modal.error({ content: "源仓启用库位,每行必填源库位" });
      return;
    }
    if (toWarehouse?.enableLocation && valid.some((l) => !l.toLocationId)) {
      Modal.error({ content: "目的仓启用库位,每行必填目的库位" });
      return;
    }
    setSaving(true);
    try {
      await transferApi.create({
        docDate: docDate.format("YYYY-MM-DD"),
        fromWarehouseId: fromWh,
        toWarehouseId: toWh,
        remark: head.remark,
        items: valid.map((l) => ({
          itemId: l.itemId!,
          qty: l.qty!,
          unitPrice: l.unitPrice,
          fromLocationId: l.fromLocationId,
          toLocationId: l.toLocationId,
        })),
      });
      Modal.success({ content: "调拨单已创建(草稿)" });
      setCreateOpen(false);
      refresh();
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<TransferDoc> = [
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
    { title: "调拨日期", dataIndex: "docDate", width: 110, render: (v: string) => fmtDate(v) },
    {
      title: "源仓 → 目的仓",
      width: 240,
      render: (_v, r) => `${whName(r.fromWarehouseId)} → ${whName(r.toWarehouseId)}`,
    },
    {
      title: "行数",
      width: 70,
      render: (_v, r) => r.items?.length ?? "-",
    },
    {
      title: "参考金额",
      dataIndex: "totalAmount",
      width: 110,
      align: "right",
      className: "num-cell",
      render: (v: string) => Number(v).toFixed(2),
    },
    { title: "创建人", dataIndex: "creator", width: 90 },
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
            <a key="submit" className="action-submit" onClick={() => doAction(() => transferApi.submit(row.id), "已提交审批")}>
              提交
            </a>,
            <Popconfirm key="void" title="确认作废该调拨单?" onConfirm={() => doAction(() => transferApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (isWriter && s === "pending") {
          btns.push(
            <Popconfirm
              key="approve"
              title="审批即执行调拨:源仓扣减并入库目的仓,源仓库存不足将整单回滚。确认?"
              onConfirm={() => doAction(() => transferApi.approve(row.id), "调拨已执行完成")}
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
        title="调拨单"
        extra={
          isWriter && (
            <Button type="primary" onClick={openCreate}>
              新建调拨单
            </Button>
          )
        }
        filter={
          <Form form={form} layout="inline" onFinish={(v) => { setPage(1); onSearch(v); }}>
            <Form.Item label="单号" name="docNo">
              <Input allowClear style={{ width: 150 }} />
            </Form.Item>
            <Form.Item label="源仓" name="fromWarehouseId">
              <Select allowClear placeholder="全部" style={{ width: 160 }} options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))} />
            </Form.Item>
            <Form.Item label="目的仓" name="toWarehouseId">
              <Select allowClear placeholder="全部" style={{ width: 160 }} options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))} />
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
          scroll: { x: 1000 },
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
        title="新建调拨单"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={860}
        extra={
          <Button type="primary" loading={saving} onClick={onCreate}>
            保存为草稿
          </Button>
        }
      >
        <Form layout="vertical">
          <Space wrap size={24}>
            <Form.Item label="调拨日期">
              <DatePicker value={docDate} onChange={(d) => setDocDate(d ?? dayjs())} />
            </Form.Item>
            <Form.Item label="源仓库" required>
              <Select
                placeholder="选择源仓库"
                style={{ width: 180 }}
                options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
                value={fromWh}
                onChange={setFromWh}
              />
            </Form.Item>
            <Form.Item label="目的仓库" required>
              <Select
                placeholder="选择目的仓库"
                style={{ width: 180 }}
                options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
                value={toWh}
                onChange={setToWh}
              />
            </Form.Item>
          </Space>
          <Form.Item label="备注">
            <Input.TextArea
              rows={2}
              onChange={(e) => form.setFieldValue("remark", e.target.value)}
            />
          </Form.Item>
        </Form>
        <div style={{ fontWeight: 600, marginBottom: 8 }}>调拨明细(批次跟随源批,库存审批时扣减)</div>
        <Table
          rowKey="key"
          size="small"
          dataSource={lines}
          pagination={false}
          columns={[
            {
              title: "物品",
              width: 220,
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
              width: 120,
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
              width: 120,
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
              title: "源库位",
              width: 130,
              render: (_v: unknown, l) =>
                fromWarehouse?.enableLocation ? (
                  <Select
                    allowClear
                    placeholder="必填"
                    style={{ width: "100%" }}
                    value={l.fromLocationId}
                    options={fromLocations.map((x) => ({ label: x.locationCode, value: x.id }))}
                    onChange={(v) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, fromLocationId: v } : x)))}
                  />
                ) : (
                  <span style={{ color: "#999" }}>-</span>
                ),
            },
            {
              title: "目的库位",
              width: 130,
              render: (_v: unknown, l) =>
                toWarehouse?.enableLocation ? (
                  <Select
                    allowClear
                    placeholder="必填"
                    style={{ width: "100%" }}
                    value={l.toLocationId}
                    options={toLocations.map((x) => ({ label: x.locationCode, value: x.id }))}
                    onChange={(v) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, toLocationId: v } : x)))}
                  />
                ) : (
                  <span style={{ color: "#999" }}>-</span>
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
        title={`调拨单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={760}
      >
        {detail && (
          <>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="调拨日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="源仓">{whName(detail.fromWarehouseId)}</Descriptions.Item>
              <Descriptions.Item label="目的仓">{whName(detail.toWarehouseId)}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <DocStatusTag status={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="审批人">{detail.approver ?? "-"}</Descriptions.Item>
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
                { title: "参考单价", dataIndex: "unitPrice", width: 100, align: "right", className: "num-cell" },
                { title: "行备注", dataIndex: "lineRemark", render: (v) => v ?? "-" },
              ]}
            />
          </>
        )}
      </Modal>

      <Modal
        title={`驳回调拨单 - ${rejectTarget?.docNo ?? ""}`}
        open={!!rejectTarget}
        onCancel={() => { setRejectTarget(null); form.setFieldValue("reason", ""); }}
        onOk={() => {
          const v = form.getFieldValue("reason") as string;
          if (!v?.trim()) return;
          doAction(() => transferApi.reject(rejectTarget!.id, v.trim()), "已驳回");
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
