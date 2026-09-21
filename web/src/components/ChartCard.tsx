// 图表卡片(TASK-v22b B4):统一样式——中文标题 + 右上小字数据口径说明 + 固定高度
// 空数据时展示 Empty(复用 A 批朴素风格),不渲染空图表

import type { ReactNode } from "react";
import { Card, Empty } from "antd";

interface ChartCardProps {
  /** 卡片标题(中文) */
  title: string;
  /** 右上角数据口径说明小字 */
  note?: string;
  /** 图表区高度,默认 300(仪表盘 260) */
  height?: number;
  /** 数据是否为空(为 true 显示 Empty) */
  empty?: boolean;
  /** 空态提示文案 */
  emptyText?: string;
  /** 图表内容 */
  children: ReactNode;
}

export function ChartCard({ title, note, height = 300, empty, emptyText = "暂无数据", children }: ChartCardProps) {
  return (
    <Card
      size="small"
      style={{ marginBottom: 16 }}
      title={title}
      extra={
        note ? (
          <span style={{ fontSize: 12, fontWeight: 400, color: "#9ca3af" }}>{note}</span>
        ) : undefined
      }
    >
      <div style={{ height, display: "flex", alignItems: "center", justifyContent: "center" }}>
        {empty ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} /> : children}
      </div>
    </Card>
  );
}
