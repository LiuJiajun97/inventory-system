// 导出下载工具:fetch 带 token 拿 blob → URL.createObjectURL 触发浏览器下载
// (window.open 直连不可行:鉴权走 Authorization 头,浏览器跳转无法携带)
import { friendlyApiError, LOGIN_PATH } from "../api/http";

/**
 * 下载二进制文件(如 xlsx 导出/模板)。
 *
 * @param url 后端下载端点(相对 /api/v1,如 "/items/export?keyword=x")
 * @param filename 本地保存文件名(含扩展名)
 * @throws Error 非 2xx 时抛出(中文友好文案)
 */
export async function downloadBlob(url: string, filename: string): Promise<void> {
  const token = localStorage.getItem("token");
  const resp = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (resp.status === 401) {
    // 与 http 拦截器同口径:仅当确实存过 token 才清本地;非登录页跳登录
    if (localStorage.getItem("token")) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
    }
    if (location.pathname !== LOGIN_PATH) {
      location.href = LOGIN_PATH;
    }
    throw new Error("未登录,请重新登录");
  }
  if (!resp.ok) {
    let body: { message?: string; error?: string } | null = null;
    try {
      body = (await resp.json()) as { message?: string; error?: string };
    } catch {
      // 非 JSON 响应体,用状态码兜底文案
    }
    throw new Error(friendlyApiError(resp.status, body, ""));
  }
  const blob = await resp.blob();
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}
