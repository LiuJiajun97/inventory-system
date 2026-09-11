// 入库单列表(SPEC-WEB V2 2.3)
// 单号 copyable + 仓库 + 物品摘要 + 总数量 + 创建人 + 创建时间 + 状态 Tag
// 查看详情 Modal 展示行明细 + 序列号 Tag

import { useEffect, useState } from "react";
import { Form, Select, Button, Table, Modal, Descriptions, Tag, Space } from "antd";
import { Link, useLocation } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import { inboundApi, warehouseApi } from "../../api";
import type { InboundDoc, Warehouse } from "../../types";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";
import { fmtDateTime } from "../../utils/format";
import { StatusTag } from "../../components/StatusTag";

export function InboundListPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [rows, setRows] = useState<InboundDoc[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<InboundDoc | null>(null);
  const [form] = Form.useForm();
  const user = getUser();
  const location = useLocation();

  useEffect(() => {
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    onSearch({}, 1, 20);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (location.search.includes("refresh")) {
      onSearch(form.getFieldsValue(), 1, pageSize);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  const onSearch = async (values: { warehouseId?: number }, pg = page, ps = pageSize) => {
    setLoading(true);
    try {
      const res = await inboundApi.list({
        warehouseId: values.warehouseId,
        page: pg,
        pageSize: ps,
      });
      setRows(res.rows);
      setTotal(res.total);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<InboundDoc> = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 220,
      render: (v: string) => (
        <span style={{ fontFamily: "monospace", fontSize: 13 }}>{v}</span>
      ),
    },
    {
      title: "仓库",
      dataIndex: ["warehouse", "warehouseName"],
      width: 140,
      ellipsis: true,
    },
    {
      title: "关联单据",
      width: 150,
      render: (_v, r) => r.refDocNo ?? "-",
    },
    {
      title: "供应商",
      width: 130,
      ellipsis: true,
      render: (_v, r) => r.supplierName ?? "-",
    },
    {
      title: "物品摘要",
      width: 240,
      ellipsis: true,
      render: (_v, r) => {
        const items = r.items ?? [];
        if (items.length === 0) return "-";
        // 物品摘要:第一条 itemName + 行数提示
        return (
          <span>
            {items.length} 行明细 · 数量合计{" "}
            {items
              .reduce((s, it) => s + Number(it.quantity), 0)
              .toFixed(2)}
          </span>
        );
      },
    },
    {
      title: "总数量",
      width: 100,
      align: "right",
      className: "num-cell",
      render: (_v, r) =>
        (r.items ?? [])
          .reduce((s, it) => s + Number(it.quantity), 0)
          .toFixed(4),
    },
    { title: "创建人", dataIndex: "creator", width: 100, ellipsis: true },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 170,
      render: (v: string) =>
        fmtDateTime(v),
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      render: (v: string) =>
        v === "finished" ? (
          <StatusTag status="inbound" label="已完成" />
        ) : (
          <Tag bordered>{v}</Tag>
        ),
    },
    {
      title: "操作",
      width: 80,
      fixed: "right" as const,
      render: (_v, row) => (
        <a onClick={() => setDetail(row)}>查看详情</a>
      ),
    },
  ];

  const filterNode = (
    <Form
      form={form}
      layout="inline"
      onFinish={(v) => {
        setPage(1);
        onSearch(v);
      }}
    >
      <Form.Item label="仓库" name="warehouseId">
        <Select
          allowClear
          placeholder="全部仓库"
          style={{ width: 200 }}
          options={warehouses.map((w) => ({
            label: w.warehouseName,
            value: w.id,
          }))}
        />
      </Form.Item>
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit">
            查询
          </Button>
          <Button
            onClick={() => {
              form.resetFields();
              setPage(1);
              onSearch({}, 1, pageSize);
            }}
          >
            重置
          </Button>
        </Space>
      </Form.Item>
    </Form>
  );

  return (
    <>
      <ListPageShell
        title="入库单列表"
        extra={
          user?.role !== "viewer" && (
            <Link to="/inbound/new">
              <Button type="primary">新建入库单</Button>
            </Link>
          )
        }
        filter={filterNode}
        tableProps={{
          rowKey: "id",
          loading,
          columns,
          dataSource: rows,
          scroll: { x: 1480 },
          pagination: {
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
              onSearch(form.getFieldsValue(), p, ps);
            },
            showTotal: (t) => `共 ${t} 条`,
          },
        }}
      />

      <Modal
        title={`入库单详情 - ${detail?.docNo ?? ""}`}
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
            <div style={{ marginTop: 12, fontWeight: 600 }}>入库明细</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              columns={[
                {
                  title: "物品ID",
                  dataIndex: "itemId",
                  width: 80,
                },
                {
                  title: "数量",
                  dataIndex: "quantity",
                  width: 100,
                  align: "right",
                  className: "num-cell",
                  render: (v: string | number) => Number(v).toFixed(4),
                },
                {
                  title: "批次ID",
                  dataIndex: "batchId",
                  width: 80,
                },
                {
                  title: "库位ID",
                  dataIndex: "locationId",
                  width: 80,
                },
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