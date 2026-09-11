// 列表页统一骨架:筛选区 + 数据区 + 分页
// 10 页共用,保证风格一致

import type { ReactNode } from "react";
import { Table, type TableProps } from "antd";
import { PageHeader } from "./PageHeader";

export interface ListPageShellProps {
  title: string;
  extra?: ReactNode;
  filter?: ReactNode;
  tableProps: TableProps<any>;
  children?: ReactNode; // 比如额外 Card / Drawer
}

export function ListPageShell({
  title,
  extra,
  filter,
  tableProps,
  children,
}: ListPageShellProps) {
  return (
    <>
      <PageHeader title={title} extra={extra} />
      {filter && <div className="filter-card">{filter}</div>}
      <div className="table-card">
        <Table {...tableProps} />
      </div>
      {children}
    </>
  );
}