// 库存查询(SPEC-WEB V2 2.7)
// 基于 ProTable:筛选字段由 columns 配置驱动(仓库/物品关键字/批次号)
// 数量 0 行灰色弱化(row-zero);可用量为负红色

import { useEffect, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import { stockApi, warehouseApi } from "../../api";
import type { StockRow, Warehouse } from "../../types";
import { fmtDate } from "../../utils/format";

export function StockQueryPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);

  // 仓库下拉数据源(异步加载,仅用于筛选项)
  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable 发 current/pageSize,后端吃 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    warehouseId?: number;
    itemKeyword?: string;
    batchNo?: string;
  }) => {
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
          {Number(r.quantity).toFixed(4)}
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
          {r.preAllocatedQty == null ? "-" : Number(r.preAllocatedQty).toFixed(4)}
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
            {Number(r.availableQty).toFixed(4)}
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
      columns={columns}
      request={request}
      headerTitle={false}
      options={false}
      search={{ labelWidth: "auto", defaultCollapsed: false, span: 6 }}
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
