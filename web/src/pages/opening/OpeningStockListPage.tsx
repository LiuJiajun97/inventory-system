// 期初库存列表(V13)
// ProTable 版:筛选字段由 columns 配置驱动(单号/仓库/状态/日期区间)
// 单号 + 日期 + 仓库 + 物品行数 + 总数量 + 状态 + 备注
// 查看详情 Modal(960 + .doc-detail-modal 紧凑)展示行明细(物品/批次/数量/单价/效期/库位)

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { openingApi, warehouseApi } from "../../api";
import type { OpeningStockDoc, OpeningStockDocItem, Warehouse } from "../../types";
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
        columns={columns}
        request={request}
        headerTitle={false}
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
        footer={null}
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
    </>
  );
}
