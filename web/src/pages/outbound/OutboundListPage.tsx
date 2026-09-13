// 出库单列表(SPEC-WEB V2 2.5)
// ProTable 版:筛选字段由 columns 配置驱动(单号/仓库/状态/日期区间)

import { useEffect, useRef, useState , useMemo} from "react";
import { Button, Descriptions, Modal, Segmented, Space, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { outboundApi, warehouseApi } from "../../api";
import { ExportButton } from "../../components/ExportButton";
import { PrintDocModal, printHeader, printMoney, usePrintNameMaps, type PrintDocData } from "../../components/PrintDocModal";
import type { OutboundDoc, Warehouse } from "../../types";
import type { DocLine } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { fmtDateTime, REF_TYPE_LABEL, fmtMoney, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { StatusTag } from "../../components/StatusTag";

// ProTable dateRange transform 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

// V14 打印:序列号字段后端存 JSON 数组字符串,打印拼接为逗号分隔文本
function parseSerials(v?: string | null): string {
  if (!v) return "";
  try {
    const arr: unknown = JSON.parse(v);
    return Array.isArray(arr) ? arr.join(",") : String(v);
  } catch {
    return String(v);
  }
}

export function OutboundListPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<OutboundDoc | null>(null);
  // V17 主表/明细视图切换(组件内状态,默认主表)
  const [viewMode, setViewMode] = useState<"main" | "line">("main");
  const [printOpen, setPrintOpen] = useState(false);
  const { itemText, locText } = usePrintNameMaps(); // V14 打印:ID→可读文本映射
  const actionRef = useRef<ActionType>();
  const { hasPerm } = usePermission();  const canExport = hasPerm("outbound:export");
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

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
    // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    status?: string;
    warehouseId?: number;
    from?: string;
    to?: string;
  }) => {
    setFilterParams({
      docNo: params.docNo,
      status: params.status,
      warehouseId: params.warehouseId,
      from: params.from,
      to: params.to,    });
    const res = await outboundApi.list({
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
    const res = await outboundApi.lines({
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
      setDetail(await outboundApi.get(r.docId));
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
    { title: "日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => r.docDate ?? "-" },
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
    { title: "状态", dataIndex: "status", width: 90, valueEnum: { finished: { text: "已完成" } }, render: (_v, r) => (r.status === "finished" ? <StatusTag status="outbound" label="已完成" /> : <Tag bordered>{r.status}</Tag>) },
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
  // V14 打印:出库单详情 VO 转 A4 打印版数据(运输信息有值才显示,空值自动过滤)
  const buildPrintData = (d: OutboundDoc): PrintDocData => {
    const totalQty = (d.items ?? []).reduce((s, it) => s + Number(it.quantity), 0);
    const refText = d.refDocNo
      ? `${d.refType ? REF_TYPE_LABEL[d.refType] ?? d.refType : ""} ${d.refDocNo}`.trim()
      : null;
    return {
      title: "出库单",
      docNo: d.docNo,
      header: printHeader([
        ["仓库", d.warehouse?.warehouseName],
        ["关联单据", refText],
        ["客户", d.customerName],
        ["承运商", d.carrier],
        ["车牌", d.vehicleNo],
        ["运费", printMoney(d.freight)],
        ["总数量", fmtQty(totalQty)],
        ["创建人", d.creator],
      ]),
      columns: [
        { title: "行号", align: "center" },
        { title: "物品" },
        { title: "库位" },
        { title: "数量", align: "right" },
        { title: "序列号" },
      ],
      rows: (d.items ?? []).map((l, i) => [
        String(l.lineNo ?? i + 1),
        itemText(l.itemId),
        locText(l.locationId),
        fmtQty(l.quantity),
        parseSerials(l.serialNos),
      ]),
      totals: ["", "合计", "", fmtQty(totalQty), ""],
      status: d.status === "finished" ? "已完成" : d.status,
      remark: d.remark,
    };
  };
  // 导出 URL:带当前筛选条件(导出不分页)
  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    if (filterParams.docNo !== undefined && filterParams.docNo !== "") p.set("docNo", String(filterParams.docNo));
    if (filterParams.status !== undefined && filterParams.status !== "") p.set("status", String(filterParams.status));
    if (filterParams.warehouseId !== undefined && filterParams.warehouseId !== "") p.set("warehouseId", String(filterParams.warehouseId));
    if (filterParams.from !== undefined && filterParams.from !== "") p.set("from", String(filterParams.from));
    if (filterParams.to !== undefined && filterParams.to !== "") p.set("to", String(filterParams.to));
    const qs = p.toString();
    return "/outbound/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);


  const columns: ProColumns<OutboundDoc>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 220,
      fieldProps: { placeholder: "出库单号", allowClear: true },
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
          <StatusTag status="outbound" label="已完成" />
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
      title: "仓库",
      width: 140,
      ellipsis: true,
      search: false,
      // 后端嵌入 warehouse 对象;缺失时用本地仓库列表兑底,避免显示 ID
      render: (_v, r) =>
        r.warehouse?.warehouseName ??
        warehouses.find((w) => w.id === r.warehouseId)?.warehouseName ??
        String(r.warehouseId),
    },
    {
      title: "关联单据",
      width: 150,
      search: false,
      render: (_v, r) => {
        if (!r.refDocNo) return "-";
        const label = r.refType ? REF_TYPE_LABEL[r.refType] : undefined;
        return label ? `${label} ${r.refDocNo}` : r.refDocNo;
      },
    },
    {
      title: "客户",
      width: 130,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.customerName ?? "-",
    },
    {
      title: "物品摘要",
      width: 240,
      ellipsis: true,
      search: false,
      render: (_v, r) => {
        const items = r.items ?? [];
        if (items.length === 0) return "-";
        return (
          <span>
            {items.length} 行明细 · 数量合计{" "}
            {fmtQty(items.reduce((s, it) => s + Number(it.quantity), 0))}
          </span>
        );
      },
    },
    {
      title: "总数量",
      width: 100,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => fmtQty((r.items ?? []).reduce((s, it) => s + Number(it.quantity), 0)),
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
      render: (_v, row) => <a onClick={() => setDetail(row)}>查看详情</a>,
    },
  ];

  return (
    <>
      <ProTable<OutboundDoc>
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无出库单" /> }}
        actionRef={actionRef}
        columns={viewMode === "line" ? (lineColumns as unknown as ProColumns<OutboundDoc>[]) : columns}
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
        scroll={{ x: 1480 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            canExport && (
              <ExportButton key="export" url={exportUrl} filename="出库单.xlsx" />
            ),
            hasPerm("outbound:create") && (
              <Link key="new" to="/outbound/new">
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
        title={`出库单详情 - ${detail?.docNo ?? ""}`}
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
              <Descriptions.Item label="创建人">
                {detail.creator ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {fmtDateTime(detail.createdAt)}
              </Descriptions.Item>
              {/* V9 运输信息(可空) */}
              <Descriptions.Item label="承运商">{detail.carrier ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="车牌">{detail.vehicleNo ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="运费">
                {fmtMoney(detail.freight)}
              </Descriptions.Item>
              <Descriptions.Item label="备注" span={2}>
                {detail.remark ?? "-"}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>出库明细</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                { title: "物品ID", dataIndex: "itemId", width: 80 },
                {
                  title: "数量",
                  dataIndex: "quantity",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string | number) => fmtQty(v),
                },
                { title: "批次ID", dataIndex: "batchId", width: 80 },
                { title: "库位ID", dataIndex: "locationId", width: 80 },
                {
                  title: "序列号",
                  dataIndex: "serialNos",
                  render: (v?: string | null) => {
                    if (!v) return "-";
                    try {
                      const arr = JSON.parse(v);
                      return (
                        <Space wrap size={[4, 4]}>
                          {arr.map((s: string, i: number) => (
                            <Tag key={i} bordered>
                              {s}
                            </Tag>
                          ))}
                        </Space>
                      );
                    } catch {
                      return v;
                    }
                  },
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
