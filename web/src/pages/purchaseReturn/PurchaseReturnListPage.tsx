// 采购退货单列表(V11)
// ProTable 规范:无标题行、筛选 span6 四列/行、新建按钮 search.optionRender 右侧
// 列 = 单号/日期/原采购单号/仓库/总金额/状态/备注;行内"查看"详情弹窗(960 宽)

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Segmented, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { purchaseReturnApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import { PrintDocModal, printHeader, usePrintNameMaps, type PrintDocData } from "../../components/PrintDocModal";
import type { PurchaseReturn, DocLine } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { fmtDate, fmtDateTime, fmtMoney, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { StatusTag } from "../../components/StatusTag";

// ProTable dateRange transform 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

export function PurchaseReturnListPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<PurchaseReturn | null>(null);
  // V17 主表/明细视图切换(组件内状态,默认主表)
  const [viewMode, setViewMode] = useState<"main" | "line">("main");
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
    const res = await purchaseReturnApi.list({
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

  // V17 明细行视图:请求 /lines(共享筛选 + 物品关键字)
  const lineRequest = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    status?: string;
    warehouseId?: number;
    from?: string;
    to?: string;
    itemKeyword?: string;
  }) => {
    const res = await purchaseReturnApi.lines({
      docNo: params.docNo,
      status: params.status,
      warehouseId: params.warehouseId,
      from: params.from,
      to: params.to,
      itemKeyword: params.itemKeyword,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  // V17 明细行点击单据号:拉取整单详情复用现有详情弹窗
  const openLineDetail = async (r: DocLine) => {
    try {
      setDetail(await purchaseReturnApi.get(r.docId));
    } catch {
      // 拦截器已提示
    }
  };

  // V17 明细视图列(共享筛选字段 + 拍平行字段;物品关键字仅明细视图渲染)
  const lineColumns: ProColumns<DocLine>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "单号", allowClear: true },
      render: (_v, r) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => openLineDetail(r)}>
          {r.docNo}
        </a>
      ),
    },
    { title: "日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate ?? undefined) },
    {
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      width: 140,
      ellipsis: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
      render: (_v, r) => r.warehouseName ?? "-",
    },
    { title: "行号", dataIndex: "lineNo", width: 60, align: "center", search: false, render: (_v, r) => (r.lineNo == null ? "-" : r.lineNo) },
    { title: "物品", width: 190, search: false, ellipsis: true, render: (_v, r) => `${r.itemCode} ${r.itemName}` },
    { title: "规格", dataIndex: "spec", width: 90, search: false, ellipsis: true },
    { title: "单位", dataIndex: "unit", width: 60, search: false },
    { title: "数量", dataIndex: "quantity", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtQty(r.quantity) },
    { title: "单价", dataIndex: "unitPrice", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.unitPrice) },
    { title: "税率(%)", dataIndex: "taxRate", width: 80, align: "right", className: "num-cell", search: false },
    { title: "金额", dataIndex: "amount", width: 100, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.amount) },
    { title: "税额", dataIndex: "taxAmount", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => fmtMoney(r.taxAmount) },
    { title: "价税合计", dataIndex: "taxInclusiveTotal", width: 110, align: "right", className: "num-cell", search: false, render: (_v, r) => <b>{fmtMoney(r.taxInclusiveTotal)}</b> },
    { title: "状态", dataIndex: "status", width: 90, search: false, render: (_v, r) => (r.status === "finished" ? <StatusTag status="inbound" label="已完成" /> : <Tag bordered>{r.status}</Tag>) },
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
    { title: "物品", dataIndex: "itemKeyword", hideInTable: true, fieldProps: { placeholder: "编码或名称关键字" } },
  ];

  // V14 打印:采购退货单详情 VO 转 A4 打印版数据(带原采购单号,空值自动过滤)
  const buildPrintData = (d: PurchaseReturn): PrintDocData => ({
    title: "采购退货单",
    docNo: d.docNo,
    header: printHeader([
      ["退货日期", fmtDate(d.docDate)],
      ["原采购单号", d.purchaseOrderNo],
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
      fmtQty(l.quantity),
      fmtMoney(l.unitPrice),
      Number(l.taxRate).toFixed(2),
      fmtMoney(l.amount),
      fmtMoney(l.taxAmount),
      fmtMoney(l.taxInclusiveTotal),
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
      fmtMoney(d.totalAmount),
    ],
    status: d.status === "finished" ? "已完成" : d.status,
    remark: d.remark,
  });

  const columns: ProColumns<PurchaseReturn>[] = [
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
      title: "原采购单号",
      dataIndex: "purchaseOrderNo",
      width: 180,
      search: false,
      render: (_v, r) => (
        <span style={{ fontFamily: "monospace", fontSize: 13 }}>{r.purchaseOrderNo ?? "-"}</span>
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
      render: (_v, r) => fmtMoney(r.totalAmount),
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
      <ProTable<PurchaseReturn>
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无采购退货单" /> }}
        actionRef={actionRef}
        columns={viewMode === "line" ? (lineColumns as unknown as ProColumns<PurchaseReturn>[]) : columns}
        request={viewMode === "line" ? (lineRequest as unknown as typeof request) : request}
        headerTitle={
          <Segmented
            options={[
              { label: "主表", value: "main" },
              { label: "明细", value: "line" },
            ]}
            value={viewMode}
            onChange={(v) => {
              setViewMode(v as "main" | "line");
              actionRef.current?.reload();
            }}
          />
        }
        options={false}
        scroll={{ x: 1250 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            hasPerm("purchase-return:create") && (
              <Link key="new" to="/purchase-returns/new">
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
        title={`采购退货单详情 - ${detail?.docNo ?? ""}`}
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
              <Descriptions.Item label="原采购单号">
                <span style={{ fontFamily: "monospace" }}>
                  {detail.purchaseOrderNo ?? "-"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="仓库">
                {detail.warehouse?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="总金额">
                {detail.totalAmount == null
                  ? "-"
                  : fmtMoney(detail.totalAmount)}
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
                  render: (v: string) => fmtQty(v),
                },
                {
                  title: "单价",
                  dataIndex: "unitPrice",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => fmtMoney(v),
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
                  render: (v: string) => fmtMoney(v),
                },
                {
                  title: "税额",
                  dataIndex: "taxAmount",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => fmtMoney(v),
                },
                {
                  title: "价税合计",
                  dataIndex: "taxInclusiveTotal",
                  width: 110,
                  align: "right",
                  className: "num-cell",
                  render: (v: string) => fmtMoney(v),
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
