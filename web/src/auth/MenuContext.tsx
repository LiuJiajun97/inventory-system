// 全局菜单/权限上下文(RBAC 批 1b)
// 登录后调 GET /auth/menus 拉取当前用户(多角色并集)的菜单树,
// 维护三份数据供全局消费:
//   nodes - 菜单树(目录为一级分组,菜单为二级),MainLayout 渲染侧栏
//   paths - 全部叶子 path 集合,RequireAuth 路由守卫按此校验
//   perms - 各节点 permissions 权限码扁平化集合,usePermission 按钮显隐
// 登录/退出时由 useAuth 派发 auth-changed 事件触发重新加载(含清缓存)。
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import type { ReactNode } from "react";
import { http } from "../api/http";
import type { MenuNode } from "../types";

/** 登录/退出后触发菜单重新加载的自定义事件名 */
export const AUTH_CHANGED_EVENT = "auth-changed";

interface MenuState {
  /** 菜单树(顶层目录列表) */
  nodes: MenuNode[];
  /** 叶子菜单 path 集合(路由守卫用) */
  paths: Set<string>;
  /** 按钮权限码集合(扁平化自各节点 permissions) */
  perms: Set<string>;
  /** 菜单接口加载中(守卫期间须等待,避免误判 403) */
  loading: boolean;
}

interface MenuContextValue extends MenuState {
  /** 是否拥有指定权限码(按钮显隐用) */
  hasPerm: (code: string) => boolean;
  /** 强制重新拉取菜单(登录/退出由 Provider 内部监听,一般无需手动调) */
  reload: () => void;
}

const EMPTY_STATE: MenuState = {
  nodes: [],
  paths: new Set(),
  perms: new Set(),
  loading: false,
};

// 初始 loading=true:已登录整页刷新时,守卫必须等菜单首次加载完成再判定,
// 否则空 path 集合会误判 403(未登录时 load() 会立即置回空态且守卫先跳 /login)
const INIT_STATE: MenuState = { ...EMPTY_STATE, loading: true };

const MenuContext = createContext<MenuContextValue>({
  ...EMPTY_STATE,
  hasPerm: () => false,
  reload: () => undefined,
});

export function MenuProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<MenuState>(INIT_STATE);

  const load = useCallback(async () => {
    // 未登录(无 token)不请求,保持空态
    if (!localStorage.getItem("token")) {
      setState(EMPTY_STATE);
      return;
    }
    setState((s) => ({ ...s, loading: true }));
    try {
      const data = (await http.get<unknown, MenuNode[]>("auth/menus")) ?? [];
      const paths = new Set<string>();
      const perms = new Set<string>();
      const walk = (list: MenuNode[]) => {
        for (const n of list) {
          if (n.path) paths.add(n.path);
          (n.permissions ?? []).forEach((p) => perms.add(p));
          walk(n.children ?? []);
        }
      };
      walk(data);
      setState({ nodes: data, paths, perms, loading: false });
    } catch {
      // 401 由 axios 拦截器跳登录;其他异常按空菜单兜底,避免侧栏永远转圈
      setState({ nodes: [], paths: new Set(), perms: new Set(), loading: false });
    }
  }, []);

  useEffect(() => {
    void load();
    const onAuthChanged = () => void load();
    window.addEventListener(AUTH_CHANGED_EVENT, onAuthChanged);
    return () => window.removeEventListener(AUTH_CHANGED_EVENT, onAuthChanged);
  }, [load]);

  const value = useMemo<MenuContextValue>(
    () => ({
      ...state,
      hasPerm: (code: string) => state.perms.has(code),
      reload: () => void load(),
    }),
    [state, load],
  );

  return <MenuContext.Provider value={value}>{children}</MenuContext.Provider>;
}

export function useMenus(): MenuContextValue {
  return useContext(MenuContext);
}
