// 总览 Dashboard(SPEC-WEB V2 2.3 + TASK-v22b B2 图表化升级)
// 顶部统计卡(count-up 数字 + 环比行)+ 图表区(近 30 天出入库趋势/各仓库存金额占比/待办)
// + 下方两栏:Top10 库存 + 最近流水
// 数据:现有 /dashboard/summary 与 /stock、/transactions、/reports/cost、/alerts、各单据列表,不改接口

import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import { Row, Col, Table, Spin, Card, List, Badge } from "antd";
import {
  HomeOutlined,
  AppstoreOutlined,
  ImportOutlined,
  ExportOutlined,
  AlertOutlined,
  FieldTimeOutlined,
  AuditOutlined,
} from "@ant-design/icons";
import { Link } from "react-router-dom";
import { Line, Pie } from "@ant-design/charts";
import dayjs from "dayjs";
import {
  alertApi,
  dashboardApi,
  settlementApi,
  stockApi,
  transactionApi,
  reportApi,
  purchaseApi,
  salesApi,
  transferApi,
  stocktakeApi,
  adjustApi,
} from "../../api";
import { fmtDateTime, fmtMoney, fmtQty } from "../../utils/format";
import type { DashboardSummary, StockRow, StockTransaction } from "../../types";
import type { CostReportRow } from "../../types/report";
import { BizTag } from "../../components/StatusTag";
import { useCountUp } from "../../hooks/useCountUp";
import { ChartCard } from "../../components/ChartCard";

/** 获取问候语(上午/下午/晚上) */
function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "上午好";
  if (hour < 18) return "下午好";
  return "晚上好";
}

/** 获取当前时间 HH:mm */
function getCurrentTime(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
}

// 月统计:笔数/入库量(正变动)/出库量(负变动绝对值)
interface MonthStat {
  count: number;
  inQty: number;
  outQty: number;
}

/** 环比行小字:本月 vs 上月;数据不足 2 个月或上月为 0 时显示 "—" */
function TrendLine({
  cur,
  prev,
  hasPrev,
  goodUp,
  format,
}: {
  cur: number;
  prev: number;
  hasPrev: boolean;
  // goodUp=true:上升为好(绿);false:上升为坏(红,如出库)
  goodUp: boolean;
  format: (n: number) => string;
}) {
  // 数据不足 2 个月 → 灰色 "—"
  if (!hasPrev) {
    return (
      <span style={{ fontSize: 12, color: "#9ca3af" }}>
        环比 {format(cur)} vs —
      </span>
    );
  }
  if (prev === 0) {
    return (
      <span style={{ fontSize: 12, color: "#9ca3af" }}>
        环比 {format(cur)} vs 上月 0
      </span>
    );
  }
  const pct = ((cur - prev) / prev) * 100;
  const up = cur >= prev;
  // 持平为中性灰
  const color = cur === prev ? "#9ca3af" : (up === goodUp ? "#16a34a" : "#dc2626");
  return (
    <span style={{ fontSize: 12, color }}>
      环比 {format(cur)} vs 上月 {format(prev)}{" "}
      {up ? "↑" : "↓"} {Math.abs(pct).toFixed(1)}%
    </span>
  );
}

