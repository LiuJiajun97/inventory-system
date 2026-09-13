// 库存查询(SPEC-WEB V2 2.7)
// 基于 ProTable:筛选字段由 columns 配置驱动(仓库/物品关键字/批次号)
// 数量 0 行灰色弱化(row-zero);可用量为负红色

import { useEffect, useMemo, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import { stockApi, warehouseApi } from "../../api";
import { ExportButton } from "../../components/ExportButton";
import { usePermission } from "../../auth/usePermission";
import type { StockRow, Warehouse } from "../../types";
import { fmtDate, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";

export function StockQueryPage() {
  const { hasPerm } = usePermission();
  const canExport = hasPerm("stock:export");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);

  // 仓库下拉数据源(异步加载,仅用于筛选项)
  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable 发 current/pageSize,后端吃 page/pageSize
    // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

const request = async (params: {
    current?: number;
    pageSize?: number;
    warehouseId?: number;
    itemKeyword?: string;
    batchNo?: string;
  }) => {
    setFilterParams({
      warehouseId: params.warehouseId,
      itemKeyword: params.itemKeyword,
      batchNo: params.batchNo,    });
    const res = await stockApi.query({
      warehouseId: params.warehouseId,
      itemKeyword: params.itemKeyword,
      batchNo: params.batchNo,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };
  // 导出 URL:带当前筛选条件(导出不分页)
  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    if (filterParams.warehouseId !== undefined && filterParams.warehouseId !== "") p.set("warehouseId", String(filterParams.warehouseId));
    if (filterParams.itemKeyword !== undefined && filterParams.itemKeyword !== "") p.set("itemKeyword", String(filterParams.itemKeyword));
    if (filterParams.batchNo !== undefined && filterParams.batchNo !== "") p.set("batchNo", String(filterParams.batchNo));
    const qs = p.toString();
    return "/stock/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);


  const columns: ProColumns<StockRow>[] = [
    {
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      hideInTable: true,
      formItemProps: { name: "warehouseId" },
      fieldProps: {
        allowClear: true,
        placeholder: "全部仓库",
        options: warehouses.map((w) => ({
          label: w.warehouseName,
          value: w.id,
        })),
      },
    },
    {
      title: "物品",
      dataIndex: "itemKeyword",
      hideInTable: true,
      fieldProps: { placeholder: "编码或名称", allowClear: true },
    },
    {
      title: "批次号",
      dataIndex: "batchNo",
      hideInTable: true,
      fieldProps: { placeholder: "批次号", allowClear: true },
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
      width: 240,
      ellipsis: true,
      search: false,
      render: (_v, r) => `${r.item?.itemName ?? "-"} ${r.item?.itemCode ?? "-"}`,
    },
    {
      title: "批次",
      dataIndex: ["batch", "batchNo"],
      width: 140,
      ellipsis: true,
      search: false,
    },
    {
      title: "库位",
      dataIndex: ["location", "locationCode"],
      width: 120,
      ellipsis: true,
      search: false,
    },
    {
      title: "数量",
      dataIndex: "quantity",
      width: 140,
      align: "right",
      search: false,
      render: (_v, r) => (
        <span className="num-cell">
          {fmtQty(r.quantity)}
          <span style={{ color: "#9ca3af", marginLeft: 4, fontSize: 12 }}>
            {r.item?.unit ?? ""}
          </span>
        </span>
      ),
    },
    {
      title: "已预占",
      dataIndex: "preAllocatedQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => (
        <span className="num-cell">
          {fmtQty(r.preAllocatedQty)}
        </span>
      ),
    },
    {
      title: "可用量",
      dataIndex: "availableQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) =>
        r.availableQty == null ? (
          "-"
        ) : (
          <span
            className="num-cell"
            style={{ color: Number(r.availableQty) < 0 ? "#cf1322" : undefined }}
          >
            {fmtQty(r.availableQty)}
          </span>
        ),
    },
    {
      title: "保质期",
      dataIndex: ["batch", "expiryDate"],
      width: 120,
      search: false,
      render: (_v, r) => fmtDate(r.batch?.expiryDate),
    },
  ];

  return (
    <ProTable<StockRow>
      rowKey="id"
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无库存" /> }}
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
          canExport && (
            <ExportButton key="export" url={exportUrl} filename="库存.xlsx" />
          ),
        ],
      }}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
      }}
      scroll={{ x: 980 }}
      rowClassName={(r) => (Number(r.quantity) === 0 ? "row-zero" : "")}
    />
  );
}
