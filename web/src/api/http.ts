// axios 实例:带 token、401 跳登录、统一错误提示
import axios from "axios";
import { apiMessageError } from "./messageBridge";

/** 应用基座路径:生产挂在 80 端口 /inventory 子路径下(同 IP 多项目按路径共享 80)。 */
export const BASE = "/inventory";

/** 登录页完整路径(401 跳转与登录态判断共用)。 */
export const LOGIN_PATH = `${BASE}/login`;

// 请求级自定义配置:silent = true 时静默错误提示(供轮询等高频调用使用,
// 由调用方自行提示;401 跳登录逻辑不受影响)
declare module "axios" {
  interface AxiosRequestConfig {
    silent?: boolean;
  }
}

/** 后端错误体(统一契约 {statusCode, code?, error, message?})。 */
export type ApiErrorBody = {
  statusCode?: number;
  code?: string;
  error?: string;
  message?: string;
};

/** HTTP 状态码的中文兜底文案(message/error 均缺失时用)。 */
const STATUS_TEXT: Record<number, string> = {
  400: "请求参数有误",
  403: "无权限执行此操作",
  404: "请求的资源不存在",
  405: "请求方式不被支持",
  429: "请求过于频繁,请稍后重试",
};

/**
 * 把 API 错误转成中文友好文案(统一出口,http 拦截器与 downloadBlob 共用)。
 * 优先后端 message(中文业务详情),次选 error,再按状态码兜底,
 * 最后处理超时/断网等网络层错误。
 *
 * @param status HTTP 状态码(无响应时为 null)
 * @param body   后端错误体(可能不是 JSON,为 null)
 * @param axiosMessage axios 原始错误信息(超时/断网英文,需翻译)
 */
export function friendlyApiError(status: number | null, body: ApiErrorBody | null, axiosMessage: string): string {
  if (body?.message) return body.message;
  if (body?.error && body.error !== "Error") return body.error;
  if (status != null) return STATUS_TEXT[status] ?? (status >= 500 ? "服务器异常,请稍后重试" : "操作失败,请重试");
  // 无 HTTP 响应:超时 / 断网 / 跨域
  if (axiosMessage.toLowerCase().includes("timeout")) return "请求超时,请检查网络后重试";
  return "网络连接失败,请检查网络";
}

/**
 * 生成幂等键 UUID。
 * crypto.randomUUID 仅在安全上下文(https/localhost)可用;
 * 生产走 http://IP 时不存在,须用 getRandomValues 兜底(任意上下文可用)。
 */
function generateIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  // 按 RFC 4122 置版本/变体位
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export const http = axios.create({
  // 生产挂 80 端口 /inventory 子路径(同 IP 多项目按路径共享 80);dev 走 vite 代理
  baseURL: "/inventory/api/v1",
  timeout: 30000,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers = config.headers ?? {};
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  // 写请求(POST/PUT/DELETE)自动附加幂等键,防 at-least-once 重试重复写入;
  // 调用方已显式带 Idempotency-Key 时不覆盖(同 key 重试由后端重放首次响应)
  const method = (config.method ?? "get").toLowerCase();
  if (method === "post" || method === "put" || method === "delete") {
    config.headers = config.headers ?? {};
    if (!config.headers["Idempotency-Key"]) {
      config.headers["Idempotency-Key"] = generateIdempotencyKey();
    }
  }
  return config;
});

http.interceptors.response.use(
  (resp) => resp.data,
  (err) => {
    const status = err?.response?.status ?? null;
    const data = (err?.response?.data ?? null) as ApiErrorBody | null;
    const silent = err?.config?.silent === true;
    const msg = friendlyApiError(status, data, err?.message ?? "");
    if (status === 401) {
      // 401 分三层处理,登录页与非登录页都需给用户反馈:
      // 1) 清本地登录态——仅当确实存过 token 时才清,避免登录页无谓写;2) toast
      // ——所有 401 都弹(silent 除外),登录页弹后端具体原因(如"用户名或密码错误"),
      // 非登录页弹"未登录,请重新登录";3) 跳转——仅非登录页跳 /login。
      if (localStorage.getItem("token")) {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
      }
      if (!silent) {
        apiMessageError(location.pathname === LOGIN_PATH ? msg || "登录失败,请重试" : "未登录,请重新登录");
      }
      if (location.pathname !== LOGIN_PATH) {
        location.href = LOGIN_PATH;
      }
    } else if (!silent && msg) {
      apiMessageError(msg);
    }
    return Promise.reject(err);
  },
);
