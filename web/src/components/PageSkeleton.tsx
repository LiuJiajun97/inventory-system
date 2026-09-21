// 整页骨架屏(TASK-v22c C5)
// 用途:1) App.tsx 路由懒加载的 <Suspense fallback>,页面 chunk 下载期间展示;
// 2) 可复用于列表页首屏等待场景(当前仅接入 Suspense,原因见迭代说明)。
// 视觉:白底 Card + Skeleton active,3 行 45px 条形(与表格统一行高对齐),上方一行模拟筛选行。

import { Card, Skeleton } from "antd";

/** 单行骨架条:45px 高,内部 4 段不等长条模拟表格单元格 */
function SkeletonRow() {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 24,
        height: 45,
        borderBottom: "1px solid #f0f0f0",
        padding: "0 16px",
      }}
    >
      {[120, 200, 140, 90].map((w, i) => (
        <Skeleton
          key={i}
          active
          title={false}
          paragraph={false}
          style={{ width: w, margin: 0 }}
        />
      ))}
    </div>
  );
}

/** 整页骨架:顶部一行(模拟筛选行)+ 3 行表格条 */
export function PageSkeleton() {
  return (
    <Card style={{ margin: 16 }} bodyStyle={{ padding: 16 }}>
      <Skeleton active title={false} paragraph={{ rows: 1, width: "30%" }} />
      <div style={{ marginTop: 16 }}>
        <SkeletonRow />
        <SkeletonRow />
        <SkeletonRow />
      </div>
    </Card>
  );
}
