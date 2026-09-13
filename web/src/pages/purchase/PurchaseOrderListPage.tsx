// 采购订单列表(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// 状态机操作列:提交(草稿/驳回) / 审批 / 驳回 / 关闭 / 作废(admin)
// 详情 Drawer 展示价税三列 + 行到货进度

import { useEffect, useRef, useState , useMemo} from "react";
import { Button, Descriptions, Input, Modal, Popconfirm, Space, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { fmtDate, fmtDateTime } from "../../utils/format";
import { purchaseApi, supplierApi } from "../../api";
import { ExportButton } from "../../components/ExportButton";
import type { PurchaseOrder } from "../../types/phase1";
import type { Supplier } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
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
  approved: { text: "已审批" },
  completed: { text: "已完成" },
  rejected: { text: "已驳回" },
  closed: { text: "已关闭" },
  voided: { text: "已作废" },
};

export function PurchaseOrderListPage() {
  const { hasPerm } = usePermission();  const canExport = hasPerm("purchase-order:export");
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端 @RequirePermission 拦截)
  const canEdit = hasPerm("purchase-order:edit");
  const canSubmit = hasPerm("purchase-order:submit");
  const canApprove = hasPerm("purchase-order:approve");
  const canReject = hasPerm("purchase-order:reject");
  const canClose = hasPerm("purchase-order:close");
  const canVoid = hasPerm("purchase-order:void");
  const navigate = useNavigate();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [detail, setDetail] = useState<PurchaseOrder | null>(null);
  const [rejectTarget, setRejectTarget] = useState<PurchaseOrder | null>(null);
  const actionRef = useRef<ActionType>();

  useEffect(() => {
    supplierApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setSuppliers(r.rows))
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
    // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    supplierId?: number;
    status?: string;
    from?: string;
    to?: string;
  }) => {
    setFilterParams({
      docNo: params.docNo,
      supplierId: params.supplierId,
      status: params.status,
      from: params.from,
      to: params.to,    });
    const res = await purchaseApi.list({
      docNo: params.docNo,
      supplierId: params.supplierId,
      status: params.status,
      from: params.from,
      to: params.to,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };
  // 导出 URL:带当前筛选条件(导出不分页)
  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    if (filterParams.docNo !== undefined && filterParams.docNo !== "") p.set("docNo", String(filterParams.docNo));
    if (filterParams.supplierId !== undefined && filterParams.supplierId !== "") p.set("supplierId", String(filterParams.supplierId));
    if (filterParams.status !== undefined && filterParams.status !== "") p.set("status", String(filterParams.status));
    if (filterParams.from !== undefined && filterParams.from !== "") p.set("from", String(filterParams.from));
    if (filterParams.to !== undefined && filterParams.to !== "") p.set("to", String(filterParams.to));
    const qs = p.toString();
    return "/purchase-orders/export" + (qs ? `?${qs}` : "");
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

  const columns: ProColumns<PurchaseOrder>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "订单号", allowClear: true },
      render: (_v, row) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => setDetail(row)}>
          {row.docNo}
        </a>
      ),
    },
    { title: "下单日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate) },
    {
      title: "供应商",
      dataIndex: "supplierId",
      width: 160,
      ellipsis: true,
      valueType: "select",
      fieldProps: { allowClear: true, placeholder: "全部" },
      render: (_v, r) =>
        suppliers.find((s) => s.id === r.supplierId)?.supplierName ?? `#${r.supplierId}`,
    },
    { title: "金额", dataIndex: "totalAmount", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => Number(r.totalAmount).toFixed(2) },
    { title: "税额", dataIndex: "totalTaxAmount", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => Number(r.totalTaxAmount).toFixed(2) },
    { title: "价税合计", dataIndex: "totalTaxInclusive", width: 130, align: "right", className: "num-cell", search: false, render: (_v, r) => <b>{Number(r.totalTaxInclusive).toFixed(2)}</b> },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true, search: false },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 160,
      search: false,
      render: (_v, r) => fmtDateTime(r.createdAt),
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
    {
      title: "操作",
      width: 260,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (canEdit && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="edit" className="action-edit" onClick={() => navigate(`/purchase-orders/new/${row.id}`)}>
              编辑
            </a>,
          );
        }
        if (canSubmit && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="submit" className="action-submit" onClick={() => doAction(() => purchaseApi.submit(row.id), "已提交审批")}>
              提交
            </a>,
          );
        }
        if (canVoid && s === "draft") {
          btns.push(
            <Popconfirm key="void" title="确认作废该草稿?" onConfirm={() => doAction(() => purchaseApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (s === "pending") {
          if (canApprove) {
            btns.push(
              <a key="approve" className="action-approve" onClick={() => doAction(() => purchaseApi.approve(row.id), "已审批通过")}>
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
              <Popconfirm key="close" title="关闭后未到货部分不再接收,确认关闭?" onConfirm={() => doAction(() => purchaseApi.close(row.id), "已关闭")}>
                <a className="action-close">关闭</a>
              </Popconfirm>,
            );
          }
          if (canVoid) {
            btns.push(
              <Popconfirm key="void" title="确认作废该订单?" onConfirm={() => doAction(() => purchaseApi.voidDoc(row.id), "已作废")}>
                <a className="action-void">作废</a>
              </Popconfirm>,
            );
          }
        }
        return <Space size={12}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  return (
    <>
      <ProTable<PurchaseOrder>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={request}
        headerTitle={false}
        options={false}
        scroll={{ x: 1360 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行);4 字段单行放得下,不显示展开/收起
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            canExport && (
              <ExportButton key="export" url={exportUrl} filename="采购订单.xlsx" />
            ),
            // 采购订单无独立 :create 码,按约定复用 :edit(能编辑即可新建)
            canEdit && (
              <Link key="new" to="/purchase-orders/new">
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
        title={`采购订单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="下单日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="超收比例(%)">
                {Number(detail.allowOverReceiptRate).toFixed(2)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <DocStatusTag status={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="金额">{Number(detail.totalAmount).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="税额">{Number(detail.totalTaxAmount).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="价税合计">
                {Number(detail.totalTaxInclusive).toFixed(2)}
              </Descriptions.Item>
              {/* V9 通用字段 */}
              <Descriptions.Item label="合同号">{detail.contractNo ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="运费">
                {detail.freight == null ? "-" : Number(detail.freight).toFixed(2)}
              </Descriptions.Item>
              <Descriptions.Item label="交货地址">{detail.shippingAddress ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="审批人">{detail.approver ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="驳回原因">{detail.rejectReason ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="备注" span={3}>{detail.remark ?? "-"}</Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>订单行(价税分离)</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              scroll={{ x: 900 }}
              columns={[
                { title: "行号", dataIndex: "lineNo", width: 50 },
                { title: "物品", dataIndex: "itemName", width: 140, render: (v: string, r) => `${r.itemCode} ${v}` },
                { title: "规格快照", dataIndex: "specSnapshot", width: 90, render: (v) => v ?? "-" },
                { title: "订购量", dataIndex: "orderedQty", width: 80, align: "right", className: "num-cell" },
                { title: "已到货", dataIndex: "arrivedQty", width: 80, align: "right", className: "num-cell" },
                { title: "单价", dataIndex: "unitPrice", width: 80, align: "right", className: "num-cell" },
                { title: "税率(%)", dataIndex: "taxRate", width: 70, align: "right", className: "num-cell" },
                { title: "金额", dataIndex: "amount", width: 90, align: "right", className: "num-cell" },
                { title: "税额", dataIndex: "taxAmount", width: 90, align: "right", className: "num-cell" },
                { title: "价税合计", dataIndex: "taxInclusiveTotal", width: 90, align: "right", className: "num-cell" },
                {
                  title: "行状态",
                  dataIndex: "closed",
                  width: 80,
                  render: (v: boolean) => (
                    <Tag color={v ? "success" : "default"}>{v ? "已到满" : "未到满"}</Tag>
                  ),
                },
              ]}
            />
          </>
        )}
      </Modal>

      <RejectModal
        target={rejectTarget}
        onClose={() => setRejectTarget(null)}
        onConfirm={(reason) => {
          if (rejectTarget) doAction(() => purchaseApi.reject(rejectTarget.id, reason), "已驳回");
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
  target: PurchaseOrder | null;
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