// 统计卡:主数字 28px 加粗等宽 + count-up 滚动 + 环比/备注行
function StatCard({
  icon,
  iconClass,
  label,
  value,
  renderValue,
  trend,
  sub,
  cardClass,
  linkTo,
  valueStyle,
}: {
  icon: React.ReactNode;
  iconClass: string;
  label: string;
  value: number;
  renderValue: (n: number) => string;
  // 主数字附加样式(如应付/应收正负色)
  valueStyle?: CSSProperties;
  // 环比行(小字)
  trend?: React.ReactNode;
  // 原有备注小字(与环比行可同时展示)
  sub?: React.ReactNode;
  cardClass?: string;
  linkTo?: string;
}) {
  const display = useCountUp(value);
  const body = (
    <div className={`stat-card${cardClass ? ` ${cardClass}` : ""}`}>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div className={`stat-icon ${iconClass}`}>{icon}</div>
        <div>
          <div className="stat-card-label">{label}</div>
          <div
            className="stat-card-value"
            style={{ fontSize: 28, fontWeight: 700, fontVariantNumeric: "tabular-nums", ...valueStyle }}
          >
            {renderValue(display)}
          </div>
          {trend}
          {sub}
        </div>
      </div>
    </div>
  );
  if (linkTo) {
    return (
      <Link to={linkTo} style={{ display: "block", height: "100%" }}>
        {body}
      </Link>
    );
  }
  return body;
}

