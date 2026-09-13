// 报表中心 Tab 1:进销存月报
// item 维度跨仓汇总;筛选 仓库/物品/日期区间;行展开看各仓期末明细
// 金额右对齐 toFixed(2),无快照显示 "-"

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { itemApi, reportApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StockMonthlyDetail, StockMonthlyRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { AmountCell, QtyCell, dayPart } from "./common";

export function MonthlyReportTab() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);

  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
  }, []);

  // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

  const request = async (params: {
    current?: number;
    pageSize?: number;
    warehouseId?: number;
    itemId?: number;
    from?: string;
    to?: string;
  }) => {
    setFilterParams({
      warehouseId: params.warehouseId,
      itemId: params.itemId,
      from: params.from,
      to: params.to,
    });
    const res = await reportApi.monthly({
      warehouseId: params.warehouseId,
      itemId: params.itemId,
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
    Object.entries(filterParams).forEach(([k, v]) => {
      if (v !== undefined && v !== "") p.set(k, String(v));
    });
    const qs = p.toString();
    return "/reports/stock-monthly/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);

  // 行展开:各仓期末明细
  const detailColumns: ColumnsType<StockMonthlyDetail> = [
    { title: "仓库", dataIndex: "warehouseName", width: 200, render: (v: string | null) => v ?? "-" },
    {
      title: "期末量",
      dataIndex: "closingQty",
      width: 160,
      align: "right",
      render: (v: string) => <QtyCell value={v} />,
    },
  ];

  const columns: ProColumns<StockMonthlyRow>[] = [
    {
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部仓库",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
    },
    {
      title: "物品",
      dataIndex: "itemId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        showSearch: true,
        optionFilterProp: "label",
        placeholder: "全部物品",
        options: items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id })),
      },
    },
    {
      title: "日期范围",
      dataIndex: "range",
      valueType: "dateRange",
      hideInTable: true,
      search: {
        // 统一约定:东八区本地日期,空格分隔传参(报表按日,直接传 yyyy-MM-dd)
        transform: (value: [unknown, unknown]) => ({
          from: dayPart(value[0]),
          to: dayPart(value[1]),
        }),
      },
    },
    { title: "物品编码", dataIndex: "itemCode", width: 110, search: false },
    { title: "物品名称", dataIndex: "itemName", width: 160, ellipsis: true, search: false },
    { title: "单位", dataIndex: "unit", width: 70, search: false },
    { title: "规格", dataIndex: "spec", width: 120, ellipsis: true, search: false },
    {
      title: "期初量",
      dataIndex: "openingQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.openingQty} />,
    },
    {
      title: "本期入量",
      dataIndex: "inQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.inQty} />,
    },
    {
      title: "本期出量",
      dataIndex: "outQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.outQty} />,
    },
    {
      title: "期末量",
      dataIndex: "closingQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.closingQty} />,
    },
    {
      title: "入库金额",
      dataIndex: "inAmount",
      width: 120,
      align: "right",
      search: false,
      render: (_v, r) => <AmountCell value={r.inAmount} />,
    },
    {
      title: "出库金额",
      dataIndex: "outAmount",
      width: 120,
      align: "right",
      search: false,
      render: (_v, r) => <AmountCell value={r.outAmount} />,
    },
  ];

  return (
    <ProTable<StockMonthlyRow>
      rowKey="itemId"
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
          <ExportButton key="export" url={exportUrl} filename="进销存月报.xlsx" />,
        ],
      }}
      expandable={{
        expandedRowRender: (r) => (
          <Table<StockMonthlyDetail>
            rowKey="warehouseId"
            size="small"
            columns={detailColumns}
            dataSource={r.details}
            pagination={false}
          />
        ),
        rowExpandable: (r) => r.details.length > 0,
      }}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
      }}
      scroll={{ x: 1080 }}
    />
  );
}
