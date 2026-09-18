// 销售订单列表(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// 状态机操作列:提交(草稿/驳回) / 审批 / 驳回 / 关闭 / 作废(admin)
// 详情 Modal 展示价税三列 + 行发货进度

import { useEffect, useRef, useState , useMemo} from "react";
import { Button, Descriptions, Input, Modal, Popconfirm, Segmented, Space, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import dayjs from "dayjs";
import { fmtDate, fmtDateTime, fmtMoney, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { DocDetailHeader, DocAuditLine } from "../../components/DocDetailSections";
import { salesApi, customerApi, warehouseApi } from "../../api";
import { ExportButton } from "../../components/ExportButton";
import { PrintDocModal, printHeader, type PrintDocData } from "../../components/PrintDocModal";
import type { Warehouse } from "../../types";
import type { SalesOrder, DocLine } from "../../types/phase1";
import type { Customer } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { DocStatusTag, docStatusLabel } from "../../components/DocStatusTag";
import { proTableRequest } from "../../utils/proTable";

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
  approved: { text: "已审批" },
  completed: { text: "已完成" },
  rejected: { text: "已驳回" },
  closed: { text: "已关闭" },
  voided: { text: "已作废" },
};

export function SalesOrderListPage() {
  const { hasPerm } = usePermission();  const canExport = hasPerm("sales-order:export");
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端 @RequirePerm 拦截)
  const canEdit = hasPerm("sales-order:edit");
  const canSubmit = hasPerm("sales-order:submit");
  const canApprove = hasPerm("sales-order:approve");
  const canReject = hasPerm("sales-order:reject");
  const canClose = hasPerm("sales-order:close");
  const canVoid = hasPerm("sales-order:void");
  const navigate = useNavigate();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<SalesOrder | null>(null);
  const [printOpen, setPrintOpen] = useState(false);
  const [rejectTarget, setRejectTarget] = useState<SalesOrder | null>(null);
  // V17 主表/明细视图切换(组件内状态,默认主表)
  const [viewMode, setViewMode] = useState<"main" | "line">("main");
  const actionRef = useRef<ActionType>();

  // 客户/仓库下拉数据源(异步加载,仅用于筛选项与名称展示)
  useEffect(() => {
    customerApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setCustomers(r.rows))
      .catch(() => undefined);
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
    // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

  // 支持 URL 带 ?status= 直达(仪表盘待办"待审批"跳转预置筛选)

  const [searchParams] = useSearchParams();

  const urlStatus = searchParams.get("status") || undefined;
  // 出入库"关联单号"带 ?docNo= 跳转过来:预填筛选并(仅一条结果时)自动开详情
  const urlDocNo = searchParams.get("docNo") || undefined;
  // 自动开详情防重复触发标志(每页生命周期内只触发一次)
  const autoOpened = useRef(false);


  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { docNo?: string; customerId?: number; status?: string; from?: string; to?: string }) => {
      setFilterParams({
        docNo: p.docNo,
        customerId: p.customerId,
        status: p.status,
        from: p.from,
        to: p.to,
      });
      return {
        docNo: p.docNo,
        customerId: p.customerId,
        status: p.status,
        from: p.from,
        to: p.to,
      };
    },
    salesApi.list,
    // 关联单号跳转且仅命中一条时自动开详情(只触发一次,不影响其他筛选)
    (res) => {
      if (urlDocNo && res.total === 1 && res.rows.length === 1 && !autoOpened.current) {
        autoOpened.current = true;
        setDetail(res.rows[0]);
      }
    },
  );

  // V17 明细行视图:请求 /lines(共享筛选 + 物品关键字/批次号)
  const lineRequest = proTableRequest(
    (p: { docNo?: string; customerId?: number; status?: string; from?: string; to?: string; itemKeyword?: string; batchNo?: string }) => ({
      docNo: p.docNo,
      customerId: p.customerId,
      status: p.status,
      from: p.from,
      to: p.to,
      itemKeyword: p.itemKeyword,
      batchNo: p.batchNo,
    }),
    salesApi.lines,
  );

  // V17 明细行点击单据号:拉取整单详情复用现有详情弹窗
  const openLineDetail = async (r: DocLine) => {
    try {
      setDetail(await salesApi.get(r.docId));
    } catch {
      // 拦截器已提示
    }
  };

  // V17 明细视图列(共享筛选字段 + 拍平行字段;物品关键字/批次号仅明细视图渲染)
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
      title: "客户",
      dataIndex: "customerId",
      width: 150,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: customers.map((s) => ({ label: s.customerName, value: s.id })),
      },
      render: (_v, r) => r.customerName ?? "-",
    },
    { title: "行号", dataIndex: "lineNo", width: 60, align: "center", search: false, render: (_v, r) => (r.lineNo == null ? "-" : r.lineNo) },
    { title: "物品", width: 190, search: false, ellipsis: true, render: (_v, r) => `${r.itemCode} ${r.itemName}` },
    { title: "规格", dataIndex: "spec", width: 90, search: false, ellipsis: true },
    { title: "单位", dataIndex: "unit", width: 60, search: false },
    { title: "数量", dataIndex: "quantity", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtQty(r.quantity) },
    { title: "单价", dataIndex: "unitPrice", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.unitPrice) },
    { title: "含税单价", dataIndex: "taxPrice", width: 100, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.taxPrice) },
    { title: "税率(%)", dataIndex: "taxRate", width: 80, align: "right", className: "num-cell", search: false },
    { title: "金额", dataIndex: "amount", width: 100, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.amount) },
    { title: "税额", dataIndex: "taxAmount", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.taxAmount) },
    { title: "价税合计", dataIndex: "taxInclusiveTotal", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => <b>{fmtMoney(r.taxInclusiveTotal)}</b> },
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
  // 导出 URL:带当前筛选条件(导出不分页)
  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    if (filterParams.docNo !== undefined && filterParams.docNo !== "") p.set("docNo", String(filterParams.docNo));
    if (filterParams.customerId !== undefined && filterParams.customerId !== "") p.set("customerId", String(filterParams.customerId));
    if (filterParams.status !== undefined && filterParams.status !== "") p.set("status", String(filterParams.status));
    if (filterParams.from !== undefined && filterParams.from !== "") p.set("from", String(filterParams.from));
    if (filterParams.to !== undefined && filterParams.to !== "") p.set("to", String(filterParams.to));
    const qs = p.toString();
    return "/sales-orders/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);


  const doAction = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      Modal.success({ content: msg });
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    }
  };

  // V14 打印:销售订单详情 VO 转 A4 打印版数据(金额两位小数,空值字段自动过滤)
  const buildPrintData = (d: SalesOrder): PrintDocData => ({
    title: "销售订单",
    docNo: d.docNo,
    header: printHeader([
      ["开单日期", fmtDate(d.docDate)],
      ["客户", customers.find((c) => c.id === d.customerId)?.customerName],
      ["发货仓库", warehouses.find((w) => w.id === d.warehouseId)?.warehouseName],
      ["合同号", d.contractNo],
      ["交货地址", d.shippingAddress],
      ["创建人", d.creator],
      ["审批人", d.approver],
    ]),
    columns: [
      { title: "行号", align: "center" },
      { title: "物品" },
      { title: "订购量", align: "right" },
      { title: "已发货", align: "right" },
      { title: "单价", align: "right" },
      { title: "含税单价", align: "right" },
      { title: "税率(%)", align: "right" },
      { title: "金额", align: "right" },
      { title: "税额", align: "right" },
      { title: "价税合计", align: "right" },
    ],
    rows: (d.items ?? []).map((l) => [
      String(l.lineNo),
      `${l.itemCode} ${l.itemName}`,
      fmtQty(l.orderedQty),
      fmtQty(l.shippedQty),
      fmtMoney(l.unitPrice),
      fmtMoney(l.taxPrice),
      String(l.taxRate),
      fmtMoney(l.amount),
      fmtMoney(l.taxAmount),
      fmtMoney(l.taxInclusiveTotal),
    ]),
    totals: [
      "",
      "",
      "",
      "",
      "",
      "",
      "合计",
      fmtMoney(d.totalAmount),
      fmtMoney(d.totalTaxAmount),
      fmtMoney(d.totalTaxInclusive),
    ],
    status: docStatusLabel(d.status),
    remark: d.remark,
  });

  const columns: ProColumns<SalesOrder>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "订单号", allowClear: true },
      render: (_v, r) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => navigate("/sales-orders/new/" + r.id)}>
          {r.docNo}
        </a>
      ),
    },
    {
      title: "客户",
      dataIndex: "customerId",
      width: 150,
      ellipsis: true,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: customers.map((s) => ({ label: s.customerName, value: s.id })),
      },
      render: (_v, r) =>
        customers.find((s) => s.id === r.customerId)?.customerName ?? `#${r.customerId}`,
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
    { title: "开单日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate) },
    {
      title: "发货仓库",
      width: 130,
      ellipsis: true,
      search: false,
      render: (_v, r) =>
        warehouses.find((w) => w.id === r.warehouseId)?.warehouseName ?? `#${r.warehouseId}`,
    },
    { title: "金额", dataIndex: "totalAmount", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.totalAmount) },
    { title: "税额", dataIndex: "totalTaxAmount", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.totalTaxAmount) },
    { title: "价税合计", dataIndex: "totalTaxInclusive", width: 130, align: "right", className: "num-cell", search: false, render: (_v, r) => <b>{fmtMoney(r.totalTaxInclusive)}</b> },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true, search: false },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 160,
      search: false,
      render: (_v, r) => fmtDateTime(r.createdAt),
    },
    {
      title: "操作",
      width: 330,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (canEdit && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="edit" className="action-edit" onClick={() => navigate(`/sales-orders/new/${row.id}`)}>
              编辑
            </a>,
          );
        }
        if (canSubmit && (s === "draft" || s === "rejected")) {
          btns.push(
            <a
              key="submit"
              className="action-submit"
              onClick={() => doAction(() => salesApi.submit(row.id), "已提交审批")}
            >
              提交
            </a>,
          );
        }
        if (canVoid && s === "draft") {
          btns.push(
            <Popconfirm key="void" title="确认作废该草稿?" onConfirm={() => doAction(() => salesApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (s === "pending") {
          if (canApprove) {
            btns.push(
              <a
                key="approve"
                className="action-approve"
                onClick={() => doAction(() => salesApi.approve(row.id), "已审批并通过预占")}
              >
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
        if (s === "approved") {
          if (canClose) {
            btns.push(
              <Popconfirm key="close" title="关闭后未发货部分不再发货,确认关闭?" onConfirm={() => doAction(() => salesApi.close(row.id), "已关闭")}>
                <a className="action-close">关闭</a>
              </Popconfirm>,
            );
          }
          if (canVoid) {
            btns.push(
              <Popconfirm key="void" title="确认作废该订单?" onConfirm={() => doAction(() => salesApi.voidDoc(row.id), "已作废")}>
                <a className="action-void">作废</a>
              </Popconfirm>,
            );
          }
        }
        // 查看入口:操作列"查看"弹详情弹窗;单号列点击进页面级只读查看页
        btns.push(<a key="detail" onClick={() => setDetail(row)}>查看</a>);
        return <Space size={12}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化,主表/明细共用同一 pageKey)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(
    viewMode === "line" ? (lineColumns as unknown as ProColumns<SalesOrder>[]) : columns,
    "sales-list",
  );
  return (
    <>
      <ProTable<SalesOrder>

      params={{ status: urlStatus, docNo: urlDocNo }}
      form={{ initialValues: { docNo: urlDocNo } }}
      rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无销售订单" /> }}
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
            canExport && (
              <ExportButton key="export" url={exportUrl} filename="销售订单.xlsx" />
            ),
            // 销售订单无独立 :create 码,按约定复用 :edit(能编辑即可新建)
            canEdit && (
              <Link key="new" to="/sales-orders/new">
                <Button type="primary">新建</Button>
              </Link>
            ),
          ],
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title={`销售订单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={<Button onClick={() => setPrintOpen(true)}>打印</Button>}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            {/* TASK-v22c C4 三段式:头部(状态+单号+价税合计) */}
            <DocDetailHeader
              status={detail.status}
              docNo={detail.docNo}
              total={detail.totalTaxInclusive}
            />
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="开单日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="发货仓库">
                {warehouses.find((w) => w.id === detail.warehouseId)?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="金额">{fmtMoney(detail.totalAmount)}</Descriptions.Item>
              <Descriptions.Item label="税额">{fmtMoney(detail.totalTaxAmount)}</Descriptions.Item>
              {/* V9 通用字段 */}
              <Descriptions.Item label="合同号">{detail.contractNo ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="运费">
                {fmtMoney(detail.freight)}
              </Descriptions.Item>
              <Descriptions.Item label="交货地址">{detail.shippingAddress ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="驳回原因">{detail.rejectReason ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>{detail.remark ?? "-"}</Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>订单行(价税分离)</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              scroll={{ x: "max-content" }}
              columns={[
                { title: "行号", dataIndex: "lineNo", width: 44 },
                { title: "物品", dataIndex: "itemName", width: 130, ellipsis: true, render: (v: string, r) => `${r.itemCode} ${v}` },
                { title: "规格快照", dataIndex: "specSnapshot", width: 70, render: (v) => v ?? "-" },
                { title: "订购量", dataIndex: "orderedQty", width: 66, align: "right", className: "num-cell" },
                { title: "已发货", dataIndex: "shippedQty", width: 66, align: "right", className: "num-cell" },
                { title: "不含税单价", dataIndex: "unitPrice", width: 84, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.unitPrice) },
                { title: "含税单价", dataIndex: "taxPrice", width: 84, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.taxPrice) },
                { title: "税率(%)", dataIndex: "taxRate", width: 60, align: "right", className: "num-cell" },
                { title: "金额", dataIndex: "amount", width: 84, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.amount) },
                { title: "税额", dataIndex: "taxAmount", width: 72, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.taxAmount) },
                { title: "价税合计", dataIndex: "taxInclusiveTotal", width: 84, align: "right", className: "num-cell", render: (_v, r) => <b>{fmtMoney(r.taxInclusiveTotal)}</b> },
                {
                  title: "行状态",
                  dataIndex: "closed",
                  width: 64,
                  render: (v: boolean) => (
                    <Tag color={v ? "success" : "default"}>{v ? "已到满" : "未到满"}</Tag>
                  ),
                },
              ]}
            />
            {/* TASK-v22c C4 审计区:创建/审批信息一行灰字 */}
            <DocAuditLine
              creator={detail.creator}
              createdAt={detail.createdAt}
              approver={detail.approver}
              approvedAt={detail.approvedAt}
            />
          </>
        )}
      </Modal>

      <RejectModal
        target={rejectTarget}
        onClose={() => setRejectTarget(null)}
        onConfirm={(reason) => {
          if (rejectTarget) doAction(() => salesApi.reject(rejectTarget.id, reason), "已驳回");
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
  target: SalesOrder | null;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  return (
    <Modal
      title={`驳回订单 - ${target?.docNo ?? ""}`}
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
        placeholder="请填写驳回原因"
      />
    </Modal>
  );
}
