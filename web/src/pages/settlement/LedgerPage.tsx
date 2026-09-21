// 应付/应收台账(V18 结算域):零建表实时聚合,按对方展开
// 主表:入库(出库)额/已开票/待开票暂估/已付(收)/余额(余额红绿)
// 行展开两层:① 发票列表(号/日期/额/已核销/未核销/来源) ② 订单执行子表(下单/入库/开票/付款)

import { useEffect, useState } from "react";
import { Table, Tag, Spin } from "antd";
import type { ColumnsType } from "antd/es/table";
import { settlementApi } from "../../api";
import type { LedgerRow, LedgerInvoiceRow, OrderProgressRow } from "../../types/phase1";
import { fmtDate, fmtMoney } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";

const money = (v: number, strong = false) => (
  <span style={{ color: v < 0 ? "#dc2626" : undefined, fontWeight: strong ? 600 : undefined }}>
    {fmtMoney(v)}
  </span>
);

function InvoiceTable({ rows }: { rows: LedgerInvoiceRow[] }) {
  const columns: ColumnsType<LedgerInvoiceRow> = [
    { title: "发票号", dataIndex: "docNo", width: 170, render: (v) => <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> },
    { title: "日期", dataIndex: "invoiceDate", width: 100, render: (v) => fmtDate(v) },
    { title: "票额", dataIndex: "totalAmount", width: 100, align: "right", className: "num-cell", render: (v) => money(v, true) },
    {
      title: "正负",
      dataIndex: "sign",
      width: 70,
      render: (v: string) => (v === "negative" ? <Tag bordered color="orange">负</Tag> : <Tag bordered>正</Tag>),
    },
    {
      title: "已核销",
      dataIndex: "settledAmount",
      width: 100,
      align: "right",
      className: "num-cell",
      render: (v) => money(v),
    },
    {
      title: "未核销",
      width: 100,
      align: "right",
      className: "num-cell",
      render: (_v, r) => money(Number(r.totalAmount) - Number(r.settledAmount)),
    },
    {
      title: "来源",
      dataIndex: "sourceType",
      width: 90,
      render: (v: string) => (v === "return_gen" ? <Tag bordered color="orange">退货生成</Tag> : <Tag bordered>手工</Tag>),
    },
  ];
  return (
    <Table
      size="small"
      rowKey="invoiceId"
      columns={columns}
      dataSource={rows}
      pagination={false}
      locale={{ emptyText: "无已确认发票" }}
    />
  );
}

function OrderTable({ rows }: { rows: OrderProgressRow[] }) {
  const columns: ColumnsType<OrderProgressRow> = [
    { title: "订单号", dataIndex: "orderNo", width: 170, render: (v) => <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span> },
    { title: "日期", dataIndex: "docDate", width: 100, render: (v) => fmtDate(v ?? undefined) },
    { title: "下单额", dataIndex: "orderAmount", width: 110, align: "right", className: "num-cell", render: (v) => money(v) },
    { title: "入库额", dataIndex: "receivedAmount", width: 110, align: "right", className: "num-cell", render: (v) => money(v) },
    { title: "开票额", dataIndex: "invoicedAmount", width: 110, align: "right", className: "num-cell", render: (v) => money(v) },
    { title: "付款额", dataIndex: "settledAmount", width: 110, align: "right", className: "num-cell", render: (v) => money(v) },
  ];
  return (
    <Table
      size="small"
      rowKey="orderId"
      columns={columns}
      dataSource={rows}
      pagination={false}
      locale={{ emptyText: "无关联订单" }}
    />
  );
}

export function LedgerPage({ mode }: { mode: "ap" | "ar" }) {
  const [rows, setRows] = useState<LedgerRow[]>([]);
  const [loading, setLoading] = useState(true);
  const isAp = mode === "ap";

  useEffect(() => {
    setLoading(true);
    const api = isAp ? settlementApi.ap() : settlementApi.ar();
    api
      .then(setRows)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, [isAp]);

  const columns: ColumnsType<LedgerRow> = [
    { title: isAp ? "供应商" : "客户", dataIndex: "partyName", width: 180, ellipsis: true },
    { title: isAp ? "入库额" : "出库额", dataIndex: "receivedAmount", width: 120, align: "right", className: "num-cell", render: (v) => money(v) },
    { title: "已开票", dataIndex: "invoicedAmount", width: 120, align: "right", className: "num-cell", render: (v) => money(v) },
    { title: "待开票暂估", dataIndex: "estimatedAmount", width: 120, align: "right", className: "num-cell", render: (v) => money(v) },
    { title: isAp ? "已付" : "已收", dataIndex: "settledAmount", width: 120, align: "right", className: "num-cell", render: (v) => money(v) },
    {
      title: isAp ? "应付余额" : "应收余额",
      dataIndex: "balance",
      width: 130,
      align: "right",
      className: "num-cell",
      render: (v) => (
        <b style={v > 0 ? { color: isAp ? "#dc2626" : "#16a34a" } : v < 0 ? { color: "#dc2626" } : undefined}>
          {fmtMoney(v)}
        </b>
      ),
    },
  ];

  return (
    <div>
      <div className="doc-page-head">
        <h1 className="doc-page-title">{isAp ? "应付台账" : "应收台账"}</h1>
      </div>
      <Spin spinning={loading}>
        <Table
          rowKey="partyId"
          columns={columns}
          dataSource={rows}
          pagination={false}
          size="middle"
          locale={{ emptyText: <EmptyHint text={isAp ? "当前暂无应付台账数据" : "当前暂无应收台账数据"} /> }}
          expandable={{
            expandedRowRender: (row) => (
              <div style={{ padding: "4px 8px" }}>
                <div style={{ fontWeight: 600, marginBottom: 6 }}>已确认发票</div>
                <InvoiceTable rows={row.invoices ?? []} />
                <div style={{ fontWeight: 600, margin: "10px 0 6px" }}>订单执行(下单 / {isAp ? "入库" : "出库"} / 开票 / {isAp ? "付款" : "收款"})</div>
                <OrderTable rows={row.orders ?? []} />
              </div>
            ),
            rowExpandable: (row) => (row.invoices?.length ?? 0) > 0 || (row.orders?.length ?? 0) > 0,
          }}
        />
      </Spin>
    </div>
  );
}
