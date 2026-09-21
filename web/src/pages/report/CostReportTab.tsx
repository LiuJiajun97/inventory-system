// 报表中心 Tab 5:库存成本(移动均价)
// 成本单元 = 仓库+物品+批次;零建表实时回放流水计算,不分页(前端本地分页)
// 均价 4 位小数(2.625 这类均价 2 位会失真),金额 2 位小数;日期默认今天(回放截止)

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ProColumns } from "@ant-design/pro-components";
import { Bar } from "@ant-design/charts";
import dayjs from "dayjs";
import { itemApi, reportApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { CostReportRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { ChartCard } from "../../components/ChartCard";
import { QtyCell } from "./common";
import { EmptyHint } from "../../components/EmptyHint";
import { fmtMoney } from "../../utils/format";

// TASK-v22b B3:成本报表图表——按物品聚合成本金额(多仓/多批次求和),Top10 横向条形图
// 成本接口不分页(全量行),金额按当前库存(回放截止默认今天)
function CostTop10Chart() {
  const [data, setData] = useState<{ name: string; amount: number }[]>([]);

  useEffect(() => {
    reportApi
      .cost()
      .then((r) => {
        const m = new Map<string, number>();
        r.rows.forEach((row) => {
          const name = row.itemName ?? "未知物品";
          m.set(name, (m.get(name) ?? 0) + Number(row.amount || 0));
        });
        const arr = Array.from(m.entries())
          .map(([name, amount]) => ({ name, amount: Number(amount.toFixed(2)) }))
          .sort((a, b) => b.amount - a.amount)
          .slice(0, 10);
        setData(arr);
      })
      .catch(() => setData([]));
  }, []);

  return (
    <ChartCard
      title="物品库存金额 Top 10"
      note="按当前库存金额(移动均价×数量)"
      height={300}
      empty={data.length === 0}
      emptyText="暂无库存成本数据"
    >
      <Bar
        data={data}
        xField="amount"
        yField="name"
        height={300}
        axis={{ x: { title: false } }}
        tooltip={{ items: [{ channel: "x", valueFormatter: (v: number) => fmtMoney(v) }] }}
      />
    </ChartCard>
  );
}

export function CostReportTab() {
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
    date?: string;
  }) => {
    setFilterParams({
      warehouseId: params.warehouseId,
      itemId: params.itemId,
      date: params.date,
    });
    const res = await reportApi.cost({
      warehouseId: params.warehouseId,
      itemId: params.itemId,
      date: params.date,
    });
    // 后端不分页:全量行 + 合计,前端本地分页
    return { data: res.rows, success: true, total: res.rows.length };
  };

  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    Object.entries(filterParams).forEach(([k, v]) => {
      if (v !== undefined && v !== "") p.set(k, String(v));
    });
    const qs = p.toString();
    return "/reports/cost/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);

  const columns: ProColumns<CostReportRow>[] = [
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
      title: "日期(回放截止)",
      dataIndex: "date",
      valueType: "date",
      hideInTable: true,
      formItemProps: { initialValue: dayjs().format("YYYY-MM-DD") },
      fieldProps: { allowClear: true },
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
      title: "数量",
      dataIndex: "quantity",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => <QtyCell value={r.quantity} />,
    },
    {
      // 均价 4 位小数:2.625 这类均价 2 位会失真
      title: "移动均价",
      dataIndex: "avgPrice",
      width: 110,
      align: "right",
      search: false,
      render: (_v, r) => (
        <span className="num-cell">{fmtMoney(r.avgPrice)}</span>
      ),
    },
    {
      title: "成本金额",
      dataIndex: "amount",
      width: 120,
      align: "right",
      search: false,
      render: (_v, r) => (
        <span className="num-cell">{fmtMoney(r.amount)}</span>
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
  } = useResizableColumns(columns, "report-cost");
  return (
    <>
      {/* TASK-v22b B3:图表在上、表格在下(表格本身不动) */}
      <CostTop10Chart />
      <ProTable<CostReportRow>
      rowKey={(r) => `${r.warehouseId}-${r.itemId}-${r.batchId}`}
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无成本数据" /> }}
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
          <ExportButton key="export" url={exportUrl} filename="库存成本.xlsx" />,
        ],
      }}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
      }}
      scroll={rcScroll}
    />
    </>
  );
}
