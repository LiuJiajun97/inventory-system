// 销售退货单列表(V11)
// ProTable 规范:无标题行、筛选 span6 四列/行、新建按钮 search.optionRender 右侧
// 列 = 单号/日期/原销售单号/仓库/总金额/状态/备注;行内"查看"详情弹窗(960 宽)

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { salesReturnApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import { PrintDocModal, printHeader, usePrintNameMaps, type PrintDocData } from "../../components/PrintDocModal";
import type { SalesReturn } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { fmtDate, fmtDateTime } from "../../utils/format";
import { StatusTag } from "../../components/StatusTag";

// ProTable dateRange transform 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

export function SalesReturnListPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<SalesReturn | null>(null);
  const [printOpen, setPrintOpen] = useState(false);
  const { itemText, locText } = usePrintNameMaps(); // V14 打印:ID→可读文本映射
  const actionRef = useRef<ActionType>();
  const { hasPerm } = usePermission();
  const location = useLocation();

  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 从新建页带 ?refresh= 跳回时自动重载列表
  useEffect(() => {
    if (location.search.includes("refresh")) actionRef.current?.reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    status?: string;
    warehouseId?: number;
    from?: string;
    to?: string;
  }) => {
    const res = await salesReturnApi.list({
      docNo: params.docNo,
      status: params.status,
      warehouseId: params.warehouseId,
      from: params.from,
      to: params.to,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  // V14 打印:销售退货单详情 VO 转 A4 打印版数据(带原销售单号,空值自动过滤)
  const buildPrintData = (d: SalesReturn): PrintDocData => ({
    title: "销售退货单",
    docNo: d.docNo,
    header: printHeader([
      ["退货日期", fmtDate(d.docDate)],
      ["原销售单号", d.salesOrderNo],
      ["仓库", d.warehouse?.warehouseName],
      ["创建人", d.creator],
    ]),
    columns: [
      { title: "行号", align: "center" },
      { title: "物品" },
      { title: "规格" },
      { title: "数量", align: "right" },
      { title: "单价", align: "right" },
      { title: "税率(%)", align: "right" },
      { title: "金额", align: "right" },
      { title: "税额", align: "right" },
      { title: "价税合计", align: "right" },
      ],
      rows: (d.items ?? []).map((l) => [
      String(l.lineNo),
      itemText(l.itemId),
      l.specSnapshot ?? "",
      Number(l.quantity).toFixed(4),
      Number(l.unitPrice).toFixed(4),
      Number(l.taxRate).toFixed(2),
      Number(l.amount).toFixed(2),
      Number(l.taxAmount).toFixed(2),
      Number(l.taxInclusiveTotal).toFixed(2),
    ]),
    totals: [
      "",
      "",
      "",
      "",
      "",
      "合计",
      "",
      "",
      d.totalAmount == null ? "" : Number(d.totalAmount).toFixed(2),
    ],
    status: d.status === "finished" ? "已完成" : d.status,
    remark: d.remark,
  });

  const columns: ProColumns<SalesReturn>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 220,
      fieldProps: { placeholder: "退货单号", allowClear: true },
      render: (_v, r) => (
        <span style={{ fontFamily: "monospace", fontSize: 13 }}>{r.docNo}</span>
      ),
    },
    {
      title: "日期",
      dataIndex: "range",
      valueType: "dateRange",
      hideInTable: true,
      search: {
        transform: (value: [unknown, unknown]) => ({
          from: toDay(value[0]),
          to: toDay(value[1]),
        }),
      },
    },
    {
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        options: [{ label: "已完成", value: "finished" }],
      },
    },
    {
      title: "日期",
      dataIndex: "docDate",
      width: 110,
      search: false,
      render: (_v, r) => fmtDate(r.docDate),
    },
    {
      title: "原销售单号",
      dataIndex: "salesOrderNo",
      width: 180,
      search: false,
      render: (_v, r) => (
        <span style={{ fontFamily: "monospace", fontSize: 13 }}>{r.salesOrderNo ?? "-"}</span>
      ),
    },
    {
      title: "仓库",
      dataIndex: ["warehouse", "warehouseName"],
      width: 140,
      ellipsis: true,
      search: false,
    },
    {
      title: "总金额",
      dataIndex: "totalAmount",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => (r.totalAmount == null ? "-" : Number(r.totalAmount).toFixed(2)),
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      search: false,
      render: (_v, r) =>
        r.status === "finished" ? (
          <StatusTag status="inbound" label="已完成" />
        ) : (
          <Tag bordered>{r.status}</Tag>
        ),
    },
    {
      title: "备注",
      dataIndex: "remark",
      width: 160,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.remark ?? "-",
    },
    {
      title: "操作",
      width: 80,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => <a onClick={() => setDetail(row)}>查看</a>,
    },
  ];

  return (
    <>
      <ProTable<SalesReturn>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={request}
        headerTitle={false}
        options={false}
        scroll={{ x: 1250 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            hasPerm("sales-return:create") && (
              <Link key="new" to="/sales-returns/new">
                <Button type="primary">新建</Button>
              </Link>
            ),
          ],
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title={`销售退货单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={<Button onClick={() => setPrintOpen(true)}>打印</Button>}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="单号">
                <span style={{ fontFamily: "monospace" }}>{detail.docNo}</span>
              </Descriptions.Item>
              <Descriptions.Item label="日期">
                {fmtDate(detail.docDate)}
              </Descriptions.Item>
              <Descriptions.Item label="原销售单号">
                <span style={{ fontFamily: "monospace" }}>
                  {detail.salesOrderNo ?? "-"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="仓库">
                {detail.warehouse?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="总金额">
                {detail.totalAmount == null
                  ? "-"
                  : Number(detail.totalAmount).toFixed(2)}
              </Descriptions.Item>
              <Descriptions.Item label="创建人">
                {detail.creator ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {fmtDateTime(detail.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label="状态">已完成</Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? "-"}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>退货明细</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                { title: "行号", dataIndex: "lineNo", width: 60 },
                { title: "物品ID", dataIndex: "itemId", width: 80 },
                {
                  title: "规格",
                  dataIndex: "specSnapshot",
                  width: 140,
                  ellipsis: true,
                  render: (v?: string | null) => v ?? "-",
                },
                {
                  title: "数量",
                  dataIndex: "quantity",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => Number(v).toFixed(4),
                },
                {
                  title: "单价",
                  dataIndex: "unitPrice",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => Number(v).toFixed(4),
                },
                {
                  title: "税率(%)",
                  dataIndex: "taxRate",
                  width: 90,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => Number(v).toFixed(2),
                },
                {
                  title: "金额",
                  dataIndex: "amount",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => Number(v).toFixed(2),
                },
                {
                  title: "税额",
                  dataIndex: "taxAmount",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => Number(v).toFixed(2),
                },
                {
                  title: "价税合计",
                  dataIndex: "taxInclusiveTotal",
                  width: 110,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => Number(v).toFixed(2),
                },
              ]}
            />
          </>
        )}
      </Modal>

      {/* V14 打印 Modal:详情数据已在 detail state,直接转换渲染 */}
      <PrintDocModal
        open={printOpen}
        onClose={() => setPrintOpen(false)}
        data={detail ? buildPrintData(detail) : null}
      />
    </>
  );
}
