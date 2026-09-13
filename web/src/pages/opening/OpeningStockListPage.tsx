// 期初库存列表(V13)
// ProTable 版:筛选字段由 columns 配置驱动(单号/仓库/状态/日期区间)
// 单号 + 日期 + 仓库 + 物品行数 + 总数量 + 状态 + 备注
// 查看详情 Modal(960 + .doc-detail-modal 紧凑)展示行明细(物品/批次/数量/单价/效期/库位)

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Segmented, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { openingApi, warehouseApi } from "../../api";
import type { OpeningStockDoc, OpeningStockDocItem, Warehouse } from "../../types";
import type { DocLine } from "../../types/phase1";
import { PrintDocModal, printHeader, type PrintDocData } from "../../components/PrintDocModal";
import { usePermission } from "../../auth/usePermission";
import { fmtDate, fmtDateTime } from "../../utils/format";
import { StatusTag } from "../../components/StatusTag";

// ProTable dateRange transform 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

export function OpeningStockListPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<OpeningStockDoc | null>(null);
  // V17 主表/明细视图切换(组件内状态,默认主表)
  const [viewMode, setViewMode] = useState<"main" | "line">("main");
  const [printOpen, setPrintOpen] = useState(false);
  const actionRef = useRef<ActionType>();
  const { hasPerm } = usePermission();
  const location = useLocation();

  // 仓库下拉数据源(异步加载,仅用于筛选项)
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
    const res = await openingApi.list({
      docNo: params.docNo,
      status: params.status,
      warehouseId: params.warehouseId,
      from: params.from,
      to: params.to,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

  // V17 明细行视图:请求 /lines(共享筛选 + 物品关键字/批次号)
  const lineRequest = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    status?: string;
    warehouseId?: number;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
  }) => {
    const res = await openingApi.lines({
      docNo: params.docNo,
      status: params.status,
      warehouseId: params.warehouseId,
      from: params.from,
      to: params.to,
      itemKeyword: params.itemKeyword,
      batchNo: params.batchNo,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  // V17 明细行点击单据号:拉取整单详情复用现有详情弹窗
  const openLineDetail = async (r: DocLine) => {
    try {
      setDetail(await openingApi.get(r.docId));
    } catch {
      // 拦截器已提示
    }
  };

  // V17 明细视图列(共享筛选字段 + 拍平行字段;物品关键字/批次号仅明细视图渲染)
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
    { title: "数量", dataIndex: "quantity", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => (r.quantity == null ? "-" : Number(r.quantity).toFixed(2)) },
    { title: "单价", dataIndex: "unitPrice", width: 90, align: "right", className: "num-cell", search: false, render: (_v, r) => (r.unitPrice == null ? "-" : Number(r.unitPrice).toFixed(2)) },
    { title: "批次号", dataIndex: "batchNo", width: 110, search: false, render: (_v, r) => r.batchNo ?? "-" },
    { title: "状态", dataIndex: "status", width: 90, valueEnum: { finished: { text: "已完成" } }, render: (_v, r) => (r.status === "finished" ? <StatusTag status="inbound" label="已完成" /> : <Tag bordered>{r.status}</Tag>) },
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
    { title: "批次号", dataIndex: "batchNo", hideInTable: true },
  ];

  // V14 打印:期初单详情 VO 转 A4 打印版数据(空值字段自动过滤)
  const buildPrintData = (d: OpeningStockDoc): PrintDocData => ({
    title: "期初库存单",
    docNo: d.docNo,
    header: printHeader([
      ["单据日期", fmtDate(d.docDate)],
      ["仓库", d.warehouse?.warehouseName],
      ["总数量", Number(d.totalQty).toFixed(2)],
      ["创建人", d.creator],
    ]),
    columns: [
      { title: "行号", align: "center" },
      { title: "物品" },
      { title: "批次号" },
      { title: "数量", align: "right" },
      { title: "期初单价", align: "right" },
      { title: "生产日期", align: "center" },
      { title: "到期日", align: "center" },
      { title: "库位", align: "center" },
    ],
    rows: (d.items ?? []).map((l) => [
      String(l.lineNo),
      l.itemName ? `${l.itemCode ?? ""} ${l.itemName}`.trim() : String(l.itemId),
      l.batchNo ?? "",
      Number(l.quantity).toFixed(4),
      l.unitPrice == null ? "" : Number(l.unitPrice).toFixed(4),
      l.productionDate ? fmtDate(l.productionDate) : "",
      l.expiryDate ? fmtDate(l.expiryDate) : "",
      l.locationId == null ? "" : String(l.locationId),
    ]),
    totals: ["", "", "合计", Number(d.totalQty).toFixed(4), "", "", "", ""],
    status: d.status === "finished" ? "已完成" : d.status,
    remark: d.remark,
  });


  const columns: ProColumns<OpeningStockDoc>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 220,
      fieldProps: { placeholder: "期初单号", allowClear: true },
      render: (_v, r) => (
        <span style={{ fontFamily: "monospace", fontSize: 13 }}>{r.docNo}</span>
      ),
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
      valueEnum: { finished: { text: "已完成" } },
      render: (_v, r) =>
        r.status === "finished" ? (
          <StatusTag status="inbound" label="已完成" />
        ) : (
          <Tag bordered>{r.status}</Tag>
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
      title: "日期",
      dataIndex: "docDate",
      width: 120,
      search: false,
      render: (_v, r) => fmtDate(r.docDate),
    },
    {
      title: "仓库",
      dataIndex: ["warehouse", "warehouseName"],
      width: 140,
      ellipsis: true,
      search: false,
    },
    {
      title: "物品行数",
      width: 100,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => r.items?.length ?? 0,
    },
    {
      title: "总数量",
      dataIndex: "totalQty",
      width: 100,
      align: "right",
      className: "num-cell",
      search: false,
      render: (v: unknown) => Number(v ?? 0).toFixed(4),
    },
    {
      title: "备注",
      dataIndex: "remark",
      width: 180,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.remark ?? "-",
    },
    { title: "创建人", dataIndex: "creator", width: 100, ellipsis: true, search: false },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 170,
      search: false,
      render: (_v, r) => fmtDateTime(r.createdAt),
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
      <ProTable<OpeningStockDoc>
        rowKey="id"
        actionRef={actionRef}
        columns={viewMode === "line" ? (lineColumns as unknown as ProColumns<OpeningStockDoc>[]) : columns}
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
        scroll={{ x: 1240 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            hasPerm("opening:create") && (
              <Link key="new" to="/opening/new">
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
        title={`期初单详情 - ${detail?.docNo ?? ""}`}
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
              <Descriptions.Item label="仓库">
                {detail.warehouse?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="单据日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="总数量">
                {Number(detail.totalQty).toFixed(4)}
              </Descriptions.Item>
              <Descriptions.Item label="创建人">
                {detail.creator ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {fmtDateTime(detail.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? "-"}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>期初明细</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                { title: "行号", dataIndex: "lineNo", width: 60 },
                {
                  title: "物品",
                  width: 200,
                  ellipsis: true,
                  render: (_v, r: OpeningStockDocItem) =>
                    r.itemName ? `${r.itemCode} ${r.itemName}` : String(r.itemId),
                },
                {
                  title: "数量",
                  dataIndex: "quantity",
                  width: 100,
                  align: "right" as const,
                  className: "num-cell",
                  render: (v: unknown) => Number(v ?? 0).toFixed(4),
                },
                {
                  title: "期初单价",
                  dataIndex: "unitPrice",
                  width: 100,
                  align: "right" as const,
                  className: "num-cell",
                  render: (v: string | number | null) =>
                    v == null ? "-" : Number(v).toFixed(4),
                },
                {
                  title: "批次号",
                  dataIndex: "batchNo",
                  width: 120,
                  render: (v?: string | null) => v ?? "-",
                },
                {
                  title: "生产日期",
                  dataIndex: "productionDate",
                  width: 110,
                  render: (v?: string | null) => fmtDate(v),
                },
                {
                  title: "到期日",
                  dataIndex: "expiryDate",
                  width: 110,
                  render: (v?: string | null) => fmtDate(v),
                },
                {
                  title: "库位",
                  dataIndex: "locationId",
                  width: 80,
                  render: (v?: number | null) => (v == null ? "-" : String(v)),
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
