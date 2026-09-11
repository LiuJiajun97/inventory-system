// 总览 Dashboard(SPEC-WEB V2 2.2)
// 顶部 4 张统计卡 + 下方两栏:Top10 库存 + 最近流水
// 数据:现有 /dashboard/summary 与 /stock、/transactions,不改接口

import { useEffect, useMemo, useState } from "react";
import { Row, Col, Table, Spin } from "antd";
import {
  HomeOutlined,
  AppstoreOutlined,
  ImportOutlined,
  ExportOutlined,
  AlertOutlined,
  FieldTimeOutlined,
} from "@ant-design/icons";
import { Link } from "react-router-dom";
import { alertApi, dashboardApi, stockApi, transactionApi } from "../../api";
import { fmtDateTime } from "../../utils/format";
import type {
  DashboardSummary,
  StockRow,
  StockTransaction,
} from "../../types";
import { PageHeader } from "../../components/PageHeader";
import { BizTag } from "../../components/StatusTag";

export function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [stocks, setStocks] = useState<StockRow[]>([]);
  const [txs, setTxs] = useState<StockTransaction[]>([]);
  const [expiryCount, setExpiryCount] = useState(0);
  const [lowStockCount, setLowStockCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // 预警计数只取 total,不阻断主加载
    alertApi.expiry({ page: 1, pageSize: 1 }).then((r) => setExpiryCount(r.total)).catch(() => undefined);
    alertApi.lowStock({ page: 1, pageSize: 1 }).then((r) => setLowStockCount(r.total)).catch(() => undefined);
  }, []);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      dashboardApi.summary(),
      stockApi
        .query({ page: 1, pageSize: 200 })
        .catch(() => ({ rows: [] as StockRow[], total: 0, page: 1, pageSize: 200 })),
      transactionApi
        .query({ page: 1, pageSize: 10 })
        .catch(() => ({ rows: [] as StockTransaction[], total: 0, page: 1, pageSize: 10 })),
    ])
      .then(([sum, st, tx]) => {
        setSummary(sum);
        setStocks(st.rows);
        setTxs(tx.rows);
      })
      .finally(() => setLoading(false));
  }, []);

  const top10 = useMemo(() => {
    return [...stocks]
      .sort((a, b) => Number(b.quantity) - Number(a.quantity))
      .slice(0, 10);
  }, [stocks]);

  const recent10 = useMemo(() => {
    return [...txs]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, 10);
  }, [txs]);

  if (loading) {
    return (
      <div style={{ padding: 80, textAlign: "center" }}>
        <Spin tip="加载中" />
      </div>
    );
  }

  return (
    <>
      <PageHeader title="总览" />

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <div className="stat-card">
            <div className="stat-card-label">
              <HomeOutlined /> 仓库数
            </div>
            <div className="stat-card-value">{summary?.warehouseCount ?? 0}</div>
          </div>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <div className="stat-card">
            <div className="stat-card-label">
              <AppstoreOutlined /> 物品数
            </div>
            <div className="stat-card-value">{summary?.itemCount ?? 0}</div>
          </div>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <div className="stat-card">
            <div className="stat-card-label">
              <ImportOutlined style={{ color: "#16a34a" }} /> 今日入库
            </div>
            <div className="stat-card-value" style={{ color: "#16a34a" }}>
              {Number(summary?.todayInboundQty ?? 0).toFixed(2)}
            </div>
            <div style={{ fontSize: 12, color: "#9ca3af" }}>
              {summary?.todayInboundCount ?? 0} 单
            </div>
          </div>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <div className="stat-card">
            <div className="stat-card-label">
              <ExportOutlined style={{ color: "#d97706" }} /> 今日出库
            </div>
            <div className="stat-card-value" style={{ color: "#d97706" }}>
              {Number(summary?.todayOutboundQty ?? 0).toFixed(2)}
            </div>
            <div style={{ fontSize: 12, color: "#9ca3af" }}>
              {summary?.todayOutboundCount ?? 0} 单
            </div>
          </div>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Link to="/alerts">
            <div className="stat-card">
              <div className="stat-card-label">
                <FieldTimeOutlined style={{ color: "#dc2626" }} /> 临期预警(30 天)
              </div>
              <div className="stat-card-value" style={{ color: "#dc2626" }}>
                {expiryCount}
              </div>
              <div style={{ fontSize: 12, color: "#9ca3af" }}>点击处理 →</div>
            </div>
          </Link>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Link to="/alerts">
            <div className="stat-card">
              <div className="stat-card-label">
                <AlertOutlined style={{ color: "#d97706" }} /> 低库存预警
              </div>
              <div className="stat-card-value" style={{ color: "#d97706" }}>
                {lowStockCount}
              </div>
              <div style={{ fontSize: 12, color: "#9ca3af" }}>点击处理 →</div>
            </div>
          </Link>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <div className="table-card">
            <div
              style={{
                fontSize: 14,
                fontWeight: 600,
                marginBottom: 12,
                color: "#1f2937",
              }}
            >
              库存 Top 10
            </div>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={top10}
              scroll={{ x: 480 }}
              columns={[
                { title: "排名", width: 60, render: (_v, _r, idx) => idx + 1 },
                {
                  title: "物品",
                  dataIndex: ["item", "itemName"],
                  ellipsis: true,
                  render: (v: string, r: StockRow) =>
                    v ? (
                      <span>
                        {v}{" "}
                        <span style={{ color: "#9ca3af", fontSize: 12 }}>
                          ({r.item?.itemCode})
                        </span>
                      </span>
                    ) : (
                      "-"
                    ),
                },
                {
                  title: "仓库",
                  dataIndex: ["warehouse", "warehouseName"],
                  width: 120,
                  ellipsis: true,
                },
                {
                  title: "数量",
                  dataIndex: "quantity",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string | number) => Number(v).toFixed(4),
                },
              ]}
            />
          </div>
        </Col>
        <Col xs={24} lg={12}>
          <div className="table-card">
            <div
              style={{
                fontSize: 14,
                fontWeight: 600,
                marginBottom: 12,
                color: "#1f2937",
              }}
            >
              最近流水(最新 10 条)
            </div>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={recent10}
              scroll={{ x: 540 }}
              columns={[
                {
                  title: "时间",
                  dataIndex: "createdAt",
                  width: 150,
                  render: (v: string) =>
                    fmtDateTime(v),
                },
                {
                  title: "业务",
                  dataIndex: "bizCode",
                  width: 70,
                  render: (v: string) => <BizTag biz={v} />,
                },
                {
                  title: "物品",
                  dataIndex: ["item", "itemName"],
                  ellipsis: true,
                },
                {
                  title: "变动量",
                  dataIndex: "changeQty",
                  width: 110,
                  align: "right",
                  render: (v: string | number) => {
                    const n = Number(v);
                    return (
                      <span
                        className={n >= 0 ? "qty-positive" : "qty-negative"}
                      >
                        {n >= 0 ? "+" : ""}
                        {n.toFixed(4)}
                      </span>
                    );
                  },
                },
              ]}
            />
          </div>
        </Col>
      </Row>
    </>
  );
}