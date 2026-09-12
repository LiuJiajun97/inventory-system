// 销售订单列表(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// 状态机操作列:提交(草稿/驳回) / 审批 / 驳回 / 关闭 / 作废(admin)
// 详情 Modal 展示价税三列 + 行发货进度

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Input, Modal, Popconfirm, Space, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { fmtDate, fmtDateTime } from "../../utils/format";
import { salesApi, customerApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { SalesOrder } from "../../types/phase1";
import type { Customer } from "../../types/phase1";
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
  approved: { text: "已审批" },
  completed: { text: "已完成" },
  rejected: { text: "已驳回" },
  closed: { text: "已关闭" },
  voided: { text: "已作废" },
};

export function SalesOrderListPage() {
  const user = getUser();
  const isWriter = user?.role === "admin" || user?.role === "operator";
  const navigate = useNavigate();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<SalesOrder | null>(null);
  const [rejectTarget, setRejectTarget] = useState<SalesOrder | null>(null);
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
  const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    customerId?: number;
    status?: string;
    from?: string;
    to?: string;
  }) => {
    const res = await salesApi.list({
      docNo: params.docNo,
      customerId: params.customerId,
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

  const columns: ProColumns<SalesOrder>[] = [
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
      title: "操作",
      width: 260,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (isWriter && (s === "draft" || s === "rejected")) {
          btns.push(
            <a key="edit" className="action-edit" onClick={() => navigate(`/sales-orders/new/${row.id}`)}>
              编辑
            </a>,
            <a
              key="submit"
              className="action-submit"
              onClick={() => doAction(() => salesApi.submit(row.id), "已提交审批")}
            >
              提交
            </a>,
          );
        }
        if (isWriter && s === "draft") {
          btns.push(
            <Popconfirm key="void" title="确认作废该草稿?" onConfirm={() => doAction(() => salesApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        if (isWriter && s === "pending") {
          btns.push(
            <Popconfirm
              key="approve"
              title="审批通过后将按 FEFO 预占库存,库存不足将回退草稿。确认审批?"
              onConfirm={() => doAction(() => salesApi.approve(row.id), "已审批并通过预占")}
            >
              <a className="action-approve">审批</a>
            </Popconfirm>,
            <a key="reject" className="action-reject" onClick={() => setRejectTarget(row)}>
              驳回
            </a>,
          );
        }
        if (isWriter && s === "approved") {
          btns.push(
            <Popconfirm key="close" title="关闭后未发货部分不再发货,确认关闭?" onConfirm={() => doAction(() => salesApi.close(row.id), "已关闭")}>
              <a className="action-close">关闭</a>
            </Popconfirm>,
            <Popconfirm key="void" title="确认作废该订单?" onConfirm={() => doAction(() => salesApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        return <Space size={12}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  return (
    <>
      <ProTable<SalesOrder>
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
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            isWriter && (
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
        footer={null}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="开单日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="发货仓库">
                {warehouses.find((w) => w.id === detail.warehouseId)?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <DocStatusTag status={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="金额">{Number(detail.totalAmount).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="税额">{Number(detail.totalTaxAmount).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="价税合计">
                {Number(detail.totalTaxInclusive).toFixed(2)}
              </Descriptions.Item>
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
          if (rejectTarget) doAction(() => salesApi.reject(rejectTarget.id, reason), "已驳回");
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
