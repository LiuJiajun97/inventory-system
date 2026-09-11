// 页面标题行:标题 18px 半粗 + 右侧操作按钮
import type { ReactNode } from "react";

export function PageHeader({
  title,
  extra,
}: {
  title: string;
  extra?: ReactNode;
}) {
  return (
    <div className="page-header">
      <h2 className="page-header-title">{title}</h2>
      {extra && <div className="page-header-extra">{extra}</div>}
    </div>
  );
}