// 顶部品牌区 logo 方块 + 系统名

export function BrandLogo({
  collapsed = false,
  size = "default",
}: {
  collapsed?: boolean;
  size?: "default" | "lg";
}) {
  const cls = size === "lg" ? "brand-logo brand-logo-lg" : "brand-logo";
  return (
    <div
      className={
        "app-sider-brand " + (collapsed ? "app-sider-brand-collapsed" : "")
      }
    >
      <span className={cls}>库</span>
      {!collapsed && <span className="app-sider-brand-text">库存管理系统</span>}
    </div>
  );
}