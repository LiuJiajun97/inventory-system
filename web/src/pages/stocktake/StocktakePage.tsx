// 盘点单(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// 新建(整仓/指定物品,保存即按当前余额生成 bookQty 快照)+ 录入实盘 + 刷新快照
// + 差异生成调整单(盘盈/盘亏各一张)+ 状态机操作

import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Button, Col, DatePicker, Descriptions, Drawer, Form, Input, Modal, Popconfirm, Row, Segmented, Select, Space, Table, Tag, theme } from "antd";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import dayjs, { type Dayjs } from "dayjs";
import { fmtDate, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { itemApi, stocktakeApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StocktakeDoc, StocktakeLine, DocLine } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { useScopeWarehouseId } from "../../auth/WarehouseScopeContext";
import { DocStatusTag, docStatusLabel } from "../../components/DocStatusTag";
import { proTableRequest } from "../../utils/proTable";
import { PrintDocModal, printHeader, usePrintNameMaps, type PrintDocData } from "../../components/PrintDocModal";

// 状态机枚举:筛选下拉用 valueEnum,表格单元格仍用 DocStatusTag 自定义渲染(样式不变)
const STATUS_ENUM = {
  draft: { text: "草稿" },
  pending: { text: "待审批" },
  approved: { text: "已审批" },
  rejected: { text: "已驳回" },
  voided: { text: "已作废" },
};

export function StocktakePage() {
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端 @RequirePerm 拦截)
  // 录入实盘/刷新快照属编辑动作 → :edit;生成调整单为审批后动作 → :approve
  const canEdit = hasPerm("stocktake:edit");
  const canSubmit = hasPerm("stocktake:submit");
  const canApprove = hasPerm("stocktake:approve");
  const canReject = hasPerm("stocktake:reject");
  const canVoid = hasPerm("stocktake:void");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<StocktakeDoc | null>(null);
  const [printOpen, setPrintOpen] = useState(false);
  const { locText } = usePrintNameMaps(); // V14 打印:库位 ID→库位码映射
  const [createOpen, setCreateOpen] = useState(false);
  const [actualDoc, setActualDoc] = useState<StocktakeDoc | null>(null);
  const [actuals, setActuals] = useState<Record<number, number | null>>({});
  // V9 盘点人/盘点日期(整单统一,随每行实盘提交,可空)
  const [checkerName, setCheckerName] = useState<string>("");
  const [checkDate, setCheckDate] = useState<Dayjs | null>(dayjs());
  const [rejectTarget, setRejectTarget] = useState<StocktakeDoc | null>(null);
  // V17 主表/明细视图切换(组件内状态,默认主表)
  const [viewMode, setViewMode] = useState<"main" | "line">("main");
  const [saving, setSaving] = useState(false);
  const [createForm] = Form.useForm();
  const actionRef = useRef<ActionType>();
  // C3 顶栏仓库快捷切换:表单未选仓库时并入请求参数(表单值优先)
  const scopeWarehouseId = useScopeWarehouseId();
  // 顶栏仓库范围切换后自动刷新表格(scope 并入请求参数;首次挂载由 ProTable 自触发,不重复)
  const scopeMounted = useRef(false);
  useEffect(() => {
    if (scopeMounted.current) actionRef.current?.reload();
    else scopeMounted.current = true;
  }, [scopeWarehouseId]);

  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();

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
    // 支持 URL 带 ?status= 直达(仪表盘待办"待审批"跳转预置筛选)
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const urlStatus = searchParams.get("status") || undefined;

  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { docNo?: string; warehouseId?: number; status?: string }) => ({
      docNo: p.docNo,
      warehouseId: p.warehouseId ?? scopeWarehouseId ?? undefined,
      status: p.status,
    }),
    stocktakeApi.list,
  );

  // V17 明细行视图:请求 /lines(共享筛选 + 物品关键字)
  const lineRequest = proTableRequest(
    (p: { docNo?: string; warehouseId?: number; status?: string; itemKeyword?: string }) => ({
      docNo: p.docNo,
      warehouseId: p.warehouseId ?? scopeWarehouseId ?? undefined,
      status: p.status,
      itemKeyword: p.itemKeyword,
    }),
    stocktakeApi.lines,
  );

  // V17 明细行点击单据号:拉取整单详情复用现有详情弹窗
  const openLineDetail = async (r: DocLine) => {
    try {
      setDetail(await stocktakeApi.get(r.docId));
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
    { title: "账面量", dataIndex: "bookQty", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtQty(r.bookQty) },
    { title: "实盘量", dataIndex: "actualQty", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtQty(r.actualQty) },
    { title: "差异", dataIndex: "diffQty", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtQty(r.diffQty) },
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

  // V14 打印:盘点单详情 VO 转 A4 打印版数据(未盘/无差异行显示空)
  const buildPrintData = (d: StocktakeDoc): PrintDocData => ({
    title: "盘点单",
    docNo: d.docNo,
    header: printHeader([
      ["盘点日期", fmtDate(d.docDate)],
      ["仓库", whName(d.warehouseId)],
      ["范围", d.scopeType === "all" ? "整仓" : "指定物品"],
      ["创建人", d.creator],
      ["审批人", d.approver],
    ]),
    columns: [
      { title: "行号", align: "center" },
      { title: "物品" },
      { title: "库位" },
      { title: "账面量", align: "right" },
      { title: "实盘量", align: "right" },
      { title: "差异(实-账)", align: "right" },
    ],
    rows: (d.items ?? []).map((l) => [
      String(l.lineNo),
      `${l.itemCode} ${l.itemName}`,
      locText(l.locationId),
      fmtQty(l.bookQty),
      l.actualQty == null ? "" : fmtQty(l.actualQty),
      l.diffQty == null ? "" : fmtQty(l.diffQty),
    ]),
    status: docStatusLabel(d.status),
    remark: d.remark,
  });

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
      actionRef.current?.reload();
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
    // V9:盘点人/盘点日期默认空/今天(已有值则回显首行)
    setCheckerName(full.items?.[0]?.checkerName ?? "");
    setCheckDate(full.items?.[0]?.checkDate ? dayjs(full.items[0].checkDate) : dayjs());
  };

  const saveActual = async () => {
    if (!actualDoc) return;
    setSaving(true);
    try {
      const lines = (actualDoc.items ?? [])
        .filter((l) => actuals[l.id] !== undefined)
        .map((l) => ({
          lineId: l.id,
          actualQty: actuals[l.id],
          // V9 盘点人/盘点日期(可空)
          checkerName: checkerName.trim() || undefined,
          checkDate: checkDate ? checkDate.format("YYYY-MM-DD") : undefined,
        }));
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

  const columns: ProColumns<StocktakeDoc>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "单号", allowClear: true },
      render: (_v, r) => (
        // 盘点单无新建/编辑表单页,单号列点击直接开整单详情弹窗(与操作列"查看"同一弹窗,列表行已含 items)
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => setDetail(r)}>
          {r.docNo}
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
      title: "状态",
      dataIndex: "status",
      width: 90,
      valueEnum: STATUS_ENUM,
      render: (_v, r) => <DocStatusTag status={r.status} />,
    },
    { title: "盘点日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate) },
    { title: "仓库", width: 140, ellipsis: true, search: false, render: (_v, r) => whName(r.warehouseId) },
    {
      title: "范围",
      dataIndex: "scopeType",
      width: 100,
      search: false,
      render: (_v, r) => (r.scopeType === "all" ? "整仓" : "指定物品"),
    },
    { title: "行数", width: 70, search: false, render: (_v, r) => r.items?.length ?? "-" },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true, search: false },
    {
      title: "操作",
      width: 370,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (canEdit && (s === "draft" || s === "pending")) {
          btns.push(<a key="actual" className="action-submit" onClick={() => openActual(row)}>录入实盘</a>);
          btns.push(
            <a key="refresh" className="action-submit" onClick={() => doAction(() => stocktakeApi.refreshBook(row.id), "快照已刷新")}>
              刷新快照
            </a>,
          );
        }
        if (canApprove && s === "approved") {
          if (row.adjustGenerated) {
            btns.push(
              <Tag key="adjust" color="default">已生成</Tag>,
            );
          } else {
            btns.push(
              <a
                key="adjust"
                className="action-approve"
                onClick={() => doAction(() => stocktakeApi.generateAdjust(row.id), "调整单已生成,请到库存调整页审批执行")}
              >
                生成调整单
              </a>,
            );
          }
        }
        if (s === "draft" || s === "rejected") {
          if (canSubmit) {
            btns.push(
              <a key="submit" className="action-submit" onClick={() => doAction(() => stocktakeApi.submit(row.id), "已提交审批")}>
                提交
              </a>,
            );
          }
          if (canVoid) {
            btns.push(
              <Popconfirm key="void" title="确认作废该盘点单?" onConfirm={() => doAction(() => stocktakeApi.voidDoc(row.id), "已作废")}>
                <a className="action-void">作废</a>
              </Popconfirm>,
            );
          }
        }
        if (s === "pending") {
          if (canApprove) {
            btns.push(
              <a key="approve" className="action-approve" onClick={() => doAction(() => stocktakeApi.approve(row.id), "已审批通过")}>
                审批
              </a>,
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
        // 查看入口:操作列"查看"弹详情弹窗;单号列点击进页面级只读查看页
        btns.push(<a key="detail" onClick={() => setDetail(row)}>查看</a>);
        return <Space size={10}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
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
          render: (v: string | null) => {
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

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化,主表/明细共用同一 pageKey)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(
    viewMode === "line" ? (lineColumns as unknown as ProColumns<StocktakeDoc>[]) : columns,
    "stocktake",
  );
  return (
    <>
      <ProTable<StocktakeDoc>

      params={{ status: urlStatus }}
      rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无盘点单" /> }}
        actionRef={actionRef}
        columns={rcColumns}
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
        options={{
          density: false,
          reload: false,
          fullScreen: false,
          setting: rcOptionSetting,
        }}
        columnsState={columnsState}
        onColumnsStateChange={onColumnsChange}
        components={rcComponents}
        scroll={rcScroll}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            // 盘点无独立 :create 码,按约定复用 :edit(能编辑即可新建)
            canEdit && (
              <Button key="new" type="primary" onClick={() => setCreateOpen(true)}>
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
        title="新建盘点单(保存即生成账面快照)"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={480}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮"创建"(文案保持现状,loading 态保留)
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setCreateOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={onCreate}>
                创建
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={createForm} layout="vertical" requiredMark={false} initialValues={{ docDate: dayjs(), scopeType: "all" }}>
          {/* 表头字段两列对齐(统一规格:两列上限) */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="盘点日期" name="docDate" rules={[{ required: true }]}>
                <DatePicker style={{ width: "100%" }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="仓库" name="warehouseId" rules={[{ required: true, message: "请选择仓库" }]}>
                <Select
                  placeholder="选择仓库"
                  options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="盘点范围" name="scopeType">
                <Select
                  options={[
                    { label: "整仓盘点", value: "all" },
                    { label: "指定物品", value: "item" },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          {/* 条件字段(指定物品时出现,多选)独占一行 */}
          <Row gutter={16}>
            <Col span={24}>
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
      </Drawer>

      <Drawer
        title={`录入实盘 - ${actualDoc?.docNo ?? ""}(差异 = 实盘 - 账面)`}
        open={!!actualDoc}
        onClose={() => setActualDoc(null)}
        width={860}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮"保存实盘"(文案保持现状,loading 态保留)
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setActualDoc(null)}>取消</Button>
              <Button type="primary" loading={saving} onClick={saveActual}>
                保存实盘
              </Button>
            </Space>
          </div>
        }
      >
        <div style={{ color: "#999", marginBottom: 8 }}>
          留空表示该行为"未盘"(不计差异);保存后差异自动重算。
        </div>
        {/* V9 盘点人/盘点日期(可空,默认今天) */}
        <Row gutter={16} style={{ marginBottom: 12 }}>
          <Col span={12}>
            <div style={{ fontSize: 12, color: "#666", marginBottom: 4 }}>盘点人(可选)</div>
            <Input
              placeholder="可选"
              value={checkerName}
              onChange={(e) => setCheckerName(e.target.value)}
            />
          </Col>
          <Col span={12}>
            <div style={{ fontSize: 12, color: "#666", marginBottom: 4 }}>盘点日期(可选,默认今天)</div>
            <DatePicker
              style={{ width: "100%" }}
              value={checkDate}
              onChange={(d) => setCheckDate(d)}
              allowClear
            />
          </Col>
        </Row>
        {actualDoc && lineTable(actualDoc.items ?? [], true)}
      </Drawer>

      <Modal
        title={`盘点单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={<Button onClick={() => setPrintOpen(true)}>打印</Button>}
        width={960}
        className="doc-detail-modal"
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

      <RejectModal
        target={rejectTarget}
        onClose={() => setRejectTarget(null)}
        onConfirm={(reason) => {
          if (rejectTarget) doAction(() => stocktakeApi.reject(rejectTarget.id, reason), "已驳回");
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
  target: StocktakeDoc | null;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  return (
    <Modal
      title={`驳回盘点单 - ${target?.docNo ?? ""}`}
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
