// 报表中心 Tab 1:进销存月报
// item 维度跨仓汇总;筛选 仓库/物品/日期区间;行展开看各仓期末明细
// 金额右对齐千分位(AmountCell),无快照显示 "-"

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ProColumns } from "@ant-design/pro-components";
import { Column } from "@ant-design/charts";
import { Table } from "antd";
import dayjs from "dayjs";
import type { ColumnsType } from "antd/es/table";
import { itemApi, reportApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StockMonthlyDetail, StockMonthlyRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { ChartCard } from "../../components/ChartCard";
import { AmountCell, QtyCell, dayPart } from "./common";
import { EmptyHint } from "../../components/EmptyHint";
import { fetchAllPages } from "../../utils/fetchAllPages";
import { fmtQty } from "../../utils/format";
import { proTableRequest } from "../../utils/proTable";

// TASK-v22b B3:进销存月报图表——按月份拆区间调月报接口,全部物品入库/出量合计
// 无日期筛选时默认近 6 个月;超过 12 个月只取最后 12 个月
function MonthlyInoutChart({ from, to }: { from?: string; to?: string }) {
  const [data, setData] = useState<{ month: string; type: string; value: number }[]>([]);

  useEffect(() => {
    const end = to ? dayjs(to) : dayjs();
    const start = from ? dayjs(from) : end.subtract(5, "month");
    // 生成月份区间列表(每月一个查询:当月 1 日~月末)
    const months: { key: string; from: string; to: string }[] = [];
    let cur = start.startOf("month");
    const endMonth = end.startOf("month");
    while ((cur.isSame(endMonth, "month") || cur.isBefore(endMonth, "month")) && months.length < 12) {
      months.push({
        key: cur.format("YYYY-MM"),
        from: cur.format("YYYY-MM-01"),
        to: cur.endOf("month").format("YYYY-MM-DD"),
      });
      cur = cur.add(1, "month");
    }
    // 每月一次月报查询(后端 pageSize 上限 200,循环拉全量;截断上限 1000,本地库远小于此)
    Promise.all(
      months.map(async (m) => {
        try {
          const rows = await fetchAllPages<StockMonthlyRow>((page, pageSize) =>
            reportApi.monthly({ from: m.from, to: m.to, page, pageSize }),
          );
          return { key: m.key, rows };
        } catch {
          return { key: m.key, rows: [] as StockMonthlyRow[] };
        }
      }),
    ).then((resList) => {
      const flat: { month: string; type: string; value: number }[] = [];
      resList.forEach(({ key, rows }) => {
        let inQty = 0;
        let outQty = 0;
        rows.forEach((row) => {
          inQty += Number(row.inQty) || 0;
          outQty += Number(row.outQty) || 0;
        });
        flat.push({ month: key, type: "入库量", value: Number(inQty.toFixed(3)) });
        flat.push({ month: key, type: "出库量", value: Number(outQty.toFixed(3)) });
      });
      setData(flat);
    });
  }, [from, to]);

  const hasData = data.some((d) => d.value > 0);
  return (
    <ChartCard
      title="各月入库/出库量"
      note={from || to ? "按当前日期筛选按月拆分" : "默认近 6 个月"}
      height={300}
      empty={!hasData}
      emptyText="当前区间内暂无入出库数据"
    >
      <Column
        data={data}
        xField="month"
        yField="value"
        colorField="type"
        height={300}
        legend={{ color: { position: "right" } }}
        tooltip={{ items: [{ channel: "y", valueFormatter: (v: number) => fmtQty(v) }] }}
      />
    </ChartCard>
  );
}

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

  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { warehouseId?: number; itemId?: number; from?: string; to?: string }) => {
      setFilterParams({
        warehouseId: p.warehouseId,
        itemId: p.itemId,
        from: p.from,
        to: p.to,
      });
      return {
        warehouseId: p.warehouseId,
        itemId: p.itemId,
        from: p.from,
        to: p.to,
      };
    },
    reportApi.monthly,
  );

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

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(columns, "report-monthly");
  return (
    <>
      {/* TASK-v22b B3:图表在上、表格在下(表格本身不动) */}
      <MonthlyInoutChart
        from={filterParams.from as string | undefined}
        to={filterParams.to as string | undefined}
      />
      <ProTable<StockMonthlyRow>
      rowKey="itemId"
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无月度库存数据" /> }}
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
      scroll={rcScroll}
    />
    </>
  );
}
