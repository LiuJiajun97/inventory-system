// 主布局(SPEC-WEB V2 1.2 / RBAC 批 1b 动态菜单)
// 左侧白底 Sider 208 + 顶栏白底 + 内容区浅灰底
// 侧栏菜单由 GET /auth/menus 动态渲染:目录为一级分组(可展开),菜单为二级;
// 图标按 menuCode 静态映射,未知编码回退默认图标;菜单加载中显示 Spin,不闪现硬编码
// 面包屑按动态菜单树自动推导(所属目录 + 当前页)

import { useEffect, useMemo, useRef, useState } from "react";
import { Layout, Menu, Breadcrumb, Button, Tooltip, Spin } from "antd";
import {
  DashboardOutlined,
  ImportOutlined,
  ExportOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  AppstoreOutlined,
  HomeOutlined,
  ShopOutlined,
  TeamOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  ShoppingCartOutlined,
  DollarOutlined,
  SwapOutlined,
  AuditOutlined,
  ToolOutlined,
  AlertOutlined,
  MonitorOutlined,
  SettingOutlined,
  BarChartOutlined,
} from "@ant-design/icons";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { clearAuth, getUser } from "../auth/useAuth";
import { useMenus } from "../auth/MenuContext";
import { BrandLogo } from "../components/BrandLogo";
import { UserAvatar } from "../components/UserAvatar";
import { RoleTag } from "../components/StatusTag";
import type { MenuNode } from "../types";

const { Sider, Header, Content } = Layout;

// 一级目录图标映射(按 menuCode;未知目录回退 SettingOutlined,不崩)
const DIR_ICON: Record<string, React.ReactNode> = {
  "dashboard-dir": <DashboardOutlined />,
  "purchase-dir": <ShoppingCartOutlined />,
  "sales-dir": <DollarOutlined />,
  "stock-dir": <DatabaseOutlined />,
  "base-dir": <AppstoreOutlined />,
  "system-dir": <SettingOutlined />,
  "reports-dir": <BarChartOutlined />,
};

// 二级菜单图标映射(按 menuCode;未知菜单回退 SettingOutlined,不崩)
const MENU_ICON: Record<string, React.ReactNode> = {
  dashboard: <DashboardOutlined />,
  alerts: <AlertOutlined />,
  reports: <BarChartOutlined />,
  "purchase-orders": <ShoppingCartOutlined />,
  "sales-orders": <DollarOutlined />,
  inbound: <ImportOutlined />,
  outbound: <ExportOutlined />,
  transfers: <SwapOutlined />,
  stocktakes: <AuditOutlined />,
  "stock-adjusts": <ToolOutlined />,
  stock: <DatabaseOutlined />,
  transactions: <FileSearchOutlined />,
  items: <AppstoreOutlined />,
  warehouses: <HomeOutlined />,
  locations: <ShopOutlined />,
  suppliers: <ShopOutlined />,
  customers: <TeamOutlined />,
  users: <TeamOutlined />,
  roles: <TeamOutlined />,
  dicts: <AuditOutlined />,
  monitor: <MonitorOutlined />,
  "operation-logs": <FileSearchOutlined />,
};

const FALLBACK_ICON = <SettingOutlined />;

// 展开状态持久化 key(按用户区分,不同角色目录不同,恢复时还会按当前 nodes 过滤)
const menuOpenStorageKey = (username?: string) => `menu-open-${username}`;

/** 读取上次手动展开状态;无记录返回 null(区别于"记录为空数组=全部收起")。 */
function loadSavedOpenKeys(username?: string): string[] | null {
  if (!username) return null;
  try {
    const raw = sessionStorage.getItem(menuOpenStorageKey(username));
    if (!raw) return null;
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter((k) => typeof k === "string") : null;
  } catch {
    return null; // 脏数据兜底,走默认全展开
  }
}

/** 用户手动变更展开状态后写入 sessionStorage(下次刷新恢复用)。 */
function saveOpenKeys(username: string | undefined, keys: string[]): void {
  if (!username) return;
  try {
    sessionStorage.setItem(menuOpenStorageKey(username), JSON.stringify(keys));
  } catch {
    // 存储满/禁用时静默失败,不影响交互
  }
}