export function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [stocks, setStocks] = useState<StockRow[]>([]);
  const [txs, setTxs] = useState<StockTransaction[]>([]);
  const [expiryCount, setExpiryCount] = useState(0);
  const [lowStockCount, setLowStockCount] = useState(0);
  // V18 结算:应付/应收余额两卡
  const [settlement, setSettlement] = useState<{ apBalance: number; arBalance: number } | null>(null);
  const [loading, setLoading] = useState(true);

  // TASK-v22b:近 60 天流水(环比 + 30 天趋势共用);本地库数据量小(<1000),前端聚合可行
  const [tx60, setTx60] = useState<StockTransaction[]>([]);
  // TASK-v22b:各仓库存金额(成本报表全量,前端按仓聚合)
  const [costRows, setCostRows] = useState<CostReportRow[]>([]);
  // TASK-v22b:待审批单据数(按单据类型分开计数,待办行各自跳对应列表页)
  const [pendingByType, setPendingByType] = useState<
    Record<"purchase" | "sales" | "transfer" | "stocktake" | "adjust", number>
  >({ purchase: 0, sales: 0, transfer: 0, stocktake: 0, adjust: 0 });

  useEffect(() => {
    settlementApi.dashboard().then(setSettlement).catch(() => undefined);
  }, []);

  useEffect(() => {
    // 预警计数只取 total,不阻断主加载
    alertApi.expiry({ page: 1, pageSize: 1 }).then((r) => setExpiryCount(r.total)).catch(() => undefined);
    alertApi.lowStock({ page: 1, pageSize: 1 }).then((r) => setLowStockCount(r.total)).catch(() => undefined);
  }, []);

  useEffect(() => {
    // TASK-v22b:环比/趋势数据(近 60 天)与成本报表数据,失败不阻断主加载
    const from = dayjs().subtract(60, "day").format("YYYY-MM-DD");
    transactionApi
      .query({ page: 1, pageSize: 200, from })
      .then((r) => setTx60(r.rows))
      .catch(() => undefined);
    reportApi.cost().then((r) => setCostRows(r.rows)).catch(() => undefined);
    // 待审批:采购/销售/调拨/盘点/调整五类单据 pending 分类计数(待办行各自跳对应列表页)
    const p = { status: "pending", page: 1, pageSize: 1 };
    Promise.all([purchaseApi.list(p), salesApi.list(p), transferApi.list(p), stocktakeApi.list(p), adjustApi.list(p)])
      .then(([pu, sa, tr, st, ad]) =>
        setPendingByType({
          purchase: pu.total,
          sales: sa.total,
          transfer: tr.total,
          stocktake: st.total,
          adjust: ad.total,
        }),
      )
      .catch(() => undefined);
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

  // 环比:按 created_at 月分组算本月/上月笔数与入出量
  const monthTrend = useMemo(() => {
    const curKey = dayjs().format("YYYY-MM");
    const prevKey = dayjs().subtract(1, "month").format("YYYY-MM");
    const cur: MonthStat = { count: 0, inQty: 0, outQty: 0 };
    const prev: MonthStat = { count: 0, inQty: 0, outQty: 0 };
    let hasPrev = false;
    tx60.forEach((t) => {
      const key = dayjs(t.createdAt).format("YYYY-MM");
      const stat = key === curKey ? cur : key === prevKey ? prev : null;
      if (!stat) return;
      if (key === prevKey) hasPrev = true;
      stat.count += 1;
      const q = Number(t.changeQty);
      if (q >= 0) stat.inQty += q;
      else stat.outQty += -q;
    });
    return { cur, prev, hasPrev };
  }, [tx60]);

  // 近 30 天出入库趋势:按 created_at 日聚合流水数量(入库=正变动,出库=负变动绝对值)
  const trend30 = useMemo(() => {
    const days: { date: string; inbound: number; outbound: number }[] = [];
    for (let i = 29; i >= 0; i--) {
      days.push({ date: dayjs().subtract(i, "day").format("MM-DD"), inbound: 0, outbound: 0 });
    }
    const map = new Map(days.map((d) => [d.date, d]));
    tx60.forEach((t) => {
      const d = map.get(dayjs(t.createdAt).format("MM-DD"));
      if (!d) return;
      const q = Number(t.changeQty);
      if (q >= 0) d.inbound += q;
      else d.outbound += -q;
    });
    return days.flatMap((d) => [
      { date: d.date, series: "入库量", value: d.inbound },
      { date: d.date, series: "出库量", value: d.outbound },
    ]);
  }, [tx60]);

  // 各仓库存金额占比:成本报表按仓聚合 amount(元,字符串转 Number)
  const warehouseAmounts = useMemo(() => {
    const m = new Map<string, number>();
    costRows.forEach((r) => {
      const name = r.warehouseName ?? "未知仓库";
      m.set(name, (m.get(name) ?? 0) + Number(r.amount || 0));
    });
    return Array.from(m.entries()).map(([name, amount]) => ({ name, amount: Number(amount.toFixed(2)) }));
  }, [costRows]);

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

  // 计算 Top10 最大数量用于数据条比例
  const maxQty = useMemo(() => {
    if (top10.length === 0) return 0;
    return Math.max(...top10.map((r) => Number(r.quantity)));
  }, [top10]);

  if (loading) {
    return (
      <div style={{ padding: 80, textAlign: "center" }}>
        <Spin tip="加载中" />
      </div>
    );
  }

  const totalAlerts = expiryCount + lowStockCount;
  // 无环比数据源的卡片统一灰色 "—" 占位(中性)
  const neutralTrend = <span style={{ fontSize: 12, color: "#9ca3af" }}>环比 —</span>;

  return (
    <>
      {/* 欢迎语区域 */}
      <div className="welcome-section">
        <h1 className="welcome-title">
          {getGreeting()}, 系统管理员
        </h1>
        <p className="welcome-subtitle">
          今日 {totalAlerts} 项预警待处理 · 数据截至 {getCurrentTime()}
        </p>
      </div>

      <Row gutter={[16, 16]}>
        {/* 仓库数 */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<HomeOutlined />}
            iconClass="blue"
            label="仓库数"
            value={summary?.warehouseCount ?? 0}
            renderValue={(n) => String(Math.round(n))}
            trend={neutralTrend}
          />
        </Col>

        {/* 物品数 */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<AppstoreOutlined />}
            iconClass="cyan"
            label="物品数"
            value={summary?.itemCount ?? 0}
            renderValue={(n) => String(Math.round(n))}
            trend={neutralTrend}
          />
        </Col>

        {/* 今日入库 */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<ImportOutlined />}
            iconClass="green"
            label="今日入库"
            value={Number(summary?.todayInboundQty ?? 0)}
            renderValue={fmtQty}
            trend={
              <TrendLine
                cur={monthTrend.cur.inQty}
                prev={monthTrend.prev.inQty}
                hasPrev={monthTrend.hasPrev}
                goodUp
                format={fmtQty}
              />
            }
            sub={<span style={{ fontSize: 12, color: "#9ca3af" }}>{summary?.todayInboundCount ?? 0} 单</span>}
          />
        </Col>

        {/* 今日出库 */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<ExportOutlined />}
            iconClass="orange"
            label="今日出库"
            value={Number(summary?.todayOutboundQty ?? 0)}
            renderValue={fmtQty}
            trend={
              <TrendLine
                cur={monthTrend.cur.outQty}
                prev={monthTrend.prev.outQty}
                hasPrev={monthTrend.hasPrev}
                goodUp={false}
                format={fmtQty}
              />
            }
            sub={<span style={{ fontSize: 12, color: "#9ca3af" }}>{summary?.todayOutboundCount ?? 0} 单</span>}
          />
        </Col>

        {/* 应付余额(V18 结算域) */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<AlertOutlined />}
            iconClass="red"
            label="应付余额"
            value={Number(settlement?.apBalance ?? 0)}
            renderValue={fmtMoney}
            valueStyle={Number(settlement?.apBalance ?? 0) < 0 ? { color: "#16a34a" } : { color: "#dc2626" }}
            trend={neutralTrend}
            sub={<span style={{ fontSize: 12, color: "#9ca3af" }}>确认采购票净额 - 已付</span>}
          />
        </Col>

        {/* 应收余额(V18 结算域) */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<FieldTimeOutlined />}
            iconClass="green"
            label="应收余额"
            value={Number(settlement?.arBalance ?? 0)}
            renderValue={fmtMoney}
            valueStyle={Number(settlement?.arBalance ?? 0) < 0 ? { color: "#dc2626" } : { color: "#16a34a" }}
            trend={neutralTrend}
            sub={<span style={{ fontSize: 12, color: "#9ca3af" }}>确认销售票净额 - 已收</span>}
          />
        </Col>

        {/* 临期预警(30 天) - 0 时恢复普通样式 */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<FieldTimeOutlined />}
            iconClass={expiryCount > 0 ? "red" : "blue"}
            label="临期预警(30 天)"
            value={expiryCount}
            renderValue={(n) => String(Math.round(n))}
            cardClass={`clickable${expiryCount > 0 ? " alert-red" : ""}`}
            linkTo="/alerts"
            sub={<span style={{ fontSize: 12, color: "#9ca3af" }}>点击处理 →</span>}
          />
        </Col>

        {/* 低库存预警 - 0 时恢复普通样式 */}
        <Col xs={24} sm={12} md={6}>
          <StatCard
            icon={<AlertOutlined />}
            iconClass={lowStockCount > 0 ? "orange" : "cyan"}
            label="低库存预警"
            value={lowStockCount}
            renderValue={(n) => String(Math.round(n))}
            cardClass={`clickable${lowStockCount > 0 ? " alert-orange" : ""}`}
            linkTo="/alerts"
            sub={<span style={{ fontSize: 12, color: "#9ca3af" }}>点击处理 →</span>}
          />
        </Col>
      </Row>

      {/* TASK-v22b 图表区:近 30 天出入库趋势 + 各仓库存金额占比 + 待办 */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={8}>
          <ChartCard
            title="近 30 天出入库趋势"
            note="按流水 created_at 聚合,数量口径"
            height={260}
            empty={trend30.every((d) => d.value === 0)}
            emptyText="近 30 天暂无出入库流水"
          >
            <Line
              data={trend30}
              xField="date"
              yField="value"
              colorField="series"
              height={260}
              legend={{ color: { position: "right" } }}
              style={{ lineWidth: 2 }}
              tooltip={{ items: [{ channel: "y", valueFormatter: (v: number) => fmtQty(v) }] }}
            />
          </ChartCard>
        </Col>
        <Col xs={24} md={8}>
          <ChartCard
            title="各仓库存金额占比"
            note="成本报表按仓库聚合(移动均价)"
            height={260}
            empty={warehouseAmounts.length === 0}
            emptyText="暂无库存成本数据"
          >
            <Pie
              data={warehouseAmounts}
              angleField="amount"
              colorField="name"
              innerRadius={0.6}
              height={260}
              legend={{ color: { position: "right" } }}
              tooltip={{ items: [{ channel: "y", valueFormatter: (v: number) => fmtMoney(v) }] }}
            />
          </ChartCard>
        </Col>
        <Col xs={24} md={8}>
          {/* 高度由内容定(头38+内容260+边距≈323,与图表卡一致);不写 height:100%——
             图表卡带 marginBottom:16 会把 Row 撑到 339,100% 会被拉高 16px */}
          <Card size="small" title="待办">
            {/* 内容区与图表卡同高(260),行数多时内部滚动,不撑高卡片 */}
            <div style={{ height: 260, overflowY: "auto", paddingRight: 4 }}>
              <List
              size="small"
              dataSource={[
                {
                  key: "expiry",
                  label: "临期预警(30 天)",
                  count: expiryCount,
                  danger: true,
                  to: "/alerts?tab=expiry",
                },
                {
                  key: "lowStock",
                  label: "低库存预警",
                  count: lowStockCount,
                  danger: true,
                  to: "/alerts?tab=low",
                },
                {
                  key: "purchase",
                  label: "待审批 · 采购订单",
                  count: pendingByType.purchase,
                  danger: false,
                  to: "/purchase-orders?status=pending",
                },
                {
                  key: "sales",
                  label: "待审批 · 销售订单",
                  count: pendingByType.sales,
                  danger: false,
                  to: "/sales-orders?status=pending",
                },
                {
                  key: "transfer",
                  label: "待审批 · 调拨单",
                  count: pendingByType.transfer,
                  danger: false,
                  to: "/transfers?status=pending",
                },
                {
                  key: "stocktake",
                  label: "待审批 · 盘点单",
                  count: pendingByType.stocktake,
                  danger: false,
                  to: "/stocktakes?status=pending",
                },
                {
                  key: "adjust",
                  label: "待审批 · 库存调整",
                  count: pendingByType.adjust,
                  danger: false,
                  to: "/stock-adjusts?status=pending",
                },
              ]}
              renderItem={(item: { key: string; label: string; count: number; danger: boolean; to: string }) => (
                <Link to={item.to} style={{ display: "block" }}>
                  <List.Item
                    style={{
                      cursor: "pointer",
                      paddingLeft: 0,
                      paddingRight: 0,
                      background: "transparent",
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "center", gap: 8, width: "100%" }}>
                      <Badge
                        status="error"
                        color={item.count > 0 ? (item.danger ? "#dc2626" : "#d97706") : "#d1d5db"}
                        text={
                          <span style={{ flex: 1, fontSize: 13, color: "#374151" }}>{item.label}</span>
                        }
                      />
                      <span
                        style={{
                          fontSize: 16,
                          fontWeight: 700,
                          fontVariantNumeric: "tabular-nums",
                          color: item.count > 0 ? (item.danger ? "#dc2626" : "#d97706") : "#9ca3af",
                        }}
                      >
                        {item.count}
                      </span>
                      <AuditOutlined style={{ color: "#c4c9d4" }} />
                    </div>
                  </List.Item>
                </Link>
              )}
              />
            </div>
          </Card>
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
                  width: 140,
                  align: "right",
                  className: "num-cell",
                  render: (v: string | number) => {
                    const num = Number(v);
                    const pct = maxQty > 0 ? (num / maxQty) * 100 : 0;
                    return (
                      <div>
                        <div style={{ marginBottom: 4, fontSize: 13 }}>
                          {fmtQty(num)}
                        </div>
                        <div className="data-bar">
                          <div
                            className="data-bar-fill"
                            style={{ width: `${Math.min(pct, 100)}%` }}
                          />
                        </div>
                      </div>
                    );
                  },
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
                        {fmtQty(n)}
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
