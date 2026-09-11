// 单据状态 Tag(一期新增单据:采购/销售/调拨/盘点/调整共用)
// draft=默认、pending=处理中、approved=成功、rejected=错误、completed=成功、voided=默认、closed=警告

import { Tag } from "antd";
import type { DocStatus } from "../types/phase1";

const STYLE: Record<DocStatus, { label: string; color: string }> = {
  draft: { label: "草稿", color: "default" },
  pending: { label: "待审批", color: "processing" },
  approved: { label: "已审批", color: "success" },
  rejected: { label: "已驳回", color: "error" },
  completed: { label: "已完成", color: "success" },
  voided: { label: "已作废", color: "default" },
  closed: { label: "已关闭", color: "warning" },
};

export function DocStatusTag({ status }: { status: string }) {
  const it = STYLE[status as DocStatus] ?? { label: status, color: "default" };
  return <Tag color={it.color}>{it.label}</Tag>;
}

export function docStatusLabel(status: string): string {
  return STYLE[status as DocStatus]?.label ?? status;
}
