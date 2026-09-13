// 报表中心 Tab 3:采购对账
// 供应商维度;筛选 供应商/日期区间(按 doc_date);行展开看期间内采购单+退货单明细

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import { reportApi, supplierApi } from "../../api";
import type { Supplier } from "../../types/phase1";
import type { PurchaseReconRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { AmountCell, QtyCell, ReconDetailTable, dayPart } from "./common";
import { EmptyHint } from "../../components/EmptyHint";

export function PurchaseReconTab() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);

  useEffect(() => {
    supplierApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setSuppliers(r.rows))
      .catch(() => undefined);
  }, []);

  // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

  const request = async (params: {
    current?: number;
    pageSize?: number;
    supplierId?: number;
    from?: string;
    to?: string;
  }) => {
    setFilterParams({ supplierId: params.supplierId, from: params.from, to: params.to });
    const res = await reportApi.purchaseRecon({
      supplierId: params.supplierId,
      from: params.from,
      to: params.to,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    Object.entries(filterParams).forEach(([k, v]) => {
      if (v !== undefined && v !== "") p.set(k, String(v));
    });
    const qs = p.toString();
    return "/reports/purchase-recon/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);

  const columns: ProColumns<PurchaseReconRow>[] = [
    {
      title: "供应商",
      dataIndex: "supplierId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        showSearch: true,
        optionFilterProp: "label",
        placeholder: "全部供应商",
        options: suppliers.map((s) => ({ label: `${s.supplierCode} ${s.supplierName}`, value: s.id })),
      },
    },
    {
      title: "日期范围",
      dataIndex: "range",
      valueType: "dateRange",
      hideInTable: true,
      search: {
        // 统一约定:东八区本地日期,空格分隔传参(按单据 doc_date)
        transform: (value: [unknown, unknown]) => ({
          from: dayPart(value[0]),
          to: dayPart(value[1]),
        }),
      },
    },
    { title: "供应商编码", dataIndex: "supplierCode", width: 120, search: false },
    { title: "供应商名称", dataIndex: "supplierName", width: 160, ellipsis: true, search: false },
    {
      title: "采购单数",
      dataIndex: "orderCount",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => <span className="num-cell">{r.orderCount}</span>,
    },
    {
      title: "采购量",
      dataIndex: "orderQty",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.orderQty} />,
    },
    {
      title: "采购金额",
      dataIndex: "orderAmount",
      width: 120,
      align: "right",
      search: false,
      render: (_v, r) => <AmountCell value={r.orderAmount} />,
    },
    {
      title: "退货量",
      dataIndex: "returnQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.returnQty} />,
    },
    {
      title: "退货金额",
      dataIndex: "returnAmount",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => <AmountCell value={r.returnAmount} />,
    },
    {
      title: "净采购量",
      dataIndex: "netQty",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.netQty} />,
    },
    {
      title: "净采购金额",
      dataIndex: "netAmount",
      width: 120,
      align: "right",
      search: false,
      render: (_v, r) => <AmountCell value={r.netAmount} />,
    },
  ];

  return (
    <ProTable<PurchaseReconRow>
      rowKey="supplierId"
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无采购对账数据" /> }}
      columns={columns}
      request={request}
      headerTitle={false}
      options={false}
      search={{
        labelWidth: "auto",
        defaultCollapsed: false,
        span: 6,
        optionRender: (_searchConfig, _props, dom) => [
          ...dom,
          <ExportButton key="export" url={exportUrl} filename="采购对账.xlsx" />,
        ],
      }}
      expandable={{
        expandedRowRender: (r) => <ReconDetailTable rows={r.details} />,
        rowExpandable: (r) => r.details.length > 0,
      }}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
      }}
      scroll={{ x: 1120 }}
    />
  );
}
