// 时间展示统一工具(一期收尾:东八区 + yyyy-MM-dd HH:mm:ss)
// 后端约定:LocalDateTime 输出 "yyyy-MM-dd HH:mm:ss"(东八区墙钟)、LocalDate 输出 "yyyy-MM-dd"
// 前端展示一律 dayjs format,禁止裸 ISO 串 / toLocaleString 斜杠式

import dayjs from "dayjs";

/**
 * 时间展示:yyyy-MM-dd HH:mm:ss(空值显示 "-")
 */
export const fmtDateTime = (v?: string | null): string =>
  v ? dayjs(v).format("YYYY-MM-DD HH:mm:ss") : "-";

/**
 * 日期展示:yyyy-MM-dd(空值显示 "-")
 */
export const fmtDate = (v?: string | null): string =>
  v ? dayjs(v).format("YYYY-MM-DD") : "-";

// 金额格式化:千分位 + 固定 2 位小数,如 1234567.8 -> "1,234,567.80"
export function fmtMoney(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === "" || Number.isNaN(Number(v))) return "-";
  return Number(v).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// 数量格式化:千分位,整数不带小数,如 12345 -> "12,345";非整数最多 3 位小数
export function fmtQty(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === "" || Number.isNaN(Number(v))) return "-";
  const n = Number(v);
  return Number.isInteger(n) ? n.toLocaleString("zh-CN") : n.toLocaleString("zh-CN", { maximumFractionDigits: 3 });
}

/**
 * 单据关联类型(refType)展示映射,与后端 ErrorCode.REF_TYPE_* 对齐
 * (出入库列表/流水中出现新值时能显示中文)
 */
export const REF_TYPE_LABEL: Record<string, string> = {
  purchase: "采购到货",
  sales: "销售发货",
  purchase_return: "采购退货",
  sales_return: "销售退货",
  opening: "期初",
};
