// 发票登记列表(V18 结算域)
// ProTable 规范:无标题行、筛选 span6、新建按钮 search.optionRender、详情弹窗 960
// 列 = 单号/类型/对方/日期/金额(负数红色)/状态/来源;操作:确认/作废/查看

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Space, Table, Tag, App } from "antd";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { invoiceApi, supplierApi, customerApi } from "../../api";
import type { Invoice } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { fmtDate, fmtDateTime, fmtMoney, fmtQty } from "../../utils/format";
import { proTableRequest } from "../../utils/proTable";
import { EmptyHint } from "../../components/EmptyHint";

function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

// 发票状态静态映射(状态机枚举不进字典)
const INVOICE_STATUS: Record<string, { text: string; color: string }> = {
  draft: { text: "草稿", color: "default" },
  mismatch: { text: "差异", color: "red" },
  confirmed: { text: "已确认", color: "green" },
  voided: { text: "已作废", color: "default" },
};

export function InvoiceListPage() {
  const [detail, setDetail] = useState<Invoice | null>(null);
  const [parties, setParties] = useState<{ label: string; value: number }[]>([]);
  const actionRef = useRef<ActionType>();
  const { hasPerm } = usePermission();
  const location = useLocation();
  const navigate = useNavigate();
  const { message, modal } = App.useApp();

  useEffect(() => {
    // 对方筛选项:供应商+客户合并(label 标注类型)
    Promise.all([
      supplierApi.list({ page: 1, pageSize: 200 }),
      customerApi.list({ page: 1, pageSize: 200 }),
    ])
      .then(([sp, ck]) => {
        setParties([
          ...sp.rows.map((s) => ({ label: `供 ${s.supplierName}`, value: s.id })),
          ...ck.rows.map((c) => ({ label: `客 ${c.customerName}`, value: c.id })),
        ]);
      })
      .catch(() => undefined);
  }, []);

  // 从编辑/新建页带 ?refresh= 跳回时自动重载列表
  useEffect(() => {
    if (location.search.includes("refresh")) actionRef.current?.reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { docNo?: string; invoiceType?: string; status?: string; partyId?: number; from?: string; to?: string }) => ({
      docNo: p.docNo,
      invoiceType: p.invoiceType,
      status: p.status,
      partyId: p.partyId,
      from: p.from,
      to: p.to,
    }),
    invoiceApi.list,
  );

  const openDetail = async (id: number) => {
    try {
      setDetail(await invoiceApi.get(id));
    } catch {
      // 拦截器已提示
    }
  };

  const onConfirm = (row: Invoice) => {
    modal.confirm({
      title: `确认发票 ${row.docNo}?`,
      content: "确认后进入应收应付台账;存在差异行将被拒绝。",
      onOk: async () => {
        try {
          await invoiceApi.confirm(row.id);
          message.success("已确认");
          actionRef.current?.reload();
        } catch {
          // 拦截器已提示
        }
      },
    });
  };

  const onVoid = (row: Invoice) => {
    modal.confirm({
      title: `作废发票 ${row.docNo}?`,
      content: "作废后释放已开票额度,源单据行可重新开票。",
      onOk: async () => {
        try {
          await invoiceApi.void(row.id);
          message.success("已作废");
          actionRef.current?.reload();
        } catch {
          // 拦截器已提示
        }
      },
    });
  };

  const columns: ProColumns<Invoice>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 190,
      fieldProps: { placeholder: "发票号", allowClear: true },
      render: (_v, r) => (
        <a
          style={{ fontFamily: "monospace", fontSize: 13 }}
          onClick={() => navigate("/invoices/new/" + r.id)}
        >
          {r.docNo}
        </a>
      ),
    },
    {
      title: "类型",
      dataIndex: "invoiceType",
      valueType: "select",
      width: 90,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: [
          { label: "采购票", value: "purchase" },
          { label: "销售票", value: "sales" },
        ],
      },
      render: (_v, r) => (
        <Tag bordered color={r.invoiceType === "purchase" ? "blue" : "purple"}>
          {r.invoiceType === "purchase" ? "采购票" : "销售票"}
        </Tag>
      ),
    },
    {
      title: "对方",
      dataIndex: "partyId",
      valueType: "select",
      width: 150,
      ellipsis: true,
      fieldProps: {
        allowClear: true,
        showSearch: true,
        optionFilterProp: "label",
        placeholder: "供应商/客户",
        options: parties,
      },
      render: (_v, r) => r.partyName ?? "-",
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
      title: "状态",
      dataIndex: "status",
      valueType: "select",
      width: 90,
      fieldProps: {
        allowClear: true,
        options: [
          { label: "草稿", value: "draft" },
          { label: "差异", value: "mismatch" },
          { label: "已确认", value: "confirmed" },
          { label: "已作废", value: "voided" },
        ],
      },
      render: (_v, r) => {
        const it = INVOICE_STATUS[r.status] ?? { text: r.status, color: "default" };
        return <Tag bordered color={it.color}>{it.text}</Tag>;
      },
    },
    { title: "日期", dataIndex: "invoiceDate", width: 110, search: false, render: (_v, r) => fmtDate(r.invoiceDate) },
    {
      title: "金额",
      dataIndex: "totalAmount",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => (
        <b style={r.totalAmount < 0 ? { color: "#dc2626" } : undefined}>
          {fmtMoney(r.totalAmount)}
        </b>
      ),
    },
    {
      title: "来源",
      dataIndex: "sourceType",
      width: 90,
      search: false,
      render: (_v, r) =>
        r.sourceType === "return_gen" ? (
          <Tag bordered color="orange">退货生成</Tag>
        ) : (
          <Tag bordered>手工</Tag>
        ),
    },
    {
      title: "操作",
      width: 220,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => (
        <Space size="small">
          {(row.status === "draft" || row.status === "mismatch") && hasPerm("invoices:edit") && (
            <Link to={`/invoices/new/${row.id}`}>编辑</Link>
          )}
          {(row.status === "draft" || row.status === "mismatch") && hasPerm("invoices:confirm") && (
            <a onClick={() => onConfirm(row)}>确认</a>
          )}
          {row.status !== "voided" && hasPerm("invoices:void") && (
            <a style={{ color: "#dc2626" }} onClick={() => onVoid(row)}>
              作废
            </a>
          )}
          <a onClick={() => void openDetail(row.id)}>查看</a>
        </Space>
      ),
    },
  ];

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(columns, "invoice-list");
  return (
    <>
      <ProTable<Invoice>
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无发票" /> }}
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
            hasPerm("invoices:create") && (
              <Link key="new" to="/invoices/new">
                <Button type="primary">新建</Button>
              </Link>
            ),
          ],
        }}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title={`发票详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="单号">
                <span style={{ fontFamily: "monospace" }}>{detail.docNo}</span>
              </Descriptions.Item>
              <Descriptions.Item label="类型">
                {detail.invoiceType === "purchase" ? "采购票" : "销售票"}
              </Descriptions.Item>
              <Descriptions.Item label="对方">{detail.partyName ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="日期">{fmtDate(detail.invoiceDate)}</Descriptions.Item>
              <Descriptions.Item
                label="金额"
              >
                <span style={detail.totalAmount < 0 ? { color: "#dc2626", fontWeight: 600 } : { fontWeight: 600 }}>
                  {fmtMoney(detail.totalAmount)}
                </span>
                {detail.sign === "negative" && (
                  <Tag bordered color="orange" style={{ marginLeft: 8 }}>负票(红字)</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {INVOICE_STATUS[detail.status]?.text ?? detail.status}
              </Descriptions.Item>
              <Descriptions.Item label="来源">
                {detail.sourceType === "return_gen" ? (
                  <>
                    退货生成
                    {detail.refReturnNo && (
                      <span style={{ fontFamily: "monospace", marginLeft: 8 }}>{detail.refReturnNo}</span>
                    )}
                  </>
                ) : (
                  "手工"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{fmtDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? "-"}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>发票行</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                { title: "行号", dataIndex: "lineNo", width: 50 },
                {
                  title: "源单号",
                  dataIndex: "srcDocNo",
                  width: 150,
                  render: (v?: string | null) =>
                    v ? <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> : "-",
                },
                {
                  title: "物品",
                  width: 170,
                  ellipsis: true,
                  render: (_v, r) => `${r.itemCode ?? ""} ${r.itemName ?? ""}`.trim() || r.itemId,
                },
                { title: "规格", dataIndex: "specSnapshot", width: 90, ellipsis: true, render: (v?: string | null) => v ?? "-" },
                {
                  title: "数量",
                  dataIndex: "quantity",
                  width: 80,
                  align: "right",
                  className: "num-cell",
                  render: (v?: number | null) => fmtQty(v),
                },
                {
                  title: "开票额",
                  dataIndex: "invoicedAmount",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: number) => (
                    <b style={v < 0 ? { color: "#dc2626" } : undefined}>{fmtMoney(v)}</b>
                  ),
                },
                {
                  title: "源行含税额",
                  dataIndex: "srcAmount",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v?: number | null) => fmtMoney(v),
                },
                {
                  title: "差异",
                  dataIndex: "variance",
                  width: 90,
                  align: "right",
                  className: "num-cell",
                  render: (v: number) =>
                    Math.abs(Number(v)) > 0.01 ? (
                      <b style={{ color: "#dc2626" }}>{fmtMoney(v)}</b>
                    ) : (
                      fmtMoney(v)
                    ),
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  );
}
