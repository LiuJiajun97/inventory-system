// 单据详情弹窗三段式公共部件(TASK-v22c C4)
// 采购/采购退货/销售/销售退货/入库/出库 6 个列表页的详情 Modal 统一结构:
// 1) DocDetailHeader:头部行(左=状态 Tag + 等宽单号,右=价税合计 20px 主色),底部分隔线;
// 2) 主体:原 Descriptions(去掉已提到头部的字段)+ 明细 Table(不变);
// 3) DocAuditLine:底部灰色小字审计行(创建人/创建时间/审批人/审批时间,缺省字段跳过)。

import { Space } from "antd";
import { DocStatusTag } from "./DocStatusTag";
import { fmtDateTime, fmtMoney } from "../utils/format";

/** 详情弹窗头部行:左 状态+单号,右 价税合计;底部 1px 分隔线 */
export function DocDetailHeader({
  status,
  docNo,
  total,
}: {
  status: string;
  docNo: string;
  /** 价税合计;null/空则右侧不渲染(无金额概念的单子) */
  total?: string | number | null;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "8px 4px",
        marginBottom: 14,
        borderBottom: "1px solid #f0f0f0",
      }}
    >
      <Space size={12} align="center">
        <DocStatusTag status={status} />
        <span style={{ fontFamily: "monospace", fontSize: 16, fontWeight: 700 }}>
          {docNo}
        </span>
      </Space>
      {total != null && total !== "" ? (
        <Space size={8} align="baseline">
          <span style={{ fontSize: 12, color: "#8c8c8c" }}>价税合计</span>
          <span style={{ fontSize: 20, fontWeight: 700, color: "#3056d3" }}>
            {fmtMoney(total)}
          </span>
        </Space>
      ) : null}
    </div>
  );
}

/** 详情弹窗底部审计行:12px 灰字,缺省字段自动跳过;全缺则不渲染 */
export function DocAuditLine({
  creator,
  createdAt,
  updater,
  updatedAt,
  approver,
  approvedAt,
}: {
  creator?: string | null;
  createdAt?: string | null;
  // V25 无审批流单据(报价单/请购单)仅展示更新人/时间,不展示审批人
  updater?: string | null;
  updatedAt?: string | null;
  approver?: string | null;
  approvedAt?: string | null;
}) {
  const parts = [
    creator ? `创建人 ${creator}` : "",
    createdAt ? `创建时间 ${fmtDateTime(createdAt)}` : "",
    updater ? `更新人 ${updater}` : "",
    updatedAt ? `更新时间 ${fmtDateTime(updatedAt)}` : "",
    approver ? `审批人 ${approver}` : "",
    approvedAt ? `审批时间 ${fmtDateTime(approvedAt)}` : "",
  ].filter(Boolean);
  if (parts.length === 0) return null;
  return (
    <div style={{ marginTop: 12, fontSize: 12, color: "#8c8c8c" }}>
      {parts.join(" · ")}
    </div>
  );
}
