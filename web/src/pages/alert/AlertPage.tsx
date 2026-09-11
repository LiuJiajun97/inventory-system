// 预警中心(一期新增)
// 临期预警(到期日 ≤ 30 天)+ 低库存预警(全仓可用 < minStock),Tab 切换

import { useEffect, useState } from "react";
import { Button, Input, Select, Space, Table, Tabs, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { alertApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { ExpiryAlertRow, LowStockRow } from "../../types/phase1";
import { PageHeader } from "../../components/PageHeader";

export function AlertPage() {
  const [tab, setTab] = useState("expiry");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [whId, setWhId] = useState<number>();
  const [expiry, setExpiry] = useState<ExpiryAlertRow[]>([]);
  const [expiryTotal, setExpiryTotal] = useState(0);
  const [expiryPage, setExpiryPage] = useState(1);
  const [low, setLow] = useState<LowStockRow[]>([]);
  const [lowTotal, setLowTotal] = useState(0);
  const [lowPage, setLowPage] = useState(1);
  const [lowKeyword, setLowKeyword] = useState<string>();
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const loadExpiry = async (pg = 1, ps = 20, w = whId) => {
    setLoading(true);
    try {
      const res = await alertApi.expiry({ warehouseId: w, page: pg, pageSize: ps });
      setExpiry(res.rows);
      setExpiryTotal(res.total);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  const loadLow = async (pg = 1, ps = 20, kw = lowKeyword) => {
    setLoading(true);
    try {
      const res = await alertApi.lowStock({ itemKeyword: kw, page: pg, pageSize: ps });
      setLow(res.rows);
      setLowTotal(res.total);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
    loadExpiry();
    loadLow();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const expiryColumns: ColumnsType<ExpiryAlertRow> = [
    { title: "物品", dataIndex: "itemName", render: (v: string, r) => `${r.itemCode} ${v}` },
    { title: "批次号", dataIndex: "batchNo", width: 140 },
    { title: "仓库", dataIndex: "warehouseName", width: 140 },
    { title: "生产日期", dataIndex: "productionDate", width: 110, render: (v) => v ?? "-" },
    { title: "到期日", dataIndex: "expiryDate", width: 110 },
    {
      title: "剩余天数",
      dataIndex: "daysLeft",
      width: 100,
      align: "right",
      render: (v: number) => (
        <Tag color={v <= 7 ? "red" : v <= 15 ? "orange" : "gold"}>{v} 天</Tag>
      ),
    },
    { title: "库存量", dataIndex: "quantity", width: 100, align: "right", className: "num-cell" },
    { title: "可用量", dataIndex: "availableQty", width: 100, align: "right", className: "num-cell" },
  ];

  const lowColumns: ColumnsType<LowStockRow> = [
    { title: "物品", dataIndex: "itemName", render: (v: string, r) => `${r.itemCode} ${v}` },
    {
      title: "最低库存",
      dataIndex: "minStock",
      width: 110,
      align: "right",
      className: "num-cell",
      render: (v) => (v == null ? "-" : Number(v).toFixed(2)),
    },
    { title: "全仓总量", dataIndex: "totalQty", width: 110, align: "right", className: "num-cell" },
    {
      title: "全仓可用量",
      dataIndex: "availableQty",
      width: 120,
      align: "right",
      className: "num-cell",
      render: (v: string) => (
        <span style={{ color: "#cf1322", fontWeight: 600 }}>{Number(v).toFixed(2)}</span>
      ),
    },
    { title: "单位", dataIndex: "unit", width: 80 },
  ];

  return (
    <>
      <PageHeader title="预警中心" />
      <div className="filter-card">
        <Space wrap>
          {tab === "expiry" ? (
            <>
              <span>仓库:</span>
              <Select
                allowClear
                placeholder="全部仓库"
                style={{ width: 200 }}
                value={whId}
                onChange={(v) => {
                  setWhId(v);
                  setExpiryPage(1);
                  loadExpiry(1, pageSize, v);
                }}
                options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
              />
              <Button onClick={() => loadExpiry(expiryPage, pageSize)}>刷新</Button>
            </>
          ) : (
            <>
              <span>关键字:</span>
              <Space.Compact>
                <Input
                  value={lowKeyword}
                  onChange={(e) => setLowKeyword(e.target.value || undefined)}
                  style={{ width: 220 }}
                  placeholder="物品编码/名称"
                  allowClear
                />
                <Button
                  onClick={() => {
                    setLowPage(1);
                    loadLow(1, pageSize, lowKeyword);
                  }}
                >
                  查询
                </Button>
              </Space.Compact>
              <Button onClick={() => loadLow(lowPage, pageSize, lowKeyword)}>刷新</Button>
            </>
          )}
        </Space>
      </div>
      <div className="table-card">
        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: "expiry",
              label: `临期预警(30 天内)(${expiryTotal})`,
              children: (
                <Table
                  rowKey={(r) => `${r.batchNo}-${r.warehouseId}`}
                  size="small"
                  loading={loading && tab === "expiry"}
                  columns={expiryColumns}
                  dataSource={expiry}
                  scroll={{ x: 900 }}
                  pagination={{
                    current: expiryPage,
                    pageSize,
                    total: expiryTotal,
                    showSizeChanger: true,
                    onChange: (p, ps) => {
                      setExpiryPage(p);
                      setPageSize(ps);
                      loadExpiry(p, ps);
                    },
                    showTotal: (t) => `共 ${t} 条`,
                  }}
                />
              ),
            },
            {
              key: "low",
              label: `低库存预警(${lowTotal})`,
              children: (
                <Table
                  rowKey="itemId"
                  size="small"
                  loading={loading && tab === "low"}
                  columns={lowColumns}
                  dataSource={low}
                  pagination={{
                    current: lowPage,
                    pageSize,
                    total: lowTotal,
                    showSizeChanger: true,
                    onChange: (p, ps) => {
                      setLowPage(p);
                      setPageSize(ps);
                      loadLow(p, ps);
                    },
                    showTotal: (t) => `共 ${t} 条`,
                  }}
                />
              ),
            },
          ]}
        />
      </div>
    </>
  );
}
