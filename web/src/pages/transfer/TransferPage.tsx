// 调拨单(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// 新建 Drawer + 详情 + 状态机操作(提交/审批执行/驳回/作废)
// 审批即执行:同一事务源仓扣减 + 目的仓入库

import { useEffect, useRef, useState } from "react";
import { Button, Col, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Row, Segmented, Select, Space, Table, theme } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import dayjs, { type Dayjs } from "dayjs";
import { fmtDate } from "../../utils/format";
import { itemApi, transferApi, warehouseApi } from "../../api";
import type { Item, Location, Warehouse } from "../../types";
import type { TransferDoc, DocLine } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { DocStatusTag, docStatusLabel } from "../../components/DocStatusTag";
import { PrintDocModal, printHeader, type PrintDocData } from "../../components/PrintDocModal";

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
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端 @RequirePermission 拦截)
  const canEdit = hasPerm("transfer:edit");
  const canSubmit = hasPerm("transfer:submit");
  const canApprove = hasPerm("transfer:approve");
  const canReject = hasPerm("transfer:reject");
  const canVoid = hasPerm("transfer:void");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<TransferDoc | null>(null);
  const [printOpen, setPrintOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  // 非空为编辑模式(草稿/已驳回单),Drawer 复用新建表单
  const [editId, setEditId] = useState<number | null>(null);
  const [rejectTarget, setRejectTarget] = useState<TransferDoc | null>(null);
  // V17 主表/明细视图切换(组件内状态,默认主表)
  const [viewMode, setViewMode] = useState<"main" | "line">("main");
  const [saving, setSaving] = useState(false);
  const [createForm] = Form.useForm();
  const actionRef = useRef<ActionType>();
  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();
  const [lines, setLines] = useState<Array<{ key: number; itemId?: number; qty?: number; unitPrice?: number; fromLocationId?: number; toLocationId?: number; vehicleNo?: string }>>([{ key: 1 }]);
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

  // V17 明细行视图:请求 /lines(共享筛选 + 物品关键字)
  const lineRequest = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    fromWarehouseId?: number;
    toWarehouseId?: number;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
  }) => {
    const res = await transferApi.lines({
      docNo: params.docNo,
      fromWarehouseId: params.fromWarehouseId,
      toWarehouseId: params.toWarehouseId,
      status: params.status,
      from: params.from,
      to: params.to,
      itemKeyword: params.itemKeyword,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  // V17 明细行点击单据号:拉取整单详情复用现有详情弹窗
  const openLineDetail = async (r: DocLine) => {
    try {
      setDetail(await transferApi.get(r.docId));
    } catch {
      // 拦截器已提示
    }
  };

  // V17 明细视图列(共享筛选字段 + 拍平行字段;物品关键字仅明细视图渲染)
  const lineColumns: ProColumns<DocLine>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "单号", allowClear: true },
      render: (_v, r) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => openLineDetail(r)}>
          {r.docNo}
        </a>
      ),
    },
    { title: "日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate ?? undefined) },
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
    { title: "源仓名称", dataIndex: "fromWarehouseName", width: 130, search: false, ellipsis: true },
    { title: "目的仓名称", dataIndex: "toWarehouseName", width: 130, search: false, ellipsis: true },
    { title: "行号", dataIndex: "lineNo", width: 60, align: "center", search: false, render: (_v, r) => (r.lineNo == null ? "-" : r.lineNo) },
    { title: "物品", width: 190, search: false, ellipsis: true, render: (_v, r) => `${r.itemCode} ${r.itemName}` },
    { title: "规格", dataIndex: "spec", width: 90, search: false, ellipsis: true },
    { title: "单位", dataIndex: "unit", width: 60, search: false },
    { title: "数量", dataIndex: "quantity", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => (r.quantity == null ? "-" : Number(r.quantity).toFixed(2)) },
    { title: "单价", dataIndex: "unitPrice", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => (r.unitPrice == null ? "-" : Number(r.unitPrice).toFixed(2)) },
    { title: "状态", dataIndex: "status", width: 90, valueEnum: STATUS_ENUM, render: (_v, r) => <DocStatusTag status={r.status} /> },
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
    { title: "物品", dataIndex: "itemKeyword", hideInTable: true, fieldProps: { placeholder: "编码或名称关键字" } },
  ];

  const doAction = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      Modal.success({ content: msg });
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    }
  };

  // V14 打印:调拨单详情 VO 转 A4 打印版数据(承运商等空值字段自动过滤)
  const buildPrintData = (d: TransferDoc): PrintDocData => ({
    title: "调拨单",
    docNo: d.docNo,
    header: printHeader([
      ["调拨日期", fmtDate(d.docDate)],
      ["源仓", whName(d.fromWarehouseId)],
      ["目的仓", whName(d.toWarehouseId)],
      ["承运商", d.carrier],
      ["创建人", d.creator],
      ["审批人", d.approver],
    ]),
    columns: [
      { title: "行号", align: "center" },
      { title: "物品" },
      { title: "数量", align: "right" },
      { title: "参考单价", align: "right" },
      { title: "行备注" },
    ],
    rows: (d.items ?? []).map((l) => [
      String(l.lineNo),
      `${l.itemCode} ${l.itemName}`,
      Number(l.qty).toFixed(4),
      Number(l.unitPrice).toFixed(2),
      l.lineRemark ?? "",
    ]),
    totals: ["", "", "参考金额合计", Number(d.totalAmount).toFixed(2), ""],
    status: docStatusLabel(d.status),
    remark: d.remark,
  });

  const openCreate = () => {
    createForm.resetFields();
    setLines([{ key: 1 }]);
    setFromWh(undefined);
    setToWh(undefined);
    setFromLocations([]);
    setToLocations([]);
    setDocDate(dayjs());
    setEditId(null);
    setCreateOpen(true);
  };

  // 编辑:GET 详情回填表头 + 行明细(行 key 用 1..n,与新建的自增 key 规则一致)
  const openEdit = (row: TransferDoc) => {
    transferApi
      .get(row.id)
      .then((doc) => {
        createForm.resetFields();
        createForm.setFieldsValue({ remark: doc.remark ?? "", carrier: doc.carrier ?? undefined });
        setLines((doc.items ?? []).map((l, i) => ({
          key: i + 1,
          itemId: l.itemId,
          qty: Number(l.qty),
          unitPrice: l.unitPrice != null ? Number(l.unitPrice) : undefined,
          fromLocationId: l.fromLocationId ?? undefined,
          toLocationId: l.toLocationId ?? undefined,
          vehicleNo: l.vehicleNo ?? undefined,
        })));
        setFromWh(doc.fromWarehouseId);
        setToWh(doc.toWarehouseId);
        setDocDate(dayjs(doc.docDate));
        setEditId(doc.id);
        setCreateOpen(true);
      })
      .catch(() => undefined);
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
    // V10 承运商(可空)
    const carrier = createForm.getFieldValue("carrier");
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
      const payload = {
        docDate: docDate.format("YYYY-MM-DD"),
        fromWarehouseId: fromWh,
        toWarehouseId: toWh,
        remark,
        carrier,
        items: valid.map((l) => ({
          itemId: l.itemId!,
          qty: l.qty!,
          unitPrice: l.unitPrice,
          fromLocationId: l.fromLocationId,
          toLocationId: l.toLocationId,
          vehicleNo: l.vehicleNo,
        })),
      };
      if (editId != null) {
        await transferApi.update(editId, payload);
        Modal.success({ content: "调拨单已保存(仍为可编辑状态)" });
      } else {
        await transferApi.create(payload);
        Modal.success({ content: "调拨单已创建(草稿)" });
      }
      setCreateOpen(false);
      setEditId(null);
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
        if (canEdit && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="edit" className="action-edit" onClick={() => openEdit(row)}>
              编辑
            </a>,
          );
        }
        if (canSubmit && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="submit" className="action-submit" onClick={() => doAction(() => transferApi.submit(row.id), "已提交审批")}>
              提交
            </a>,
          );
        }
        if (canVoid && (s === "draft" || s === "rejected")) {
          btns.push(
            <Popconfirm key="void" title="确认作废该调拨单?" onConfirm={() => doAction(() => transferApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (s === "pending") {
          if (canApprove) {
            btns.push(
              <Popconfirm
                key="approve"
                title="审批即执行调拨:源仓扣减并入库目的仓,源仓库存不足将整单回滚。确认?"
                onConfirm={() => doAction(() => transferApi.approve(row.id), "调拨已执行完成")}
              >
                <a className="action-approve">审批执行</a>
              </Popconfirm>,
            );
          }
          if (canReject) {
            btns.push(
              <a key="reject" className="action-reject" onClick={() => setRejectTarget(row)}>
                驳回
              </a>,
            );
          }
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
        columns={viewMode === "line" ? (lineColumns as unknown as ProColumns<TransferDoc>[]) : columns}
        request={viewMode === "line" ? (lineRequest as unknown as typeof request) : request}
        headerTitle={
          <Segmented
            options={[
              { label: "主表", value: "main" },
              { label: "明细", value: "line" },
            ]}
            value={viewMode}
            onChange={(v) => {
              setViewMode(v as "main" | "line");
              actionRef.current?.reload();
            }}
          />
        }
        options={false}
        scroll={{ x: 1000 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            // 调拨无独立 :create 码,按约定复用 :edit(能编辑即可新建)
            canEdit && (
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
        title={editId != null ? "编辑调拨单" : "新建调拨单"}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={860}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮(文案保持现状,loading 态保留)
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setCreateOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={onCreate}>
                {editId != null ? "保存" : "保存为草稿"}
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={createForm} layout="vertical" requiredMark={false}>
          {/* 表头字段两列对齐(统一规格:两列上限);行明细 Table 不包 Col,联动零改动 */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="调拨日期">
                <DatePicker value={docDate} onChange={(d) => setDocDate(d ?? dayjs())} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="源仓库" required>
                <Select
                  placeholder="选择源仓库"
                  style={{ width: "100%" }}
                  options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
                  value={fromWh}
                  onChange={setFromWh}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="目的仓库" required>
                <Select
                  placeholder="选择目的仓库"
                  style={{ width: "100%" }}
                  options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
                  value={toWh}
                  onChange={setToWh}
                />
              </Form.Item>
            </Col>
            {/* V10 承运商(可空) */}
            <Col span={12}>
              <Form.Item label="承运商" name="carrier">
                <Input placeholder="可选" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item label="备注" name="remark">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
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
              // V9 车牌(行内输入,可空)
              title: "车牌",
              width: 130,
              render: (_v: unknown, l) => (
                <Input
                  placeholder="可选"
                  value={l.vehicleNo}
                  onChange={(e) => setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, vehicleNo: e.target.value || undefined } : x)))}
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
        title={`调拨单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={<Button onClick={() => setPrintOpen(true)}>打印</Button>}
        width={960}
        className="doc-detail-modal"
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

      {/* V14 打印 Modal:详情数据已在 detail state,直接转换渲染 */}
      <PrintDocModal
        open={printOpen}
        onClose={() => setPrintOpen(false)}
        data={detail ? buildPrintData(detail) : null}
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
