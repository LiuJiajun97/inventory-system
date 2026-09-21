// 报表中心公共小组件:日期区间转换/数量金额单元格/行展开明细表

import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import type { ReconDocDetail } from "../../types/report";
import { fmtMoney, fmtQty } from "../../utils/format";

// ProTable dateRange 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
export function dayPart(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

// 数量单元格:右对齐 tabular-nums,千分位展示(契约字符串转数字)
export function QtyCell({ value }: { value: string | null | undefined }) {
  if (value == null || value === "") return "-";
  return <span className="num-cell">{fmtQty(value)}</span>;
}

// 金额单元格:右对齐,千分位 + 2 位小数,空快照显示 "-"
export function AmountCell({ value }: { value: string | null | undefined }) {
  if (value == null || value === "") return "-";
  return <span className="num-cell">{fmtMoney(value)}</span>;
}

// 对账行展开:期间内单据明细表(单号/日期/类型/金额)
export function ReconDetailTable({ rows }: { rows: ReconDocDetail[] }) {
  const columns: ColumnsType<ReconDocDetail> = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 200,
      render: (v: string) => <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span>,
    },
    { title: "单据日期", dataIndex: "docDate", width: 130 },
    { title: "单据类型", dataIndex: "docTypeName", width: 120 },
    {
      title: "价税合计",
      dataIndex: "amount",
      align: "right",
      render: (v: string | null) => fmtMoney(v),
    },
  ];
  return (
    <Table<ReconDocDetail>
      rowKey="docNo"
      size="small"
      columns={columns}
      dataSource={rows}
      pagination={false}
    />
  );
}
