// 按钮级权限 hook(RBAC 批 1b)
// hasPerm(code) 判断当前用户是否持有权限码,数据来自 /auth/menus 各节点 permissions
// 前端隐藏仅为体验,真正的 403 兜底由后端 @RequirePerm 拦截
import { useMenus } from "./MenuContext";

export function usePermission() {
  const { perms } = useMenus();
  return {
    hasPerm: (code: string) => perms.has(code),
  };
}
