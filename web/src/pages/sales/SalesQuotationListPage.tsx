// 销售报价单列表(V25)
// ProTable 版:无审批流(draft/sent/converted/voided)
// 状态机操作列:草稿(发送/转订单/作废/编辑) / 已发送(转订单/作废) / 过期禁用转订单
// 状态 Tag 末位加"已过期"橙色标记(状态不变,纯展示)

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Popconfirm, Space, Table, Tag, Tooltip } from "antd";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { fmtDate, fmtDateTime, fmtMoney, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { DocDetailHeader, DocAuditLine } from "../../components/DocDetailSections";
import { customerApi, warehouseApi, quotationApi, salesApi } from "../../api";
import { usePermission } from "../../auth/usePermission";
import { proTableRequest } from "../../utils/proTable";
import type { Warehouse } from "../../types";
import type { SalesQuotation } from "../../types/phase1";
import type { Customer } from "../../types/phase1";

// 报价单状态枚举(前端静态映射;后端 QuotationStatus 常量)
const STATUS_ENUM: Record<string, { text: string; color: string }> = {
  draft: { text: "草稿", color: "default" },
  sent: { text: "已发送", color: "processing" },
  converted: { text: "已转换", color: "success" },
  voided: { text: "已作废", color: "default" },
};

function StatusTag({ status }: { status: string }) {
  const it = STATUS_ENUM[status];
  if (!it) return <Tag>{status}</Tag>;
  return <Tag color={it.color}>{it.text}</Tag>;
}

export function SalesQuotationListPage() {
  const { hasPerm } = usePermission();
  const canEdit = hasPerm("quotation:edit");
  const canSend = hasPerm("quotation:send");
  const canConvert = hasPerm("quotation:convert");
  const canVoid = hasPerm("quotation:void");
  const canCreate = canEdit; // 与销售订单页一致:能编辑即可新建
  const navigate = useNavigate();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<SalesQuotation | null>(null);
  const actionRef = useRef<ActionType>();

  useEffect(() => {
    customerApi.list({ page: 1, pageSize: 200 }).then((r) => setCustomers(r.rows)).catch(() => undefined);
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
  }, []);

  const [searchParams] = useSearchParams();
  const urlDocNo = searchParams.get("docNo") || undefined;
  const autoOpened = useRef(false);

  const request = proTableRequest(
    (p: { docNo?: string; customerId?: number; status?: string; from?: string; to?: string }) => ({
      docNo: p.docNo,
      customerId: p.customerId,
      status: p.status,
      from: p.from,
      to: p.to,
    }),
    quotationApi.list,
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
      // 拦截器已提示
    }
  };

  // 转换销售订单:跳新建页并带 ref 三列
  const convertToOrder = async (row: SalesQuotation) => {
    try {
      // 通过 URL search 传递 ref,新建页读取并回填
      const url = `/sales-orders/new?refType=quotation&refId=${row.id}&refNo=${encodeURIComponent(row.docNo)}`;
      navigate(url);
    } catch {
      // ignore
    }
  };

  const columns: ProColumns<SalesQuotation>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "报价单号", allowClear: true },
      render: (_v, r) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => navigate("/sales-quotations/new/" + r.id)}>
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
      render: (_v, r) => customers.find((s) => s.id === r.customerId)?.customerName ?? `#${r.customerId}`,
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 110,
      valueEnum: STATUS_ENUM,
      render: (_v, r) => (
        <Space size={4}>
          <StatusTag status={r.status} />
          {r.expired && <Tag color="orange">已过期</Tag>}
        </Space>
      ),
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
    {
      title: "报价有效期",
      dataIndex: "quoteValidUntil",
      width: 110,
      search: false,
      render: (_v, r) => fmtDate(r.quoteValidUntil ?? undefined),
    },
    {
      title: "发货仓库",
      width: 130,
      ellipsis: true,
      search: false,
      render: (_v, r) => warehouses.find((w) => w.id === r.warehouseId)?.warehouseName ?? `#${r.warehouseId}`,
    },
    { title: "金额", dataIndex: "totalAmount", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.totalAmount) },
    { title: "税额", dataIndex: "totalTaxAmount", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.totalTaxAmount) },
    { title: "价税合计", dataIndex: "totalTaxInclusive", width: 130, align: "right", className: "num-cell", search: false, render: (_v, r) => <b>{fmtMoney(r.totalTaxInclusive)}</b> },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true, search: false },
    { title: "创建时间", dataIndex: "createdAt", width: 160, search: false, render: (_v, r) => fmtDateTime(r.createdAt) },
    {
      title: "操作",
      width: 360,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (canEdit && s === "draft") {
          btns.push(
            <a key="edit" className="action-edit" onClick={() => navigate(`/sales-quotations/new/${row.id}`)}>
              编辑
            </a>,
          );
        }
        if (canSend && s === "draft") {
          btns.push(
            <a key="send" className="action-submit" onClick={() => doAction(() => quotationApi.send(row.id), "已标记发送")}>
              发送
            </a>,
          );
        }
        // 转换订单:仅 draft/sent 可转换,且未过期
        const convertable = (s === "draft" || s === "sent") && !row.expired;
        if (canConvert) {
          if (convertable) {
            btns.push(
              <a key="convert" className="action-approve" onClick={() => convertToOrder(row)}>
                转订单
              </a>,
            );
          } else if (row.expired && (s === "draft" || s === "sent")) {
            btns.push(
              <Tooltip key="convert-tip" title="报价单已过期,不可转换">
                <a style={{ color: "#bfbfbf", cursor: "not-allowed" }}>转订单</a>
              </Tooltip>,
            );
          }
        }
        if (canVoid && (s === "draft" || s === "sent")) {
          btns.push(
            <Popconfirm key="void" title="确认作废该报价单?" onConfirm={() => doAction(() => quotationApi.voidDoc(row.id), "已作废")}>
              <a className="action-void">作废</a>
            </Popconfirm>,
          );
        }
        btns.push(<a key="detail" onClick={() => setDetail(row)}>查看</a>);
        return <Space size={12}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  const { columns: rcColumns, scroll: rcScroll, columnsState, onColumnsChange, components: rcComponents, optionSetting: rcOptionSetting } =
    useResizableColumns(columns, "quotation-list");

  return (
    <>
      <ProTable<SalesQuotation>
        params={{ docNo: urlDocNo }}
        form={{ initialValues: { docNo: urlDocNo } }}
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无销售报价单" /> }}
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
              <Link key="new" to="/sales-quotations/new">
                <Button type="primary">新建</Button>
              </Link>
            ),
          ],
        }}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title={`销售报价单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={<Button onClick={() => setDetail(null)}>关闭</Button>}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            <DocDetailHeader status={detail.status} docNo={detail.docNo} total={detail.totalTaxInclusive} />
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="开单日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="客户">
                {customers.find((c) => c.id === detail.customerId)?.customerName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="发货仓库">
                {warehouses.find((w) => w.id === detail.warehouseId)?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="报价有效期">{fmtDate(detail.quoteValidUntil ?? undefined)}</Descriptions.Item>
              <Descriptions.Item label="金额">{fmtMoney(detail.totalAmount)}</Descriptions.Item>
              <Descriptions.Item label="税额">{fmtMoney(detail.totalTaxAmount)}</Descriptions.Item>
              <Descriptions.Item label="备注" span={3}>{detail.remark ?? "-"}</Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>报价单行(价税分离)</div>
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
                { title: "数量", dataIndex: "quantity", width: 66, align: "right", className: "num-cell" },
                { title: "不含税单价", dataIndex: "unitPrice", width: 84, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.unitPrice) },
                { title: "含税单价", dataIndex: "taxPrice", width: 84, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.taxPrice) },
                { title: "税率(%)", dataIndex: "taxRate", width: 60, align: "right", className: "num-cell" },
                { title: "金额", dataIndex: "amount", width: 84, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.amount) },
                { title: "税额", dataIndex: "taxAmount", width: 72, align: "right", className: "num-cell", render: (_v, r) => fmtMoney(r.taxAmount) },
                { title: "价税合计", dataIndex: "totalAmount", width: 84, align: "right", className: "num-cell", render: (_v, r) => <b>{fmtMoney(r.totalAmount)}</b> },
              ]}
            />
            <DocAuditLine creator={detail.creator} createdAt={detail.createdAt} updater={detail.updater} updatedAt={detail.updatedAt} />
          </>
        )}
      </Modal>
    </>
  );
}