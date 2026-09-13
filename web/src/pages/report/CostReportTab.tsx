// 报表中心 Tab 5:库存成本(移动均价)
// 成本单元 = 仓库+物品+批次;零建表实时回放流水计算,不分页(前端本地分页)
// 均价 4 位小数(2.625 这类均价 2 位会失真),金额 2 位小数;日期默认今天(回放截止)

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import dayjs from "dayjs";
import { itemApi, reportApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { CostReportRow } from "../../types/report";
import { ExportButton } from "../../components/ExportButton";
import { QtyCell } from "./common";
import { EmptyHint } from "../../components/EmptyHint";
import { fmtMoney } from "../../utils/format";

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

  return (
    <ProTable<CostReportRow>
      rowKey={(r) => `${r.warehouseId}-${r.itemId}-${r.batchId}`}
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无成本数据" /> }}
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
          <ExportButton key="export" url={exportUrl} filename="库存成本.xlsx" />,
        ],
      }}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
      }}
      scroll={{ x: 950 }}
    />
  );
}
