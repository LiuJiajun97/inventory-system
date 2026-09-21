// 系统监控页(仅 admin):本机 CPU/内存/JVM/磁盘实时快照
// 数据源:GET /monitor/overview,5 秒轮询,离开页面停止轮询
// 只读快照,无历史曲线;前端仅用 antd 原生组件(Statistic/Progress/Table/Descriptions)

import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  App,
  Card,
  Col,
  Descriptions,
  Progress,
  Row,
  Spin,
  Statistic,
  Table,
  Tag,
  theme,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  ApiOutlined,
  ClusterOutlined,
  FieldTimeOutlined,
  HddOutlined,
} from "@ant-design/icons";
import { monitorApi } from "../../api";
import { PageHeader } from "../../components/PageHeader";
import type { MonitorDisk, MonitorOverview } from "../../types";

// ===== 页面内工具函数 =====

/** 1 MB / 1 GB 的字节数 */
const MB = 1024 * 1024;
const GB = 1024 * 1024 * 1024;

/** 字节按量级自动转 MB/GB(保留 1 位小数);空值或负值显示 "-" */
const fmtBytes = (bytes?: number | null): string => {
  if (bytes == null || bytes < 0) return "-";
  if (bytes >= GB) return `${(bytes / GB).toFixed(1)} GB`;
  if (bytes >= MB) return `${(bytes / MB).toFixed(1)} MB`;
  return `${bytes} B`;
};

/** 0~1 占用率 → 百分比(四舍五入并夹到 0~100);null 原样返回 */
const pct = (v?: number | null): number | null =>
  v == null ? null : Math.max(0, Math.min(100, Math.round(v * 100)));

