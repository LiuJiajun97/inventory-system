// 流水查询(SPEC-WEB V2 2.8)
// ProTable 版:筛选字段由 columns 配置驱动(仓库/物品/业务/日期区间)
// 变动量正绿负红 + tabular-nums + 业务 Tag 入库=blue 出库=orange
// 日期区间显式转本地时间串:from=当天 00:00:00,to=结束当天 23:59:59(含结束当天)

import { useEffect, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ProColumns } from "@ant-design/pro-components";
import dayjs from "dayjs";
import { itemApi, transactionApi, warehouseApi } from "../../api";
import type { Item, StockTransaction, Warehouse } from "../../types";
import { fmtDateTime, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { BizTag, BIZ_OPTIONS } from "../../components/StatusTag";
import { proTableRequest } from "../../utils/proTable";

// ProTable dateRange transform 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
function dayPart(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

export function TransactionQueryPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);

  // 仓库/物品下拉数据源(异步加载,仅用于筛选项)
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

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { warehouseId?: number; itemId?: number; bizCode?: string; from?: string; to?: string }) => ({
      warehouseId: p.warehouseId,
      itemId: p.itemId,
      bizCode: p.bizCode as "inbound" | "outbound" | undefined,
      from: p.from,
      to: p.to,
    }),
    transactionApi.query,
  );

  const columns: ProColumns<StockTransaction>[] = [
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
        placeholder: "全部",
        options: items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id })),
      },
    },
    {
      title: "业务",
      dataIndex: "bizCode",
      width: 80,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: BIZ_OPTIONS,
      },
      render: (_v, r) => <BizTag biz={r.bizCode} />,
    },
    {
      title: "日期范围",
      dataIndex: "range",
      valueType: "dateRange",
      hideInTable: true,
      search: {
        // 统一约定:东八区本地时间,空格分隔;开始=当天 00:00:00,结束=当天 23:59:59(含结束当天)
        transform: (value: [unknown, unknown]) => {
          const from = dayPart(value[0]);
          const to = dayPart(value[1]);
          return {
            from: from == null ? undefined : from + " 00:00:00",
            to: to == null ? undefined : to + " 23:59:59",
          };
        },
      },
    },
    {
      title: "时间",
      dataIndex: "createdAt",
      width: 170,
      search: false,
      render: (_v, r) => fmtDateTime(r.createdAt),
    },
    {
      title: "仓库",
      dataIndex: ["warehouse", "warehouseName"],
      width: 140,
      ellipsis: true,
      search: false,
    },
    {
      title: "物品",
      width: 200,
      ellipsis: true,
      search: false,
      render: (_v, r) => (
        <span>
          {r.item?.itemName ?? "-"}
          {r.item?.itemCode && (
            <span style={{ color: "#9ca3af", marginLeft: 4, fontSize: 12 }}>
              ({r.item.itemCode})
            </span>
          )}
        </span>
      ),
    },
    {
      title: "批次",
      dataIndex: ["batch", "batchNo"],
      width: 130,
      ellipsis: true,
      search: false,
    },
    {
      title: "单号",
      dataIndex: "docNo",
      width: 180,
      ellipsis: true,
      search: false,
      render: (_v, r) =>
        r.docNo ? (
          <span style={{ fontFamily: "monospace", fontSize: 12 }}>{r.docNo}</span>
        ) : (
          "-"
        ),
    },
    {
      title: "变动量",
      dataIndex: "changeQty",
      width: 130,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => {
        const n = Number(r.changeQty);
        return (
          <span className={n >= 0 ? "qty-positive" : "qty-negative"}>
            {n >= 0 ? "+" : ""}
            {fmtQty(n)}
          </span>
        );
      },
    },
    {
      title: "结存",
      dataIndex: "afterQty",
      width: 120,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => fmtQty(r.afterQty),
    },
    { title: "操作人", dataIndex: "operator", width: 100, ellipsis: true, search: false },
  ];

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(columns, "transaction-query");
  return (
    <ProTable<StockTransaction>
      rowKey="id"
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无库存流水" /> }}
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
      search={{ labelWidth: "auto", defaultCollapsed: false, span: 6 }}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
      }}
    />
  );
}
