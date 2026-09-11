// 盘点单(一期新增)
// 新建(整仓/指定物品,保存即按当前余额生成 bookQty 快照)+ 录入实盘 + 刷新快照
// + 差异生成调整单(盘盈/盘亏各一张)+ 状态机操作

import { useEffect, useState } from "react";
import { Button, DatePicker, Descriptions, Drawer, Form, Input, Modal, Popconfirm, Select, Space, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { fmtDate } from "../../utils/format";
import { itemApi, stocktakeApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StocktakeDoc, StocktakeLine } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";
import { DocStatusTag } from "../../components/DocStatusTag";

export function StocktakePage() {
  const user = getUser();
  const isAdmin = user?.role === "admin";
  const isWriter = user?.role === "admin" || user?.role === "operator";
  const [rows, setRows] = useState<StocktakeDoc[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<StocktakeDoc | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [actualDoc, setActualDoc] = useState<StocktakeDoc | null>(null);
  const [actuals, setActuals] = useState<Record<number, number | null>>({});
  const [rejectTarget, setRejectTarget] = useState<StocktakeDoc | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [createForm] = Form.useForm();

  const whName = (id: number) => warehouses.find((w) => w.id === id)?.warehouseName ?? `#${id}`;

  const onSearch = async (values: Record<string, unknown>, pg = 1, ps = 20) => {
    setLoading(true);
    try {
      const res = await stocktakeApi.list({
        warehouseId: values.warehouseId as number | undefined,
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

  const onCreate = async () => {
    const v = await createForm.validateFields();
    if (v.scopeType === "item" && (!v.itemIds || v.itemIds.length === 0)) {
      Modal.error({ content: "指定物品盘点需至少选择一个物品" });
      return;
    }
    setSaving(true);
    try {
      const doc = await stocktakeApi.create({
        warehouseId: v.warehouseId,
        docDate: (v.docDate as Dayjs).format("YYYY-MM-DD"),
        scopeType: v.scopeType,
        itemIds: v.scopeType === "item" ? v.itemIds : undefined,
        remark: v.remark,
      });
      Modal.success({ content: `盘点单已创建,已按当前余额生成快照(${doc.items?.length ?? 0} 行)` });
      setCreateOpen(false);
      createForm.resetFields();
      refresh();
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const openActual = async (row: StocktakeDoc) => {
    const full = await stocktakeApi.get(row.id);
    setActualDoc(full);
    setActuals(Object.fromEntries((full.items ?? []).map((l) => [l.id, l.actualQty ? Number(l.actualQty) : null])));
  };

  const saveActual = async () => {
    if (!actualDoc) return;
    setSaving(true);
    try {
      const lines = (actualDoc.items ?? [])
        .filter((l) => actuals[l.id] !== undefined)
        .map((l) => ({ lineId: l.id, actualQty: actuals[l.id] }));
      const full = await stocktakeApi.enterActual(actualDoc.id, lines);
      setActualDoc(full);
      setActuals(Object.fromEntries((full.items ?? []).map((l) => [l.id, l.actualQty ? Number(l.actualQty) : null])));
      Modal.success({ content: "实盘数量已保存,差异已重算" });
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<StocktakeDoc> = [
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
    { title: "盘点日期", dataIndex: "docDate", width: 110, render: (v: string) => fmtDate(v) },
    { title: "仓库", width: 140, ellipsis: true, render: (_v, r) => whName(r.warehouseId) },
    {
      title: "范围",
      dataIndex: "scopeType",
      width: 100,
      render: (v: string) => (v === "all" ? "整仓" : "指定物品"),
    },
    { title: "行数", width: 70, render: (_v, r) => r.items?.length ?? "-" },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      render: (v: string) => <DocStatusTag status={v} />,
    },
    {
      title: "操作",
      width: 300,
      fixed: "right" as const,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (isWriter && (s === "draft" || s === "pending")) {
          btns.push(<a key="actual" className="action-submit" onClick={() => openActual(row)}>录入实盘</a>);
          btns.push(
            <a key="refresh" className="action-submit" onClick={() => doAction(() => stocktakeApi.refreshBook(row.id), "快照已刷新")}>
              刷新快照
            </a>,
          );
        }
        if (isWriter && s === "approved") {
          btns.push(
            <Popconfirm
              key="adjust"
              title="将按差异生成盘盈/盘亏调整单(草稿),确认?"
              onConfirm={() => doAction(() => stocktakeApi.generateAdjust(row.id), "调整单已生成,请到库存调整页审批执行")}
            >
              <a className="action-approve">生成调整单</a>
            </Popconfirm>,
          );
        }
        if (isWriter && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="submit" className="action-submit" onClick={() => doAction(() => stocktakeApi.submit(row.id), "已提交审批")}>
              提交
            </a>,
            <Popconfirm key="void" title="确认作废该盘点单?" onConfirm={() => doAction(() => stocktakeApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (isWriter && s === "pending") {
          btns.push(
            <a key="approve" className="action-approve" onClick={() => doAction(() => stocktakeApi.approve(row.id), "已审批通过")}>
              审批
            </a>,
            <a key="reject" className="action-reject" onClick={() => setRejectTarget(row)}>
              驳回
            </a>,
          );
        }
        return <Space size={10}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  const statusOptions = [
    { label: "草稿", value: "draft" },
    { label: "待审批", value: "pending" },
    { label: "已审批", value: "approved" },
    { label: "已驳回", value: "rejected" },
    { label: "已作废", value: "voided" },
  ];

  const lineTable = (lines: StocktakeLine[], editable: boolean) => (
    <Table
      size="small"
      rowKey="id"
      dataSource={lines}
      pagination={false}
      scroll={{ x: 800 }}
      columns={[
        { title: "行号", dataIndex: "lineNo", width: 50 },
        { title: "物品", dataIndex: "itemName", render: (v: string, r) => `${r.itemCode} ${v}` },
        { title: "账面量", dataIndex: "bookQty", width: 100, align: "right", className: "num-cell" },
        {
          title: "实盘量",
          dataIndex: "actualQty",
          width: 130,
          render: (v: string | null, r) =>
            editable ? (
              <Input
                key={`${r.id}-${v ?? ""}`}
                defaultValue={v ?? ""}
                placeholder="未盘"
                onBlur={(e) => {
                  const raw = e.target.value.trim();
                  setActuals((m) => ({ ...m, [r.id]: raw === "" ? null : Number(raw) }));
                }}
              />
            ) : (
              v ?? "-"
            ),
        },
        {
          title: "差异(实-账)",
          dataIndex: "diffQty",
          width: 110,
          align: "right",
          className: "num-cell",
          render: (v: string | null, r) => {
            if (v == null) return "-";
            const n = Number(v);
            return (
              <span style={{ color: n > 0 ? "#389e0d" : n < 0 ? "#cf1322" : undefined }}>
                {n > 0 ? `+${n}` : n}
              </span>
            );
          },
        },
      ]}
    />
  );

  return (
    <>
      <ListPageShell
        title="盘点单"
        extra={
          isWriter && (
            <Button type="primary" onClick={() => setCreateOpen(true)}>
              新建盘点单
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
          scroll: { x: 1100 },
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
        title="新建盘点单(保存即生成账面快照)"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={480}
        extra={
          <Button type="primary" loading={saving} onClick={onCreate}>
            创建
          </Button>
        }
      >
        <Form form={createForm} layout="vertical" initialValues={{ docDate: dayjs(), scopeType: "all" }}>
          <Form.Item label="盘点日期" name="docDate" rules={[{ required: true }]}>
            <DatePicker style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="仓库" name="warehouseId" rules={[{ required: true, message: "请选择仓库" }]}>
            <Select
              placeholder="选择仓库"
              options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
            />
          </Form.Item>
          <Form.Item label="盘点范围" name="scopeType">
            <Select
              options={[
                { label: "整仓盘点", value: "all" },
                { label: "指定物品", value: "item" },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(a, b) => a.scopeType !== b.scopeType}>
            {({ getFieldValue }) =>
              getFieldValue("scopeType") === "item" ? (
                <Form.Item label="物品" name="itemIds" rules={[{ required: true, message: "请选择物品" }]}>
                  <Select
                    mode="multiple"
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择参与盘点的物品"
                    options={items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }))}
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={`录入实盘 - ${actualDoc?.docNo ?? ""}(差异 = 实盘 - 账面)`}
        open={!!actualDoc}
        onClose={() => setActualDoc(null)}
        width={720}
        extra={
          <Button type="primary" loading={saving} onClick={saveActual}>
            保存实盘
          </Button>
        }
      >
        <div style={{ color: "#999", marginBottom: 8 }}>
          留空表示该行为"未盘"(不计差异);保存后差异自动重算。
        </div>
        {actualDoc && lineTable(actualDoc.items ?? [], true)}
      </Drawer>

      <Modal
        title={`盘点单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={760}
      >
        {detail && (
          <>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="盘点日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="仓库">{whName(detail.warehouseId)}</Descriptions.Item>
              <Descriptions.Item label="范围">{detail.scopeType === "all" ? "整仓" : "指定物品"}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <DocStatusTag status={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="审批人">{detail.approver ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="驳回原因" span={3}>{detail.rejectReason ?? "-"}</Descriptions.Item>
            </Descriptions>
            {lineTable(detail.items ?? [], false)}
          </>
        )}
      </Modal>

      <Modal
        title={`驳回盘点单 - ${rejectTarget?.docNo ?? ""}`}
        open={!!rejectTarget}
        onCancel={() => { setRejectTarget(null); form.setFieldValue("reason", ""); }}
        onOk={() => {
          const v = form.getFieldValue("reason") as string;
          if (!v?.trim()) return;
          doAction(() => stocktakeApi.reject(rejectTarget!.id, v.trim()), "已驳回");
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
