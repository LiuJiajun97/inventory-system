// 预警中心(一期新增)
// ProTable 版:Tabs 双表,每个 tab 各自 ProTable(各自 request + 各自 actionRef,筛选天然隔离)
// 临期预警(到期日 ≤ 30 天)+ 低库存预警(全仓可用 < minStock)
// 刷新按钮放临期 tab 筛选行右侧,点击两个表都 reload

import { useEffect, useRef, useState } from "react";
import { Button, Tabs, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { alertApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { ExpiryAlertRow, LowStockRow } from "../../types/phase1";

export function AlertPage() {
  const [tab, setTab] = useState("expiry");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const expiryRef = useRef<ActionType>();
  const lowRef = useRef<ActionType>();

  // 仓库下拉数据源(异步加载,仅用于临期 tab 筛选项)
  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const requestExpiry = async (params: {
    current?: number;
    pageSize?: number;
    warehouseId?: number;
  }) => {
    const res = await alertApi.expiry({
      warehouseId: params.warehouseId,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

  const requestLow = async (params: {
    current?: number;
    pageSize?: number;
    itemKeyword?: string;
  }) => {
    const res = await alertApi.lowStock({
      itemKeyword: params.itemKeyword || undefined,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  const expiryColumns: ProColumns<ExpiryAlertRow>[] = [
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
      dataIndex: "itemName",
      search: false,
      render: (_v, r) => `${r.itemCode} ${r.itemName}`,
    },
    { title: "批次号", dataIndex: "batchNo", width: 140, search: false },
    { title: "仓库", dataIndex: "warehouseName", width: 140, search: false },
    { title: "生产日期", dataIndex: "productionDate", width: 110, search: false, render: (_v, r) => r.productionDate ?? "-" },
    { title: "到期日", dataIndex: "expiryDate", width: 110, search: false },
    {
      title: "剩余天数",
      dataIndex: "daysLeft",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => (
        <Tag color={r.daysLeft <= 7 ? "red" : r.daysLeft <= 15 ? "orange" : "gold"}>{r.daysLeft} 天</Tag>
      ),
    },
    { title: "库存量", dataIndex: "quantity", width: 100, align: "right", className: "num-cell", search: false },
    { title: "可用量", dataIndex: "availableQty", width: 100, align: "right", className: "num-cell", search: false },
  ];

  const lowColumns: ProColumns<LowStockRow>[] = [
    {
      title: "关键字",
      dataIndex: "itemKeyword",
      hideInTable: true,
      fieldProps: { placeholder: "物品编码/名称", allowClear: true },
    },
    { title: "物品", dataIndex: "itemName", search: false, render: (_v, r) => `${r.itemCode} ${r.itemName}` },
    {
      title: "最低库存",
      dataIndex: "minStock",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      render: (v) => (v == null ? "-" : Number(v).toFixed(2)),
    },
    { title: "全仓总量", dataIndex: "totalQty", width: 110, align: "right", className: "num-cell", search: false },
    {
      title: "全仓可用量",
      dataIndex: "availableQty",
      width: 120,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => (
        <span style={{ color: "#cf1322", fontWeight: 600 }}>{Number(r.availableQty).toFixed(2)}</span>
      ),
    },
    { title: "单位", dataIndex: "unit", width: 80, search: false },
  ];

  return (
    <div className="table-card">
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: "expiry",
            label: "临期预警(30 天内)",
            children: (
              <ProTable<ExpiryAlertRow>
                rowKey={(r) => `${r.batchNo}-${r.warehouseId}`}
                actionRef={expiryRef}
                size="small"
                columns={expiryColumns}
                request={requestExpiry}
                headerTitle={false}
                options={false}
                scroll={{ x: 900 }}
                search={{
                  labelWidth: "auto",
                  defaultCollapsed: false,
                  span: 6,
                  // 刷新按钮放临期 tab 筛选行右侧;点击两个表都 reload
                  optionRender: (_searchConfig, _props, dom) => [
                    ...dom,
                    <Button
                      key="refresh"
                      onClick={() => {
                        expiryRef.current?.reload();
                        lowRef.current?.reload();
                      }}
                    >
                      刷新
                    </Button>,
                  ],
                }}
                pagination={{
                  pageSize: 20,
                  showSizeChanger: true,
                  showTotal: (t) => `共 ${t} 条`,
                }}
              />
            ),
          },
          {
            key: "low",
            label: "低库存预警",
            children: (
              <ProTable<LowStockRow>
                rowKey="itemId"
                actionRef={lowRef}
                size="small"
                columns={lowColumns}
                request={requestLow}
                headerTitle={false}
                options={false}
                search={{ labelWidth: "auto", defaultCollapsed: false, span: 6 }}
                pagination={{
                  pageSize: 20,
                  showSizeChanger: true,
                  showTotal: (t) => `共 ${t} 条`,
                }}
              />
            ),
          },
        ]}
      />
    </div>
  );
}
