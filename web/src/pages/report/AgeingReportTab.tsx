// 报表中心 Tab 2:库龄/呆滞
// 批次维度(无批次仓按 item+仓库 汇总);筛选 仓库/物品/库龄区间/呆滞天数(默认 90)
// 库龄区间用文字不带颜色(与打印一致朴素风格);呆滞标记用文字"呆滞"

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ProColumns } from "@ant-design/pro-components";
import { Pie } from "@ant-design/charts";
import { itemApi, reportApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StockAgeingRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { ChartCard } from "../../components/ChartCard";
import { QtyCell } from "./common";
import { EmptyHint } from "../../components/EmptyHint";
import { fetchAllPages } from "../../utils/fetchAllPages";
import { fmtQty } from "../../utils/format";
import { proTableRequest } from "../../utils/proTable";

// TASK-v22b B3:库龄图——按库龄段(0-30/31-90/91-180/未知)聚合当前量占比(环图)
// 不带筛选全量拉取(pageSize=1000;本地库批次行数远小于 1000)
function AgeingBucketChart() {
  // 库龄段展示顺序(后端 ageBucket 文字:0-30 / 31-90 / 91-180 / >180 / 未知)
  const BUCKET_ORDER = ["0-30", "31-90", "91-180", ">180", "未知"];
  const [data, setData] = useState<{ bucket: string; quantity: number }[]>([]);

  useEffect(() => {
    // 后端 pageSize 上限 200,循环拉全量(截断上限 1000,本地库批次行数远小于此)
    fetchAllPages<StockAgeingRow>((page, pageSize) => reportApi.ageing({ page, pageSize }))
      .then((rows) => {
        const m = new Map<string, number>();
        rows.forEach((row) => {
          m.set(row.ageBucket, (m.get(row.ageBucket) ?? 0) + Number(row.quantity || 0));
        });
        const arr = Array.from(m.entries()).map(([bucket, quantity]) => ({
          bucket,
          quantity: Number(quantity.toFixed(3)),
        }));
        // 固定库龄段顺序,未预知的段排最后
        arr.sort(
          (a, b) =>
            (BUCKET_ORDER.indexOf(a.bucket) - BUCKET_ORDER.indexOf(b.bucket)) ||
            b.quantity - a.quantity,
        );
        setData(arr);
      })
      .catch(() => setData([]));
  }, []);

  const total = data.reduce((s, d) => s + d.quantity, 0);
  return (
    <ChartCard
      title="库龄段数量占比"
      note="全部库存按库龄段聚合数量"
      height={300}
      empty={data.length === 0}
      emptyText="暂无库存库龄数据"
    >
      <Pie
        data={data}
        angleField="quantity"
        colorField="bucket"
        innerRadius={0.6}
        height={300}
        legend={{ color: { position: "right" } }}
        tooltip={{
          items: [
            {
              channel: "y",
              // 数量 + 占比
              valueFormatter: (v: number) => `${fmtQty(v)} (${total > 0 ? ((v / total) * 100).toFixed(1) : "0.0"}%)`,
            },
          ],
        }}
      />
    </ChartCard>
  );
}

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

  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { warehouseId?: number; itemId?: number; ageFrom?: number; ageTo?: number; stagnantDays?: number }) => {
      setFilterParams({
        warehouseId: p.warehouseId,
        itemId: p.itemId,
        ageFrom: p.ageFrom,
        ageTo: p.ageTo,
        stagnantDays: p.stagnantDays,
      });
      return {
        warehouseId: p.warehouseId,
        itemId: p.itemId,
        ageFrom: p.ageFrom,
        ageTo: p.ageTo,
        stagnantDays: p.stagnantDays,
      };
    },
    reportApi.ageing,
  );

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

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(columns, "report-ageing");
  return (
    <>
      {/* TASK-v22b B3:图表在上、表格在下(表格本身不动) */}
      <AgeingBucketChart />
      <ProTable<StockAgeingRow>
      rowKey={(r) => `${r.warehouseId}-${r.itemId}-${r.batchId}`}
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无库龄数据" /> }}
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
          <ExportButton key="export" url={exportUrl} filename="库龄呆滞.xlsx" />,
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
