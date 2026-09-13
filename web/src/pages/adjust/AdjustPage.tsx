// 库存调整单(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// gain 盘盈入库 / loss 盘亏出库;审批即执行库存动作;盘点差异生成 + 手工调整共用

import { useEffect, useRef, useState } from "react";
import { Button, Col, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Row, Segmented, Select, Space, Table, theme } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import dayjs, { type Dayjs } from "dayjs";
import { adjustApi, itemApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StockAdjustDoc, DocLine } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { DocStatusTag, docStatusLabel } from "../../components/DocStatusTag";
import { PrintDocModal, printHeader, usePrintNameMaps, type PrintDocData } from "../../components/PrintDocModal";
import { fmtDate } from "../../utils/format";

const TYPE_LABEL: Record<string, string> = { gain: "盘盈(入库)", loss: "盘亏(出库)", scrap: "报损(出库)" };

// 状态机枚举:筛选下拉用 valueEnum,表格单元格仍用 DocStatusTag 自定义渲染(样式不变)
const STATUS_ENUM = {
  draft: { text: "草稿" },
  pending: { text: "待审批" },
  completed: { text: "已完成" },
  rejected: { text: "已驳回" },
  voided: { text: "已作废" },
};

export function AdjustPage() {
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端 @RequirePermission 拦截)
  const canEdit = hasPerm("stock-adjust:edit");
  const canSubmit = hasPerm("stock-adjust:submit");
  const canApprove = hasPerm("stock-adjust:approve");
  const canReject = hasPerm("stock-adjust:reject");
  const canVoid = hasPerm("stock-adjust:void");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<StockAdjustDoc | null>(null);
  const [printOpen, setPrintOpen] = useState(false);
  const { locText } = usePrintNameMaps(); // V14 打印:库位 ID→库位码映射
  const [createOpen, setCreateOpen] = useState(false);
  // 非空为编辑模式(草稿/已驳回单),Drawer 复用新建表单
  const [editId, setEditId] = useState<number | null>(null);
  const [rejectTarget, setRejectTarget] = useState<StockAdjustDoc | null>(null);
  // V17 主表/明细视图切换(组件内状态,默认主表)
  const [viewMode, setViewMode] = useState<"main" | "line">("main");
  const [saving, setSaving] = useState(false);
  const [createForm] = Form.useForm();
  const actionRef = useRef<ActionType>();
  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();
  const [adjustType, setAdjustType] = useState<"gain" | "loss" | "scrap">("loss");
  const [lines, setLines] = useState<Array<{ key: number; itemId?: number; qty?: number; unitPrice?: number; reason?: string; batchId?: number; locationId?: number }>>([{ key: 1 }]);

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

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    warehouseId?: number;
    adjustType?: string;
    status?: string;
  }) => {
    const res = await adjustApi.list({
      docNo: params.docNo,
      warehouseId: params.warehouseId,
      adjustType: params.adjustType,
      status: params.status,
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
    warehouseId?: number;
    adjustType?: string;
    status?: string;
    itemKeyword?: string;
  }) => {
    const res = await adjustApi.lines({
      docNo: params.docNo,
      warehouseId: params.warehouseId,
      adjustType: params.adjustType,
      status: params.status,
      itemKeyword: params.itemKeyword,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  // V17 明细行点击单据号:拉取整单详情复用现有详情弹窗
  const openLineDetail = async (r: DocLine) => {
    try {
      setDetail(await adjustApi.get(r.docId));
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
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      width: 140,
      ellipsis: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
      render: (_v, r) => r.warehouseName ?? "-",
    },
    { title: "行号", dataIndex: "lineNo", width: 60, align: "center", search: false, render: (_v, r) => (r.lineNo == null ? "-" : r.lineNo) },
    { title: "物品", width: 190, search: false, ellipsis: true, render: (_v, r) => `${r.itemCode} ${r.itemName}` },
    { title: "规格", dataIndex: "spec", width: 90, search: false, ellipsis: true },
    { title: "单位", dataIndex: "unit", width: 60, search: false },
    { title: "数量", dataIndex: "quantity", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => (r.quantity == null ? "-" : Number(r.quantity).toFixed(2)) },
    { title: "单价", dataIndex: "unitPrice", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => (r.unitPrice == null ? "-" : Number(r.unitPrice).toFixed(2)) },
    { title: "状态", dataIndex: "status", width: 90, valueEnum: STATUS_ENUM, render: (_v, r) => <DocStatusTag status={r.status} /> },
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

  // V14 打印:调整单详情 VO 转 A4 打印版数据(来源单/参考单价等空值自动过滤)
  const buildPrintData = (d: StockAdjustDoc): PrintDocData => ({
    title: "库存调整单",
    docNo: d.docNo,
    header: printHeader([
      ["调整日期", fmtDate(d.docDate)],
      ["仓库", whName(d.warehouseId)],
      ["类型", TYPE_LABEL[d.adjustType] ?? d.adjustType],
      ["来源单", d.refDocNo],
      ["创建人", d.creator],
      ["审批人", d.approver],
    ]),
    columns: [
      { title: "行号", align: "center" },
      { title: "物品" },
      { title: "库位" },
      { title: "数量", align: "right" },
      { title: "参考单价", align: "right" },
      { title: "原因" },
    ],
    rows: (d.items ?? []).map((l) => [
      String(l.lineNo),
      `${l.itemCode} ${l.itemName}`,
      locText(l.locationId),
      Number(l.qty).toFixed(4),
      l.unitPrice == null ? "" : Number(l.unitPrice).toFixed(2),
      l.reason ?? "",
    ]),
    status: docStatusLabel(d.status),
    remark: d.remark,
  });

  const openCreate = () => {
    createForm.resetFields();
    setAdjustType("loss");
    setLines([{ key: 1 }]);
    setEditId(null);
    setCreateOpen(true);
  };

  // 编辑:GET 详情回填表头 + 行明细(行 key 用 1..n;批次/库位原样保留不展示编辑)
  const openEdit = (row: StockAdjustDoc) => {
    adjustApi
      .get(row.id)
      .then((doc) => {
        createForm.resetFields();
        createForm.setFieldsValue({
          docDate: dayjs(doc.docDate),
          warehouseId: doc.warehouseId,
          remark: doc.remark ?? "",
        });
        setAdjustType(doc.adjustType as "gain" | "loss" | "scrap");
        setLines((doc.items ?? []).map((l, i) => ({
          key: i + 1,
          itemId: l.itemId,
          qty: Number(l.qty),
          unitPrice: l.unitPrice != null ? Number(l.unitPrice) : undefined,
          reason: l.reason ?? undefined,
          batchId: l.batchId ? l.batchId : undefined,
          locationId: l.locationId ? l.locationId : undefined,
        })));
        setEditId(doc.id);
        setCreateOpen(true);
      })
      .catch(() => undefined);
  };

  const onCreate = async () => {
    const head = createForm.getFieldsValue(["docDate", "warehouseId", "remark"]);
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
      const payload = {
        warehouseId: head.warehouseId,
        docDate: (head.docDate as Dayjs).format("YYYY-MM-DD"),
        adjustType,
        remark: head.remark,
        items: valid.map((l) => ({
          itemId: l.itemId!,
          qty: l.qty!,
          unitPrice: l.unitPrice,
          reason: l.reason,
          batchId: l.batchId,
          locationId: l.locationId,
        })),
      };
      if (editId != null) {
        await adjustApi.update(editId, payload);
        Modal.success({ content: "调整单已保存(仍为可编辑状态)" });
      } else {
        await adjustApi.create(payload);
        Modal.success({ content: "调整单已创建(草稿)" });
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

  const columns: ProColumns<StockAdjustDoc>[] = [
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
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
    },
    {
      title: "类型",
      dataIndex: "adjustType",
      width: 110,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: [
          { label: "盘盈(入库)", value: "gain" },
          { label: "盘亏(出库)", value: "loss" },
        ],
      },
      render: (_v, r) => (
        <span style={{ color: r.adjustType === "gain" ? "#389e0d" : "#cf1322" }}>
          {TYPE_LABEL[r.adjustType] ?? r.adjustType}
        </span>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      valueEnum: STATUS_ENUM,
      render: (_v, r) => <DocStatusTag status={r.status} />,
    },
    { title: "调整日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate) },
    {
      title: "仓库",
      width: 130,
      ellipsis: true,
      search: false,
      render: (_v, r) => whName(r.warehouseId),
    },
    {
      title: "来源",
      dataIndex: "refDocNo",
      width: 150,
      search: false,
      render: (_v, r) => r.refDocNo ?? "-",
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
            <a key="submit" className="action-submit" onClick={() => doAction(() => adjustApi.submit(row.id), "已提交审批")}>
              提交
            </a>,
          );
        }
        if (canVoid && (s === "draft" || s === "rejected")) {
          btns.push(
            <Popconfirm key="void" title="确认作废该调整单?" onConfirm={() => doAction(() => adjustApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (s === "pending") {
          if (canApprove) {
            btns.push(
              <Popconfirm
                key="approve"
                title={row.adjustType === "gain" ? "审批即执行盘盈入库。确认?" : "审批即执行盘亏出库(库存不足将回滚)。确认?"}
                onConfirm={() => doAction(() => adjustApi.approve(row.id), "调整已执行完成")}
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
      <ProTable<StockAdjustDoc>
        rowKey="id"
        actionRef={actionRef}
        columns={viewMode === "line" ? (lineColumns as unknown as ProColumns<StockAdjustDoc>[]) : columns}
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
        scroll={{ x: 1050 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            // 库存调整无独立 :create 码,按约定复用 :edit(能编辑即可新建)
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
        title={editId != null ? "编辑库存调整单" : "新建库存调整单"}
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
          {/* 表头字段两列对齐(统一规格:两列上限);行明细 Table 不包 Col,零改动 */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="调整类型" required>
                <Select
                  value={adjustType}
                  onChange={setAdjustType}
                  style={{ width: "100%" }}
                  options={[
                    { label: "盘盈(入库)", value: "gain" },
                    { label: "盘亏(出库)", value: "loss" },
                    // 报损单仅编辑态可能出现(手工报损),新建保持盘盈/盘亏两项
                    ...(editId != null ? [{ label: "报损(出库)", value: "scrap" }] : []),
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="调整日期" name="docDate" initialValue={dayjs()}>
                <DatePicker style={{ width: "100%" }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="仓库" name="warehouseId">
                <Select
                  placeholder="选择仓库"
                  style={{ width: "100%" }}
                  options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
                />
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
        footer={<Button onClick={() => setPrintOpen(true)}>打印</Button>}
        width={960}
        className="doc-detail-modal"
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

      <RejectModal
        target={rejectTarget}
        onClose={() => setRejectTarget(null)}
        onConfirm={(reason) => {
          if (rejectTarget) doAction(() => adjustApi.reject(rejectTarget.id, reason), "已驳回");
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
  target: StockAdjustDoc | null;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  return (
    <Modal
      title={`驳回调整单 - ${target?.docNo ?? ""}`}
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