/** 秒 → x 天 x 时 x 分(相对时长,不涉及绝对时间) */
const fmtUptime = (sec: number): string => {
  const d = Math.floor(sec / 86400);
  const h = Math.floor((sec % 86400) / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const parts: string[] = [];
  if (d > 0) parts.push(`${d} 天`);
  if (h > 0) parts.push(`${h} 时`);
  parts.push(`${m} 分`);
  return parts.join("");
};

/** 当前时间 HH:mm:ss(用于"更新于"展示) */
const nowHms = (): string => new Date().toTimeString().slice(0, 8);

export function MonitorPage() {
  const { token } = theme.useToken();
  const { message } = App.useApp();
  const [data, setData] = useState<MonitorOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [updatedAt, setUpdatedAt] = useState("");
  // 请求失败只提示一次,恢复成功再复位,避免 5 秒轮询刷屏
  const failNotified = useRef(false);

  useEffect(() => {
    let timer: number | undefined;
    const load = async () => {
      try {
        const d = await monitorApi.overview();
        setData(d);
        failNotified.current = false;
        setUpdatedAt(nowHms());
      } catch {
        if (!failNotified.current) {
          failNotified.current = true;
          message.error("监控数据加载失败,将自动重试");
        }
      } finally {
        setLoading(false);
      }
    };
    load();
    timer = window.setInterval(load, 5000);
    return () => window.clearInterval(timer);
  }, [message]);

  // 可用空间最紧张的分区(顶部磁盘卡展示)
  const minUsableDisk = useMemo<MonitorDisk | null>(() => {
    if (!data || data.disks.length === 0) return null;
    return data.disks.reduce((min, d) => (d.usable < min.usable ? d : min), data.disks[0]);
  }, [data]);

  // 使用率配色:≥80% 红,≥60% 橙,其余主色
  const strokeFor = (p: number): string =>
    p >= 80 ? token.colorError : p >= 60 ? token.colorWarning : token.colorPrimary;

  if (loading && !data) {
    return (
      <div style={{ padding: 80, textAlign: "center" }}>
        <Spin tip="加载中" />
      </div>
    );
  }

  const sysCpu = data ? pct(data.systemCpuUsage) : null;
  const procCpu = data ? pct(data.processCpuUsage) : null;
  const memPct =
    data && data.mem.total > 0
      ? Math.round((data.mem.used / data.mem.total) * 100)
      : null;
  const heapPct =
    data && data.jvm.heapMax > 0
      ? Math.round((data.jvm.heapUsed / data.jvm.heapMax) * 100)
      : null;
  const diskUsed = minUsableDisk ? minUsableDisk.total - minUsableDisk.usable : 0;
  const diskPct =
    minUsableDisk && minUsableDisk.total > 0
      ? Math.round((diskUsed / minUsableDisk.total) * 100)
      : null;

  // 环形进度:null 显示 "-",不进 Progress;外层固定 72 宽居中
  const circle = (value: number | null) => (
    <div style={{ width: 72, margin: "0 auto", textAlign: "center" }}>
      {value == null ? (
        <div
          style={{
            width: 72,
            height: 72,
            lineHeight: "72px",
            textAlign: "center",
            fontSize: 24,
            color: token.colorTextQuaternary,
          }}
        >
          -
        </div>
      ) : (
        <Progress
          type="circle"
          percent={value}
          size={[72, 72]}
          strokeWidth={8}
          strokeColor={strokeFor(value)}
          format={(p) => `${p}%`}
        />
      )}
    </div>
  );

  const diskColumns: ColumnsType<MonitorDisk> = [
    { title: "盘符", dataIndex: "mount", width: 90 },
    {
      title: "总容量",
      dataIndex: "total",
      align: "right",
      render: (v: number) => fmtBytes(v),
    },
    {
      title: "已用",
      align: "right",
      render: (_v, r) => fmtBytes(r.total - r.usable),
    },
    {
      title: "可用",
      dataIndex: "usable",
      align: "right",
      render: (v: number) => fmtBytes(v),
    },
    {
      title: "使用率",
      width: 200,
      render: (_v, r) => {
        const rate = r.total > 0 ? Math.round(((r.total - r.usable) / r.total) * 100) : 0;
        return (
          <Progress
            percent={rate}
            size="small"
            strokeColor={strokeFor(rate)}
            format={(p) => `${p}%`}
          />
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="系统监控"
        extra={
          <span style={{ fontSize: 13, color: token.colorTextTertiary }}>
            每 5 秒自动刷新
            {updatedAt ? ` · 更新于 ${updatedAt}` : ""}
          </span>
        }
      />

      {/* 顶部 4 张统计卡 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Row gutter={16} align="middle" wrap={false}>
              <Col flex="96px">{circle(sysCpu)}</Col>
              <Col flex="auto">
                <Statistic
                  title={<IconTitle icon={<ApiOutlined />} label="系统 CPU" />}
                  value={sysCpu ?? "-"}
                  suffix={sysCpu != null ? "%" : ""}
                />
                <div style={{ fontSize: 12, color: token.colorTextQuaternary, marginTop: 4 }}>
                  进程 CPU {procCpu != null ? `${procCpu}%` : "-"}
                </div>
              </Col>
            </Row>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Row gutter={16} align="middle" wrap={false}>
              <Col flex="96px">{circle(memPct)}</Col>
              <Col flex="auto">
                <Statistic
                  title={<IconTitle icon={<ClusterOutlined />} label="物理内存" />}
                  value={data ? fmtBytes(data.mem.used) : "-"}
                />
                <div style={{ fontSize: 12, color: token.colorTextQuaternary, marginTop: 4 }}>
                  总量 {data ? fmtBytes(data.mem.total) : "-"}
                </div>
              </Col>
            </Row>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Row gutter={16} align="middle" wrap={false}>
              <Col flex="96px">{circle(heapPct)}</Col>
              <Col flex="auto">
                <Statistic
                  title={<IconTitle icon={<HddOutlined />} label="JVM 堆" />}
                  value={data ? fmtBytes(data.jvm.heapUsed) : "-"}
                />
                <div style={{ fontSize: 12, color: token.colorTextQuaternary, marginTop: 4 }}>
                  上限 {data ? fmtBytes(data.jvm.heapMax) : "-"}
                </div>
              </Col>
            </Row>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Row gutter={16} align="middle" wrap={false}>
              <Col flex="96px">{circle(diskPct)}</Col>
              <Col flex="auto">
                <Statistic
                  title={<IconTitle icon={<FieldTimeOutlined />} label="磁盘(最紧张分区)" />}
                  value={minUsableDisk ? fmtBytes(minUsableDisk.usable) : "-"}
                />
                <div style={{ fontSize: 12, color: token.colorTextQuaternary, marginTop: 4 }}>
                  {minUsableDisk ? `${minUsableDisk.mount} 剩余` : "-"}
                </div>
              </Col>
            </Row>
          </Card>
        </Col>
      </Row>

      {/* 磁盘分区表 */}
      <Card style={{ marginTop: 16 }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 600,
            marginBottom: 12,
            color: token.colorText,
          }}
        >
          磁盘分区
          <Tag color="blue" style={{ marginLeft: 8 }}>
            使用率超 80% 变红
          </Tag>
        </div>
        <Table<MonitorDisk>
          rowKey="mount"
          size="small"
          pagination={false}
          dataSource={data?.disks ?? []}
          columns={diskColumns}
        />
      </Card>

      {/* 进程信息卡 */}
      <Card style={{ marginTop: 16 }}>
        <Descriptions
          title="进程信息"
          bordered
          size="small"
          column={{ xs: 1, sm: 2, lg: 3 }}
        >
          <Descriptions.Item label="运行时长">
            {data ? fmtUptime(data.jvm.uptimeSeconds) : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="线程数">
            {data ? data.jvm.threadCount : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="系统负载">
            {data && data.loadAverage < 0 ? "-" : data ? data.loadAverage.toFixed(2) : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="GC 次数">
            {data && data.gcCollectionCount < 0 ? "-" : data ? data.gcCollectionCount : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="JVM 版本">
            {data ? data.jvm.javaVersion : "-"}
          </Descriptions.Item>
          <Descriptions.Item label="操作系统">
            {data ? `${data.os.name} (${data.os.arch})` : "-"}
            <div style={{ fontSize: 12, color: token.colorTextQuaternary }}>
              用户 {data?.os.userName ?? "-"}
            </div>
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </>
  );
}

/** 统计卡标题:小图标 + 文字(与仪表板风格一致) */
function IconTitle({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        fontSize: 13,
        color: "rgba(0,0,0,0.45)",
      }}
    >
      <span style={{ color: "rgba(0,0,0,0.45)" }}>{icon}</span>
      {label}
    </span>
  );
}
