// 付款单/收款单列表(V18 结算域,一表一 type:payment/receipt)
// ProTable 规范:无标题行、筛选 span6、新建按钮 search.optionRender、详情弹窗 960
// 列 = 单号/对方/日期/金额/状态;操作:作废/查看

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Space, Table, Tag, App } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { paymentApi, supplierApi, customerApi } from "../../api";
import type { PaymentDoc } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { fmtDate, fmtDateTime, fmtMoney } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";

function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

export function PaymentListPage({ mode }: { mode: "payment" | "receipt" }) {
  const isPayment = mode === "payment";
  const [detail, setDetail] = useState<PaymentDoc | null>(null);
  const [parties, setParties] = useState<{ label: string; value: number }[]>([]);
  const actionRef = useRef<ActionType>();
  const { hasPerm } = usePermission();
  const location = useLocation();
  const { message, modal } = App.useApp();
  const createPerm = isPayment ? "payments:create" : "receipts:create";
  const voidPerm = isPayment ? "payments:void" : "receipts:void";

  useEffect(() => {
    if (isPayment) {
      supplierApi
        .list({ page: 1, pageSize: 200 })
        .then((r) => setParties(r.rows.map((s) => ({ label: s.supplierName, value: s.id }))))
        .catch(() => undefined);
    } else {
      customerApi
        .list({ page: 1, pageSize: 200 })
        .then((r) => setParties(r.rows.map((c) => ({ label: c.customerName, value: c.id }))))
        .catch(() => undefined);
    }
  }, [isPayment]);

  useEffect(() => {
    if (location.search.includes("refresh")) actionRef.current?.reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    status?: string;
    partyId?: number;
    from?: string;
    to?: string;
  }) => {
    const res = await paymentApi.list({
      payType: mode,
      docNo: params.docNo,
      status: params.status,
      partyId: params.partyId,
      from: params.from,
      to: params.to,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  const onVoid = (row: PaymentDoc) => {
    modal.confirm({
      title: `作废${isPayment ? "付款" : "收款"}单 ${row.docNo}?`,
      content: "作废后释放核销额度,对应发票可重新核销。",
      onOk: async () => {
        try {
          await paymentApi.void(row.id);
          message.success("已作废");
          actionRef.current?.reload();
        } catch {
          // 拦截器已提示
        }
      },
    });
  };

  const columns: ProColumns<PaymentDoc>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 190,
      fieldProps: { placeholder: isPayment ? "付款单号" : "收款单号", allowClear: true },
      render: (_v, r) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => void openDetail(r.id)}>
          {r.docNo}
        </a>
      ),
    },
    {
      title: "对方",
      dataIndex: "partyId",
      valueType: "select",
      width: 160,
      ellipsis: true,
      fieldProps: {
        allowClear: true,
        showSearch: true,
        optionFilterProp: "label",
        placeholder: isPayment ? "供应商" : "客户",
        options: parties,
      },
      render: (_v, r) => r.partyName ?? "-",
    },
    {
      title: "状态",
      dataIndex: "status",
      valueType: "select",
      width: 90,
      fieldProps: {
        allowClear: true,
        options: [
          { label: "已确认", value: "confirmed" },
          { label: "已作废", value: "voided" },
        ],
      },
      render: (_v, r) => (
        <Tag bordered color={r.status === "confirmed" ? "green" : "default"}>
          {r.status === "confirmed" ? "已确认" : "已作废"}
        </Tag>
      ),
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
    { title: "日期", dataIndex: "payDate", width: 110, search: false, render: (_v, r) => fmtDate(r.payDate ?? undefined) },
    {
      title: "金额",
      dataIndex: "totalAmount",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => <b>{fmtMoney(r.totalAmount)}</b>,
    },
    {
      title: "备注",
      dataIndex: "remark",
      width: 150,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.remark ?? "-",
    },
    {
      title: "操作",
      width: 120,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => (
        <Space size="small">
          {row.status !== "voided" && hasPerm(voidPerm) && (
            <a style={{ color: "#dc2626" }} onClick={() => onVoid(row)}>
              作废
            </a>
          )}
          <a onClick={() => void openDetail(row.id)}>查看</a>
        </Space>
      ),
    },
  ];

  const openDetail = async (id: number) => {
    try {
      setDetail(await paymentApi.get(id));
    } catch {
      // 拦截器已提示
    }
  };

  return (
    <>
      <ProTable<PaymentDoc>
        rowKey="id"
        locale={{ emptyText: <EmptyHint text={isPayment ? "当前筛选条件下暂无付款单" : "当前筛选条件下暂无收款单"} /> }}
        actionRef={actionRef}
        columns={columns}
        request={request}
        options={false}
        scroll={{ x: 1000 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            hasPerm(createPerm) && (
              <Link key="new" to={isPayment ? "/payments/new" : "/receipts/new"}>
                <Button type="primary">新建</Button>
              </Link>
            ),
          ],
        }}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title={`${isPayment ? "付款" : "收款"}单详情 - ${detail?.docNo ?? ""}`}
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
              <Descriptions.Item label="类型">{isPayment ? "付款单" : "收款单"}</Descriptions.Item>
              <Descriptions.Item label="对方">{detail.partyName ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="日期">{fmtDate(detail.payDate ?? undefined)}</Descriptions.Item>
              <Descriptions.Item label="金额">
                <b>{fmtMoney(detail.totalAmount)}</b>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {detail.status === "confirmed" ? "已确认" : "已作废"}
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{fmtDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? "-"}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>核销行</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.lines ?? []}
              pagination={false}
              columns={[
                {
                  title: "发票号",
                  dataIndex: "invoiceNo",
                  width: 180,
                  render: (v?: string | null) =>
                    v ? <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> : "-",
                },
                {
                  title: "核销额",
                  dataIndex: "amount",
                  align: "right",
                  className: "num-cell",
                  render: (v: number) => <b>{fmtMoney(v)}</b>,
                },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  );
}
