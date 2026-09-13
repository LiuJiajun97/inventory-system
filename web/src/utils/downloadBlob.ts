// 导出下载工具:fetch 带 token 拿 blob → URL.createObjectURL 触发浏览器下载
// (window.open 直连不可行:鉴权走 Authorization 头,浏览器跳转无法携带)

/**
 * 下载二进制文件(如 xlsx 导出/模板)。
 *
 * @param url 后端下载端点(相对 /api/v1,如 "/items/export?keyword=x")
 * @param filename 本地保存文件名(含扩展名)
 * @throws Error 非 2xx 时抛出(尽量带后端错误消息)
 */
export async function downloadBlob(url: string, filename: string): Promise<void> {
  const token = localStorage.getItem("token");
  const resp = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!resp.ok) {
    let msg = `下载失败(HTTP ${resp.status})`;
    try {
      const data = (await resp.json()) as { error?: string; message?: string };
      msg = data?.error ?? data?.message ?? msg;
    } catch {
      // 非 JSON 响应体,用默认消息
    }
    throw new Error(msg);
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
