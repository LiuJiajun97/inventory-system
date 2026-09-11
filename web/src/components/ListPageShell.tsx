// 列表页统一骨架:操作工具条(可选)+ 筛选区(可选)+ 数据区 + 分页
// 10+ 页共用,保证风格一致
// 注:页面标题横幅已移除(用户要求:顶栏/侧栏已体现当前页,横幅一行纯占空间),
// 页面级操作按钮(如新建)统一放标题行右侧

import type { ReactNode } from "react";
import { Table, type TableProps } from "antd";

export interface ListPageShellProps {
  extra?: ReactNode; // 页面级操作按钮(如新建/筛选仓库),渲染在独立工具条行
  filter?: ReactNode;
  tableProps: TableProps<any>;
  children?: ReactNode; // 比如额外 Card / Drawer
}

export function ListPageShell({
  extra,
  filter,
  tableProps,
  children,
}: ListPageShellProps) {
  return (
    <>
      {extra && (
        <div className="page-toolbar">
          <div className="page-toolbar-extra">{extra}</div>
        </div>
      )}
      {filter && <div className="filter-card">{filter}</div>}
      <div className="table-card">
        <Table {...tableProps} />
      </div>
      {children}
    </>
  );
}
