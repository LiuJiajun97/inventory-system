// 路由守卫:未登录跳 /login;角色不够跳 /403
import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { getToken, getUser } from "./useAuth";
import type { Role } from "../types";

export function RequireAuth({ children, roles }: { children: ReactNode; roles?: Role[] }) {
  const token = getToken();
  if (!token) return <Navigate to="/login" replace />;
  if (roles && roles.length > 0) {
    const user = getUser();
    if (!user || !roles.includes(user.role)) {
      return <Navigate to="/403" replace />;
    }
  }
  return <>{children}</>;
}
