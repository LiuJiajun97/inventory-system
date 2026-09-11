// 独立新建物品页面(兼容旧路由 /items/new)
// 重定向到列表页(由 Drawer 接管新建)

import { Navigate } from "react-router-dom";

export function ItemFormPage() {
  return <Navigate to="/items" replace />;
}