export function MainLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const user = getUser();
  const role = user?.role;
  const [collapsed, setCollapsed] = useState(false);
  const { nodes, paths, loading } = useMenus();

  // 展开状态:初始只做一次,之后完全由用户控制,任何情况下不强制重置
  // (修复:旧版用 openKeys.length === 0 判断"默认展开",用户把所有一级目录
  // 全部收起后 openKeys 变空,effect 会把目录强制重新全部展开)
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const didInitOpenRef = useRef(false);
  useEffect(() => {
    if (nodes.length === 0 || didInitOpenRef.current) return;
    didInitOpenRef.current = true;
    const codes = nodes.map((n) => n.menuCode);
    const saved = loadSavedOpenKeys(user?.username);
    if (saved) {
      // 恢复上次状态:按当前目录编码过滤,防角色/菜单变更后残留脏数据
      setOpenKeys(saved.filter((k) => codes.includes(k)));
    } else {
      // 无记录:默认展开全部一级目录
      setOpenKeys(codes);
    }
  }, [nodes, user?.username]);

  // 用户手动展开/收起:更新状态并持久化到 sessionStorage(按用户 key)
  const onOpenChange = (keys: string[]) => {
    setOpenKeys(keys);
    saveOpenKeys(user?.username, keys);
  };

  // 动态菜单:仅渲染有 path 的叶子(排除 button 型节点),空目录不展示
  const menuItems = useMemo(
    () =>
      nodes
        .map((dir) => ({
          key: dir.menuCode,
          icon: DIR_ICON[dir.menuCode] ?? FALLBACK_ICON,
          label: dir.menuName,
          children: (dir.children ?? [])
            .filter((c) => c.path)
            .map((c) => ({
              key: c.menuCode,
              icon: MENU_ICON[c.menuCode] ?? FALLBACK_ICON,
              label: <Link to={c.path as string}>{c.menuName}</Link>,
            })),
        }))
        .filter((dir) => dir.children.length > 0),
    [nodes],
  );

  // 选中态:按 pathname 在菜单 path 集合中精确/最长前缀匹配
  const selectedKey = useMemo(() => {
    const matched = Array.from(paths).sort((a, b) => b.length - a.length).find(
      (p) => location.pathname === p || location.pathname.startsWith(p + "/"),
    );
    return nodes
      .flatMap((d) => d.children ?? [])
      .find((c) => c.path === matched)?.menuCode ?? "";
  }, [location.pathname, paths, nodes]);

  // 面包屑:所属一级目录(无 path,纯文本)+ 当前叶子页名
  const crumbs = useMemo(() => {
    const matched = Array.from(paths).sort((a, b) => b.length - a.length).find(
      (p) => location.pathname === p || location.pathname.startsWith(p + "/"),
    );
    const leaf = nodes
      .flatMap((d) => (d.children ?? []).map((c) => ({ dir: d, c })))
      .find(({ c }) => c.path === matched);
    if (!leaf) {
      return [{ label: "首页" }];
    }
    return [
      { label: leaf.dir.menuName },
      { label: leaf.c.menuName },
    ];
  }, [location.pathname, paths, nodes]);

  const onLogout = () => {
    // clearAuth 内部派发 auth-changed,菜单缓存随之清空
    clearAuth();
    navigate("/login", { replace: true });
  };

  return (
    <Layout className="app-layout">
      <Sider
        width={208}
        collapsible
        collapsed={collapsed}
        trigger={null}
        className="app-sider"
      >
        <BrandLogo collapsed={collapsed} />
        {loading ? (
          <div style={{ display: "flex", justifyContent: "center", padding: 40 }}>
            <Spin />
          </div>
        ) : (
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            openKeys={openKeys}
            onOpenChange={onOpenChange}
            style={{ borderRight: 0, padding: "8px 0" }}
            items={menuItems}
          />
        )}
      </Sider>
      <Layout>
        <Header className="app-header">
          <div className="app-header-left">
            <Tooltip title={collapsed ? "展开侧栏" : "收起侧栏"}>
              <span
                className="sider-collapse-btn"
                onClick={() => setCollapsed((c) => !c)}
              >
                {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              </span>
            </Tooltip>
            <Breadcrumb
              items={crumbs.map((c) => ({ title: c.label }))}
            />
          </div>
          <div className="app-header-right">
            {role && <RoleTag role={role} />}
            <span className="user-meta">
              <UserAvatar name={user?.name ?? user?.username} />
              {user?.name ?? user?.username}
            </span>
            <Tooltip title="退出登录">
              <Button
                type="text"
                icon={<LogoutOutlined />}
                onClick={onLogout}
              />
            </Tooltip>
          </div>
        </Header>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
