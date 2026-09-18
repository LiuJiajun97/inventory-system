// 报表中心 Tab 4:销售对账
// 客户维度;筛选 客户/日期区间(按 doc_date);行展开看期间内销售单+退货单明细

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ProColumns } from "@ant-design/pro-components";
import { customerApi, reportApi } from "../../api";
import type { Customer } from "../../types/phase1";
import type { SalesReconRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { AmountCell, QtyCell, ReconDetailTable, dayPart } from "./common";
import { EmptyHint } from "../../components/EmptyHint";
import { proTableRequest } from "../../utils/proTable";

export function SalesReconTab() {
  const [customers, setCustomers] = useState<Customer[]>([]);

  useEffect(() => {
    customerApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setCustomers(r.rows))
      .catch(() => undefined);
  }, []);

  // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { customerId?: number; from?: string; to?: string }) => {
      setFilterParams({ customerId: p.customerId, from: p.from, to: p.to });
      return { customerId: p.customerId, from: p.from, to: p.to };
    },
    reportApi.salesRecon,
  );

  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    Object.entries(filterParams).forEach(([k, v]) => {
      if (v !== undefined && v !== "") p.set(k, String(v));
    });
    const qs = p.toString();
    return "/reports/sales-recon/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);

  const columns: ProColumns<SalesReconRow>[] = [
    {
      title: "客户",
      dataIndex: "customerId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        showSearch: true,
        optionFilterProp: "label",
        placeholder: "全部客户",
        options: customers.map((c) => ({ label: `${c.customerCode} ${c.customerName}`, value: c.id })),
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
    { title: "客户编码", dataIndex: "customerCode", width: 120, search: false },
    { title: "客户名称", dataIndex: "customerName", width: 160, ellipsis: true, search: false },
    {
      title: "销售单数",
      dataIndex: "orderCount",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => <span className="num-cell">{r.orderCount}</span>,
    },
    {
      title: "销售量",
      dataIndex: "orderQty",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.orderQty} />,
    },
    {
      title: "销售金额",
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
      title: "净销售量",
      dataIndex: "netQty",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.netQty} />,
    },
    {
      title: "净销售金额",
      dataIndex: "netAmount",
      width: 120,
      align: "right",
      search: false,
      render: (_v, r) => <AmountCell value={r.netAmount} />,
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
  } = useResizableColumns(columns, "report-sales-recon");
  return (
    <ProTable<SalesReconRow>
      rowKey="customerId"
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无销售对账数据" /> }}
      columns={rcColumns}
      request={request}
      headerTitle={false}
      options={{
        density: false,
        reload: false,
        fullScreen: false,
        setting: rcOptionSetting,
      }}
      columnsState={columnsState}
      onColumnsStateChange={onColumnsChange}
      components={rcComponents}
      search={{
        labelWidth: "auto",
        defaultCollapsed: false,
        span: 6,
        optionRender: (_searchConfig, _props, dom) => [
          ...dom,
          <ExportButton key="export" url={exportUrl} filename="销售对账.xlsx" />,
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
      scroll={rcScroll}
    />
  );
}
