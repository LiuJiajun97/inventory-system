// 请购单列表(V25,无审批:草稿/已提交/已转换/已取消)
// 镜像采购订单页但更轻:操作列按状态(草稿:提交/转采购订单/取消/编辑;已提交:转/取消)

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Popconfirm, Space, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { fmtDate, fmtDateTime, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { DocDetailHeader, DocAuditLine } from "../../components/DocDetailSections";
import { requisitionApi, userApi, warehouseApi, dictApi, purchaseApi } from "../../api";
import { usePermission } from "../../auth/usePermission";
import { proTableRequest } from "../../utils/proTable";
import type { Warehouse } from "../../types";
import type { UserInfo } from "../../types";
import type { PurchaseRequisition } from "../../types/phase1";

// 请购单状态(后端 RequisitionStatus)
const STATUS_ENUM: Record<string, { text: string; color: string }> = {
  draft: { text: "草稿", color: "default" },
  submitted: { text: "已提交", color: "processing" },
  converted: { text: "已转换", color: "success" },
  cancelled: { text: "已取消", color: "default" },
};

function StatusTag({ status }: { status: string }) {
  const it = STATUS_ENUM[status];
  if (!it) return <Tag>{status}</Tag>;
  return <Tag color={it.color}>{it.text}</Tag>;
}

export function PurchaseRequisitionListPage() {
  const { hasPerm } = usePermission();
  const canEdit = hasPerm("requisition:edit");
  const canSubmit = hasPerm("requisition:submit");
  const canConvert = hasPerm("requisition:convert");
  const canCancel = hasPerm("requisition:cancel");
  const canCreate = canEdit;
  const navigate = useNavigate();
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  // 申请部门字典(dept 类型)
  const [deptOptions, setDeptOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [detail, setDetail] = useState<PurchaseRequisition | null>(null);
  const actionRef = useRef<ActionType>();

  useEffect(() => {
    userApi.list({ page: 1, pageSize: 200 }).then((r) => setUsers(r.rows)).catch(() => undefined);
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    dictApi.getType("dept").then((arr) => setDeptOptions(arr.map((d) => ({ label: d.label, value: d.code })))).catch(() => undefined);
  }, []);

  const [searchParams] = useSearchParams();
  const urlDocNo = searchParams.get("docNo") || undefined;
  const autoOpened = useRef(false);

  const request = proTableRequest(
    (p: { docNo?: string; warehouseId?: number; applicantId?: number; status?: string; from?: string; to?: string }) => ({
      docNo: p.docNo,
      warehouseId: p.warehouseId,
      applicantId: p.applicantId,
      status: p.status,
      from: p.from,
      to: p.to,
    }),
    requisitionApi.list,
    (res) => {
      if (urlDocNo && res.total === 1 && res.rows.length === 1 && !autoOpened.current) {
        autoOpened.current = true;
        setDetail(res.rows[0]);
      }
    },
  );

  const doAction = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      Modal.success({ content: msg });
      actionRef.current?.reload();
    } catch {
      // ignore
    }
  };

  const convertToOrder = (row: PurchaseRequisition) => {
    navigate(`/purchase-orders/new?refType=requisition&refId=${row.id}&refNo=${encodeURIComponent(row.docNo)}`);
  };

  const columns: ProColumns<PurchaseRequisition>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "请购单号", allowClear: true },
      render: (_v, r) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => navigate("/purchase-requisitions/new/" + r.id)}>
          {r.docNo}
        </a>
      ),
    },
    {
      title: "仓库",
      dataIndex: "warehouseId",
      width: 130,
      ellipsis: true,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
      render: (_v, r) => warehouses.find((w) => w.id === r.warehouseId)?.warehouseName ?? `#${r.warehouseId}`,
    },
    {
      title: "申请人",
      dataIndex: "applicantId",
      width: 110,
      ellipsis: true,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: users.map((u) => ({ label: u.name, value: u.id })),
      },
      render: (_v, r) => users.find((u) => u.id === r.applicantId)?.name ?? `#${r.applicantId}`,
    },
    {
      title: "申请部门",
      dataIndex: "department",
      width: 130,
      ellipsis: true,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: deptOptions,
      },
      render: (_v, r) => deptOptions.find((d) => d.value === r.department)?.label ?? (r.department ?? "-"),
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      valueEnum: STATUS_ENUM,
      render: (_v, r) => <StatusTag status={r.status} />,
    },
    {
      title: "日期",
      dataIndex: "range",
      valueType: "dateRange",
      hideInTable: true,
      search: {
        transform: (value: [unknown, unknown]) => ({
          from: typeof value[0] === "string" ? value[0] : (value[0] as { format: (s: string) => string }).format("YYYY-MM-DD"),
          to: typeof value[1] === "string" ? value[1] : (value[1] as { format: (s: string) => string }).format("YYYY-MM-DD"),
        }),
      },
    },
    { title: "开单日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate) },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true, search: false },
    { title: "创建时间", dataIndex: "createdAt", width: 160, search: false, render: (_v, r) => fmtDateTime(r.createdAt) },
    {
      title: "操作",
      width: 320,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (canEdit && s === "draft") {
          btns.push(
            <a key="edit" className="action-edit" onClick={() => navigate(`/purchase-requisitions/new/${row.id}`)}>
              编辑
            </a>,
          );
        }
        if (canSubmit && s === "draft") {
          btns.push(
            <a key="submit" className="action-submit" onClick={() => doAction(() => requisitionApi.submit(row.id), "已提交")}>
              提交
            </a>,
          );
        }
        if (canConvert && (s === "draft" || s === "submitted")) {
          btns.push(
            <a key="convert" className="action-approve" onClick={() => convertToOrder(row)}>
              转采购订单
            </a>,
          );
        }
        if (canCancel && (s === "draft" || s === "submitted")) {
          btns.push(
            <Popconfirm key="cancel" title="确认取消该请购单?" onConfirm={() => doAction(() => requisitionApi.cancel(row.id), "已取消")}>
              <a className="action-void">取消</a>
            </Popconfirm>,
          );
        }
        btns.push(<a key="detail" onClick={() => setDetail(row)}>查看</a>);
        return <Space size={12}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  const { columns: rcColumns, scroll: rcScroll, columnsState, onColumnsChange, components: rcComponents, optionSetting: rcOptionSetting } =
    useResizableColumns(columns, "requisition-list");

  return (
    <>
      <ProTable<PurchaseRequisition>
        params={{ docNo: urlDocNo }}
        form={{ initialValues: { docNo: urlDocNo } }}
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无请购单" /> }}
        actionRef={actionRef}
        columns={rcColumns}
        request={request}
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
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            canCreate && (
              <Link key="new" to="/purchase-requisitions/new">
                <Button type="primary">新建</Button>
              </Link>
            ),
          ],
        }}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title={`请购单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={<Button onClick={() => setDetail(null)}>关闭</Button>}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            <DocDetailHeader status={detail.status} docNo={detail.docNo} />
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="开单日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="仓库">
                {warehouses.find((w) => w.id === detail.warehouseId)?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="申请人">
                {users.find((u) => u.id === detail.applicantId)?.name ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="申请部门">
                {deptOptions.find((d) => d.value === detail.department)?.label ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>{detail.remark ?? "-"}</Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>请购明细(无金额合计,行价可空)</div>
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
                { title: "数量", dataIndex: "quantity", width: 80, align: "right", className: "num-cell", render: (_v, r) => fmtQty(r.quantity) },
                { title: "期望到货日", dataIndex: "expectedDate", width: 110, render: (v: string | null | undefined) => fmtDate(v ?? undefined) },
                { title: "参考单价", dataIndex: "unitPrice", width: 100, align: "right", className: "num-cell", render: (v: string | number | null | undefined) => v == null || v === "" ? "-" : v },
                { title: "行备注", dataIndex: "remark", width: 140, ellipsis: true, render: (v: string | null | undefined) => v ?? "-" },
              ]}
            />
            <DocAuditLine creator={detail.creator} createdAt={detail.createdAt} updater={detail.updater} updatedAt={detail.updatedAt} />
          </>
        )}
      </Modal>
    </>
  );
}