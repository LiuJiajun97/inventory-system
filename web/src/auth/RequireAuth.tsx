// 路由守卫(RBAC 批 1b 动态化)
// 未登录跳 /login;菜单加载中等待(转圈);
// 当前路径不在用户菜单 path 集合内(精确或前缀匹配)跳 /403
// /login、/403 自身不走守卫;/ 由 index 重定向到 /dashboard 后再校验
import type { ReactNode } from "react";
import { Spin } from "antd";
import { Navigate, useLocation } from "react-router-dom";
import { getToken } from "./useAuth";
import { useMenus } from "./MenuContext";

export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { loading, paths } = useMenus();
  const token = getToken();

  if (!token) {
    return <Navigate to="/login" replace />;
  }
  // /403 页面自身不再校验(否则重定向死循环);/ 交给 index 重定向
  const pathname = location.pathname;
  if (pathname !== "/403" && pathname !== "/") {
    if (loading) {
      return (
        <div style={{ display: "flex", justifyContent: "center", padding: 80 }}>
          <Spin size="large" />
        </div>
      );
    }
    // 精确匹配或前缀匹配(覆盖 /purchase-orders/new/:id 等带子路径的新建/编辑路由)
    const allowed = Array.from(paths).some(
      (p) => pathname === p || pathname.startsWith(p + "/"),
    );
    if (!allowed) {
      return <Navigate to="/403" replace />;
    }
  }
  return <>{children}</>;
}
