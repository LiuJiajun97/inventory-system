// 出库单列表(SPEC-WEB V2 2.5)
// ProTable 版:筛选字段由 columns 配置驱动(单号/仓库/状态/日期区间)

import { useEffect, useRef, useState } from "react";
import { Button, Descriptions, Modal, Space, Table, Tag } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { Link, useLocation } from "react-router-dom";
import dayjs from "dayjs";
import { outboundApi, warehouseApi } from "../../api";
import type { OutboundDoc, Warehouse } from "../../types";
import { getUser } from "../../auth/useAuth";
import { fmtDateTime } from "../../utils/format";
import { StatusTag } from "../../components/StatusTag";

// ProTable dateRange transform 实收值:form 存 'YYYY-MM-DD' 字符串(直接输入路径);
// 部分路径(弹层选择)可能传 dayjs,两种都兼容
function toDay(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

export function OutboundListPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<OutboundDoc | null>(null);
  const actionRef = useRef<ActionType>();
  const user = getUser();
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
  const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    status?: string;
    warehouseId?: number;
    from?: string;
    to?: string;
  }) => {
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
      render: (_v, r) => r.refDocNo ?? "-",
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
            {items.reduce((s, it) => s + Number(it.quantity), 0).toFixed(2)}
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
      render: (_v, r) =>
        (r.items ?? [])
          .reduce((s, it) => s + Number(it.quantity), 0)
          .toFixed(4),
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
        actionRef={actionRef}
        columns={columns}
        request={request}
        headerTitle={false}
        options={false}
        scroll={{ x: 1480 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            user?.role !== "viewer" && (
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
        footer={null}
        width={720}
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
                  render: (v: string | number) => Number(v).toFixed(4),
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
    </>
  );
}
