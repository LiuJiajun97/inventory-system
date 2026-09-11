// 预警中心(一期新增)
// 临期预警(到期日 ≤ 30 天)+ 低库存预警(全仓可用 < minStock),Tab 切换
// 布局遵循统一列表页骨架:筛选卡(Form inline,右侧刷新按钮)+ Tabs 数据区

import { useEffect, useState } from "react";
import { Button, Form, Input, Select, Space, Table, Tabs, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { alertApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { ExpiryAlertRow, LowStockRow } from "../../types/phase1";
import { ListPageShell } from "../../components/ListPageShell";

export function AlertPage() {
  const [form] = Form.useForm();
  const [tab, setTab] = useState("expiry");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [expiry, setExpiry] = useState<ExpiryAlertRow[]>([]);
  const [expiryTotal, setExpiryTotal] = useState(0);
  const [expiryPage, setExpiryPage] = useState(1);
  const [low, setLow] = useState<LowStockRow[]>([]);
  const [lowTotal, setLowTotal] = useState(0);
  const [lowPage, setLowPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const loadExpiry = async (pg = 1, ps = 20, warehouseId?: number) => {
    setLoading(true);
    try {
      const res = await alertApi.expiry({ warehouseId, page: pg, pageSize: ps });
      setExpiry(res.rows);
      setExpiryTotal(res.total);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  const loadLow = async (pg = 1, ps = 20, itemKeyword?: string) => {
    setLoading(true);
    try {
      const res = await alertApi.lowStock({ itemKeyword, page: pg, pageSize: ps });
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

  // 按当前 tab 加载数据(用于查询/重置/刷新)
  const loadCurrent = (pg: number, ps: number, values: Record<string, unknown>) => {
    if (tab === "expiry") {
      loadExpiry(pg, ps, values.warehouseId as number | undefined);
    } else {
      loadLow(pg, ps, values.itemKeyword as string | undefined);
    }
  };

  const onSearch = (values: Record<string, unknown>) => {
    if (tab === "expiry") setExpiryPage(1);
    else setLowPage(1);
    loadCurrent(1, pageSize, values);
  };

  const onReset = () => {
    form.resetFields();
    if (tab === "expiry") {
      setExpiryPage(1);
      loadExpiry(1, pageSize);
    } else {
      setLowPage(1);
      loadLow(1, pageSize);
    }
  };

  const onRefresh = () => {
    const values = form.getFieldsValue();
    if (tab === "expiry") loadExpiry(expiryPage, pageSize, values.warehouseId as number | undefined);
    else loadLow(lowPage, pageSize, values.itemKeyword as string | undefined);
  };

  // 切 tab 时清空筛选表单,避免另一 tab 的参数串过来
  const onTabChange = (key: string) => {
    form.resetFields();
    setTab(key);
  };

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

  const filterNode = (
    <Form form={form} layout="inline" onFinish={onSearch}>
      {tab === "expiry" ? (
        <Form.Item label="仓库" name="warehouseId">
          <Select
            allowClear
            placeholder="全部仓库"
            style={{ width: 160 }}
            options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
            onChange={(v) => {
              // 仓库下拉选择后立即查询(保持原有交互)
              setExpiryPage(1);
              loadExpiry(1, pageSize, v);
            }}
          />
        </Form.Item>
      ) : (
        <Form.Item label="关键字" name="itemKeyword">
          <Input allowClear placeholder="物品编码/名称" style={{ width: 220 }} />
        </Form.Item>
      )}
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={onReset}>重置</Button>
        </Space>
      </Form.Item>
    </Form>
  );

  return (
    <ListPageShell
      filter={filterNode}
      extra={<Button onClick={onRefresh}>刷新</Button>}
      children={
        <div className="table-card">
          <Tabs
            activeKey={tab}
            onChange={onTabChange}
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
                        const values = form.getFieldsValue();
                        loadExpiry(p, ps, values.warehouseId as number | undefined);
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
                        const values = form.getFieldsValue();
                        loadLow(p, ps, values.itemKeyword as string | undefined);
                      },
                      showTotal: (t) => `共 ${t} 条`,
                    }}
                  />
                ),
              },
            ]}
          />
        </div>
      }
    />
  );
}
