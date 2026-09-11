// 主布局(SPEC-WEB V2 1.2)
// 左侧白底 Sider 208 + 顶栏白底 + 内容区浅灰底
// 面包屑按路由自动生成;用户区:头像首字+姓名+角色 Tag+退出

import { Layout, Menu, Breadcrumb, Button, Tooltip } from "antd";
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
} from "@ant-design/icons";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useMemo, useState } from "react";
import { clearAuth, getUser } from "../auth/useAuth";
import type { Role } from "../types";
import { BrandLogo } from "../components/BrandLogo";
import { UserAvatar } from "../components/UserAvatar";
import { RoleTag } from "../components/StatusTag";

const { Sider, Header, Content } = Layout;

interface MenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  path: string;
  roles: Role[];
  parent?: string; // 面包屑父级
  breadcrumbLabel?: string;
}

const ALL_ITEMS: MenuItem[] = [
  {
    key: "dashboard",
    icon: <DashboardOutlined />,
    label: "总览",
    path: "/dashboard",
    breadcrumbLabel: "总览",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "inbound",
    icon: <ImportOutlined />,
    label: "入库单",
    path: "/inbound",
    breadcrumbLabel: "入库单",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "inbound-new",
    icon: <ImportOutlined />,
    label: "新建入库单",
    path: "/inbound/new",
    breadcrumbLabel: "新建入库单",
    roles: ["admin", "operator"],
    parent: "inbound",
  },
  {
    key: "outbound",
    icon: <ExportOutlined />,
    label: "出库单",
    path: "/outbound",
    breadcrumbLabel: "出库单",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "outbound-new",
    icon: <ExportOutlined />,
    label: "新建出库单",
    path: "/outbound/new",
    breadcrumbLabel: "新建出库单",
    roles: ["admin", "operator"],
    parent: "outbound",
  },
  {
    key: "stock",
    icon: <DatabaseOutlined />,
    label: "库存查询",
    path: "/stock",
    breadcrumbLabel: "库存查询",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "transactions",
    icon: <FileSearchOutlined />,
    label: "流水查询",
    path: "/transactions",
    breadcrumbLabel: "流水查询",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "items",
    icon: <AppstoreOutlined />,
    label: "物品",
    path: "/items",
    breadcrumbLabel: "物品",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "suppliers",
    icon: <ShopOutlined />,
    label: "供应商",
    path: "/suppliers",
    breadcrumbLabel: "供应商",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "customers",
    icon: <TeamOutlined />,
    label: "客户",
    path: "/customers",
    breadcrumbLabel: "客户",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "purchase-orders",
    icon: <ShoppingCartOutlined />,
    label: "采购订单",
    path: "/purchase-orders",
    breadcrumbLabel: "采购订单",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "purchase-orders-new",
    icon: <ShoppingCartOutlined />,
    label: "新建采购订单",
    path: "/purchase-orders/new",
    breadcrumbLabel: "新建采购订单",
    roles: ["admin", "operator"],
    parent: "purchase-orders",
  },
  {
    key: "sales-orders",
    icon: <DollarOutlined />,
    label: "销售订单",
    path: "/sales-orders",
    breadcrumbLabel: "销售订单",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "sales-orders-new",
    icon: <DollarOutlined />,
    label: "新建销售订单",
    path: "/sales-orders/new",
    breadcrumbLabel: "新建销售订单",
    roles: ["admin", "operator"],
    parent: "sales-orders",
  },
  {
    key: "transfers",
    icon: <SwapOutlined />,
    label: "调拨单",
    path: "/transfers",
    breadcrumbLabel: "调拨单",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "stocktakes",
    icon: <AuditOutlined />,
    label: "盘点单",
    path: "/stocktakes",
    breadcrumbLabel: "盘点单",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "stock-adjusts",
    icon: <ToolOutlined />,
    label: "库存调整",
    path: "/stock-adjusts",
    breadcrumbLabel: "库存调整",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "alerts",
    icon: <AlertOutlined />,
    label: "预警中心",
    path: "/alerts",
    breadcrumbLabel: "预警中心",
    roles: ["admin", "operator", "viewer"],
  },
  {
    key: "items-new",
    icon: <AppstoreOutlined />,
    label: "新建物品",
    path: "/items/new",
    breadcrumbLabel: "新建物品",
    roles: ["admin"],
    parent: "items",
  },
  {
    key: "warehouses",
    icon: <HomeOutlined />,
    label: "仓库",
    path: "/warehouses",
    breadcrumbLabel: "仓库",
    roles: ["admin"],
  },
  {
    key: "locations",
    icon: <ShopOutlined />,
    label: "库位",
    path: "/locations",
    breadcrumbLabel: "库位",
    roles: ["admin"],
  },
  {
    key: "users",
    icon: <TeamOutlined />,
    label: "用户",
    path: "/users",
    breadcrumbLabel: "用户",
    roles: ["admin"],
  },
  {
    key: "dicts",
    icon: <AuditOutlined />,
    label: "字典",
    path: "/dicts",
    breadcrumbLabel: "字典",
    roles: ["admin"],
  },
];

function buildBreadcrumb(pathname: string): Array<{ label: string; to?: string }> {
  const items: Array<{ label: string; to?: string }> = [];
  // 找到精确匹配或最长前缀匹配
  const exact = ALL_ITEMS.find((it) => it.path === pathname);
  let main = exact;
  if (!main) {
    main = ALL_ITEMS.find(
      (it) => pathname.startsWith(it.path + "/") || pathname === it.path,
    );
  }
  if (!main) {
    items.push({ label: "首页" });
    return items;
  }
  if (main.parent) {
    const parent = ALL_ITEMS.find((it) => it.key === main!.parent);
    if (parent) {
      items.push({ label: parent.breadcrumbLabel ?? parent.label, to: parent.path });
    }
  }
  items.push({ label: main.breadcrumbLabel ?? main.label });
  return items;
}

export function MainLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const user = getUser();
  const role: Role | undefined = user?.role;
  const [collapsed, setCollapsed] = useState(false);

  const visibleItems = useMemo(
    () => ALL_ITEMS.filter((it) => role && it.roles.includes(role) && !it.parent),
    [role],
  );

  const selectedKey =
    ALL_ITEMS.find(
      (it) =>
        (location.pathname === it.path ||
          (it.path !== "/" && location.pathname.startsWith(it.path))) &&
        !it.parent,
    )?.key ?? "dashboard";

  const onLogout = () => {
    clearAuth();
    navigate("/login", { replace: true });
  };

  const crumbs = buildBreadcrumb(location.pathname);

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider
        width={208}
        collapsible
        collapsed={collapsed}
        trigger={null}
        className="app-sider"
      >
        <BrandLogo collapsed={collapsed} />
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          style={{ borderRight: 0, padding: "8px 0" }}
          items={visibleItems.map((it) => ({
            key: it.key,
            icon: it.icon,
            label: <Link to={it.path}>{it.label}</Link>,
          }))}
        />
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
              items={crumbs.map((c) => ({
                title: c.to ? <Link to={c.to}>{c.label}</Link> : c.label,
              }))}
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