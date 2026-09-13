// 报表中心 Tab 2:库龄/呆滞
// 批次维度(无批次仓按 item+仓库 汇总);筛选 仓库/物品/库龄区间/呆滞天数(默认 90)
// 库龄区间用文字不带颜色(与打印一致朴素风格);呆滞标记用文字"呆滞"

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import { itemApi, reportApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StockAgeingRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { QtyCell } from "./common";
import { EmptyHint } from "../../components/EmptyHint";

export function AgeingReportTab() {
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
    ageFrom?: number;
    ageTo?: number;
    stagnantDays?: number;
  }) => {
    setFilterParams({
      warehouseId: params.warehouseId,
      itemId: params.itemId,
      ageFrom: params.ageFrom,
      ageTo: params.ageTo,
      stagnantDays: params.stagnantDays,
    });
    const res = await reportApi.ageing({
      warehouseId: params.warehouseId,
      itemId: params.itemId,
      ageFrom: params.ageFrom,
      ageTo: params.ageTo,
      stagnantDays: params.stagnantDays,
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
    return "/reports/stock-ageing/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);

  const columns: ProColumns<StockAgeingRow>[] = [
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
      title: "库龄起(天)",
      dataIndex: "ageFrom",
      valueType: "digit",
      hideInTable: true,
      fieldProps: { min: 0, precision: 0, placeholder: "不限" },
    },
    {
      title: "库龄止(天)",
      dataIndex: "ageTo",
      valueType: "digit",
      hideInTable: true,
      fieldProps: { min: 0, precision: 0, placeholder: "不限" },
    },
    {
      title: "呆滞天数",
      dataIndex: "stagnantDays",
      valueType: "digit",
      hideInTable: true,
      formItemProps: { initialValue: 90 },
      fieldProps: { min: 1, precision: 0 },
    },
    { title: "仓库", dataIndex: "warehouseName", width: 140, ellipsis: true, search: false },
    { title: "物品编码", dataIndex: "itemCode", width: 110, search: false },
    { title: "物品名称", dataIndex: "itemName", width: 160, ellipsis: true, search: false },
    { title: "单位", dataIndex: "unit", width: 70, search: false },
    {
      title: "批次号",
      dataIndex: "batchNo",
      width: 130,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.batchNo ?? "-",
    },
    {
      title: "生产日期",
      dataIndex: "productionDate",
      width: 120,
      search: false,
      render: (_v, r) => r.productionDate ?? "-",
    },
    {
      title: "库龄天数",
      dataIndex: "ageDays",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => (r.ageDays == null ? "-" : <span className="num-cell">{r.ageDays}</span>),
    },
    {
      // 库龄区间:文字不带颜色(与打印一致朴素风格)
      title: "库龄区间",
      dataIndex: "ageBucket",
      width: 100,
      search: false,
    },
    {
      title: "当前量",
      dataIndex: "quantity",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.quantity} />,
    },
    {
      // 呆滞标记:文字"呆滞"
      title: "呆滞",
      dataIndex: "stagnant",
      width: 80,
      search: false,
      render: (_v, r) => (r.stagnant ? "呆滞" : ""),
    },
  ];

  return (
    <ProTable<StockAgeingRow>
      rowKey={(r) => `${r.warehouseId}-${r.itemId}-${r.batchId}`}
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无库龄数据" /> }}
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
          <ExportButton key="export" url={exportUrl} filename="库龄呆滞.xlsx" />,
        ],
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
