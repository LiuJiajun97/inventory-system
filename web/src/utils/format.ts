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
