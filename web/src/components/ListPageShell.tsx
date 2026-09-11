// 列表页统一骨架:筛选区(可选,右侧含页面级操作按钮)+ 数据区 + 分页
// 10+ 页共用,保证风格一致
// 注:页面标题横幅已移除(用户要求:顶栏/侧栏已体现当前页,横幅一行纯占空间),
// 页面级操作按钮(如新建)放筛选卡内部右侧,与筛选表单同一行,无独立工具条行

import type { ReactNode } from "react";
import { Table, type TableProps } from "antd";

export interface ListPageShellProps {
  extra?: ReactNode; // 页面级操作按钮(如新建/筛选仓库),渲染在筛选卡内部右侧
  filter?: ReactNode;
  tableProps?: TableProps<any>; // 可选:非单 Table 页面(如 Tabs 预警)可不传,数据区由 children 提供
  children?: ReactNode; // 比如额外 Card / Drawer,或自管数据区(Tabs 等)
}

export function ListPageShell({
  extra,
  filter,
  tableProps,
  children,
}: ListPageShellProps) {
  return (
    <>
      {filter && (
        <div className="filter-card">
          {filter}
          {extra && <div className="filter-card-extra">{extra}</div>}
        </div>
      )}
      {tableProps && (
        <div className="table-card">
          <Table {...tableProps} />
        </div>
      )}
      {children}
    </>
  );
}
