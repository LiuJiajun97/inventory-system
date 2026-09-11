// 调拨单(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// 新建 Drawer + 详情 + 状态机操作(提交/审批执行/驳回/作废)
// 审批即执行:同一事务源仓扣减 + 目的仓入库

import { useEffect, useRef, useState } from "react";
import { Button, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import dayjs, { type Dayjs } from "dayjs";
import { fmtDate } from "../../utils/format";
import { itemApi, transferApi, warehouseApi } from "../../api";
import type { Item, Location, Warehouse } from "../../types";
import type { TransferDoc } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";
import { DocStatusTag } from "../../components/DocStatusTag";

// ProTable dateRange transform 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

// 状态机枚举:筛选下拉用 valueEnum,表格单元格仍用 DocStatusTag 自定义渲染(样式不变)
const STATUS_ENUM = {
  draft: { text: "草稿" },
  pending: { text: "待审批" },
  completed: { text: "已完成" },
  rejected: { text: "已驳回" },
  voided: { text: "已作废" },
};

export function TransferPage() {
  const user = getUser();
  const isWriter = user?.role === "admin" || user?.role === "operator";
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<TransferDoc | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState<TransferDoc | null>(null);
  const [saving, setSaving] = useState(false);
  const [createForm] = Form.useForm();
  const actionRef = useRef<ActionType>();
  const [lines, setLines] = useState<Array<{ key: number; itemId?: number; qty?: number; unitPrice?: number; fromLocationId?: number; toLocationId?: number }>>([{ key: 1 }]);
  const [fromWh, setFromWh] = useState<number>();
  const [toWh, setToWh] = useState<number>();
  const [fromLocations, setFromLocations] = useState<Location[]>([]);
  const [toLocations, setToLocations] = useState<Location[]>([]);
  const [docDate, setDocDate] = useState<Dayjs>(dayjs());

  const fromWarehouse = warehouses.find((w) => w.id === fromWh);
  const toWarehouse = warehouses.find((w) => w.id === toWh);

  const whName = (id: number) => warehouses.find((w) => w.id === id)?.warehouseName ?? `#${id}`;

  // 仓库/物品下拉数据源(异步加载,仅用于筛选项与名称展示)
  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
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

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    fromWarehouseId?: number;
    toWarehouseId?: number;
    status?: string;
    from?: string;
    to?: string;
  }) => {
    const res = await transferApi.list({
      docNo: params.docNo,
      fromWarehouseId: params.fromWarehouseId,
      toWarehouseId: params.toWarehouseId,
      status: params.status,
      from: params.from,
      to: params.to,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  const doAction = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      Modal.success({ content: msg });
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    }
  };

  const openCreate = () => {
    createForm.resetFields();
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
    const remark = createForm.getFieldValue("remark");
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
        remark,
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
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<TransferDoc>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "单号", allowClear: true },
      render: (_v, row) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => setDetail(row)}>
          {row.docNo}
        </a>
      ),
    },
    {
      title: "源仓",
      dataIndex: "fromWarehouseId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
    },
    {
      title: "目的仓",
      dataIndex: "toWarehouseId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      valueEnum: STATUS_ENUM,
      render: (_v, r) => <DocStatusTag status={r.status} />,
    },
    {
      title: "日期",
      dataIndex: "range",
      valueType: "dateRange",
      hideInTable: true,
      search: {
        transform: (value: [unknown, unknown]) => ({
          from: toDay(value[0]),
          to: toDay(value[1]),
        }),
      },
    },
    { title: "调拨日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate) },
    {
      title: "源仓 → 目的仓",
      width: 240,
      search: false,
      render: (_v, r) => `${whName(r.fromWarehouseId)} → ${whName(r.toWarehouseId)}`,
    },
    {
      title: "行数",
      width: 70,
      search: false,
      render: (_v, r) => r.items?.length ?? "-",
    },
    {
      title: "参考金额",
      dataIndex: "totalAmount",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => Number(r.totalAmount).toFixed(2),
    },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true, search: false },
    {
      title: "操作",
      width: 220,
      fixed: "right" as const,
      search: false,
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

  const itemOptions = items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }));

  return (
    <>
      <ProTable<TransferDoc>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={request}
        headerTitle={false}
        options={false}
        scroll={{ x: 1000 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            isWriter && (
              <Button key="new" type="primary" onClick={openCreate}>
                新建
              </Button>
            ),
          ],
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
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
        <Form form={createForm} layout="vertical">
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
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} />
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

      <RejectModal
        target={rejectTarget}
        onClose={() => setRejectTarget(null)}
        onConfirm={(reason) => {
          if (rejectTarget) doAction(() => transferApi.reject(rejectTarget.id, reason), "已驳回");
          setRejectTarget(null);
        }}
      />
    </>
  );
}

// 驳回弹窗(独立组件:表单实例与列表筛选解耦,避免原"共用 form"的坑)
function RejectModal({
  target,
  onClose,
  onConfirm,
}: {
  target: TransferDoc | null;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  return (
    <Modal
      title={`驳回调拨单 - ${target?.docNo ?? ""}`}
      open={!!target}
      onCancel={onClose}
      onOk={() => {
        if (!reason.trim()) return;
        onConfirm(reason.trim());
        setReason("");
      }}
      okButtonProps={{ disabled: !reason.trim() }}
    >
      <Input.TextArea
        rows={3}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="驳回原因(必填)"
      />
    </Modal>
  );
}
