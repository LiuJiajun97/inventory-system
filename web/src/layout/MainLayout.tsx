// 主布局(SPEC-WEB V2 1.2 / RBAC 批 1b 动态菜单)
// 顶栏(TASK-v22c C2/C3):左侧 折叠按钮 + 面包屑 + 仓库快捷切换 Select;
// 右侧 当前日期 + 授权仓徽标(非 admin)+ 角色 Tag + 头像下拉(退出登录)
// 左侧白底 Sider 208 + 顶栏白底 + 内容区浅灰底
// 侧栏菜单由 GET /auth/menus 动态渲染:目录为一级分组(可展开),菜单为二级;
// 图标按 menuCode 静态映射,未知编码回退默认图标;菜单加载中显示 Spin,不闪现硬编码
// 面包屑按动态菜单树自动推导(所属目录 + 当前页)

import { useEffect, useMemo, useRef, useState } from "react";
import { Layout, Menu, Breadcrumb, Tooltip, Spin, Tag, Dropdown, Select } from "antd";
import dayjs from "dayjs";
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
  SafetyCertificateOutlined,
  ShoppingCartOutlined,
  DollarOutlined,
  SwapOutlined,
  AuditOutlined,
  ToolOutlined,
  AlertOutlined,
  MonitorOutlined,
  SettingOutlined,
  BarChartOutlined,
  AccountBookOutlined,
  FileDoneOutlined,
} from "@ant-design/icons";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { clearAuth, getUser } from "../auth/useAuth";
import { authApi } from "../api";
import { useMenus } from "../auth/MenuContext";
import { WarehouseScopeProvider, useWarehouseScope } from "../auth/WarehouseScopeContext";
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
  "settlement-dir": <AccountBookOutlined />,
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
  invoices: <FileDoneOutlined />,
  "settlement-ap": <AccountBookOutlined />,
  "settlement-ar": <AccountBookOutlined />,
  payments: <AccountBookOutlined />,
  receipts: <AccountBookOutlined />,
};

const FALLBACK_ICON = <SettingOutlined />;

// 中文星期:dayjs() 本地时区(用户环境为东八区)
const WEEK_CN = ["日", "一", "二", "三", "四", "五", "六"];

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
  return (
    // 仓库范围上下文挂登录态内:顶栏徽标/快捷切换与各列表页共享同一份仓库列表
    <WarehouseScopeProvider>
      <MainLayoutInner />
    </WarehouseScopeProvider>
  );
}

function MainLayoutInner() {
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
    // 先通知后端吊销当前 token 的 jti(立即失效);请求失败也清本地(降级为自然过期),
    // 再清本地登录态并回登录页
    authApi.logout().catch(() => undefined).finally(() => {
      // clearAuth 内部派发 auth-changed,菜单缓存随之清空
      clearAuth();
      navigate("/login", { replace: true });
    });
  };

  // C2:当前日期(YYYY-MM-DD 周X,灰色小字)
  const todayText = `${dayjs().format("YYYY-MM-DD")} 周${WEEK_CN[dayjs().day()]}`;

  // C2:数据范围徽标——非 admin 且已拉到授权仓列表(数据权限过滤后的结果)时显示数量
  const scope = useWarehouseScope();
  const showScopeBadge = role !== "admin" && scope.warehouses.length > 0;

  // C2:头像下拉菜单(退出登录);原独立退出按钮已移除
  const avatarMenu = {
    items: [
      {
        key: "logout",
        icon: <LogoutOutlined />,
        label: "退出登录",
        onClick: onLogout,
      },
    ],
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
            {/* C3 仓库快捷切换:选中后列表页自动按此仓过滤,清空=全部仓库 */}
            <Select
              allowClear
              style={{ width: 180, marginLeft: 16 }}
              placeholder="全部仓库"
              size="small"
              value={scope.scopeWarehouseId ?? undefined}
              onChange={(v) => scope.setScopeWarehouseId(v ?? null)}
              options={scope.warehouses.map((w) => ({
                label: w.warehouseName,
                value: w.id,
              }))}
            />
          </div>
          <div className="app-header-right">
            {/* C2 当前日期(灰色小字) */}
            <span style={{ color: "#8c8c8c", fontSize: 12, marginRight: 12 }}>
              {todayText}
            </span>
            {/* C2 数据范围徽标:非 admin 显示授权仓数量 */}
            {showScopeBadge && (
              <Tag icon={<SafetyCertificateOutlined />} style={{ marginRight: 8 }}>
                授权仓:{scope.warehouses.length} 个
              </Tag>
            )}
            {role && <RoleTag role={role} />}
            <span className="user-meta">
              {/* C2:头像改为下拉菜单入口(退出登录) */}
              <Dropdown menu={avatarMenu} trigger={["click"]}>
                <UserAvatar
                  name={user?.name ?? user?.username}
                  style={{ cursor: "pointer" }}
                />
              </Dropdown>
              {user?.name ?? user?.username}
            </span>
          </div>
        </Header>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
