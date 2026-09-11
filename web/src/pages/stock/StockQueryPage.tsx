// 库存查询(SPEC-WEB V2 2.7)
// 筛选(仓库/物品关键字/批次号);数量 0 行灰色弱化

import { useEffect, useState } from "react";
import { Form, Select, Input, Button, Table, Space } from "antd";
import type { ColumnsType } from "antd/es/table";
import { stockApi, warehouseApi } from "../../api";
import type { StockRow, Warehouse } from "../../types";
import { fmtDate } from "../../utils/format";
import { ListPageShell } from "../../components/ListPageShell";

export function StockQueryPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [rows, setRows] = useState<StockRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    onSearch({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onSearch = async (
    values: {
      warehouseId?: number;
      itemKeyword?: string;
      batchNo?: string;
    },
    pg = page,
    ps = pageSize,
  ) => {
    setLoading(true);
    try {
      const res = await stockApi.query({ ...values, page: pg, pageSize: ps });
      setRows(res.rows);
      setTotal(res.total);
      setPage(pg);
      setPageSize(ps);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<StockRow> = [
    {
      title: "仓库",
      dataIndex: ["warehouse", "warehouseName"],
      width: 140,
      ellipsis: true,
    },
    {
      title: "物品",
      width: 240,
      render: (_v, r) => (
        <div style={{ lineHeight: 1.4 }}>
          <div>{r.item?.itemName ?? "-"}</div>
          <div style={{ color: "#9ca3af", fontSize: 12 }}>
            {r.item?.itemCode ?? "-"}
          </div>
        </div>
      ),
    },
    {
      title: "批次",
      dataIndex: ["batch", "batchNo"],
      width: 140,
      ellipsis: true,
    },
    {
      title: "库位",
      dataIndex: ["location", "locationCode"],
      width: 120,
      ellipsis: true,
    },
    {
      title: "数量",
      dataIndex: "quantity",
      width: 140,
      align: "right",
      className: "num-cell",
      render: (v: string | number, r: StockRow) => {
        const n = Number(v);
        return (
          <span className={n === 0 ? "" : ""}>
            {n.toFixed(4)}
            <span style={{ color: "#9ca3af", marginLeft: 4, fontSize: 12 }}>
              {r.item?.unit ?? ""}
            </span>
          </span>
        );
      },
    },
    {
      title: "已预占",
      dataIndex: "preAllocatedQty",
      width: 100,
      align: "right",
      className: "num-cell",
      render: (v: string | number | undefined) =>
        v == null ? "-" : Number(v).toFixed(4),
    },
    {
      title: "可用量",
      dataIndex: "availableQty",
      width: 100,
      align: "right",
      className: "num-cell",
      render: (v: string | number | undefined) =>
        v == null ? "-" : (
          <span style={{ color: Number(v) < 0 ? "#cf1322" : undefined }}>
            {Number(v).toFixed(4)}
          </span>
        ),
    },
    {
      title: "保质期",
      dataIndex: ["batch", "expiryDate"],
      width: 120,
      render: (v?: string | null) => fmtDate(v),
    },
  ];

  const filterNode = (
    <Form
      form={form}
      layout="inline"
      onFinish={(values) => onSearch(values, 1, pageSize)}
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
      <Form.Item label="物品" name="itemKeyword">
        <Input placeholder="编码或名称" style={{ width: 200 }} allowClear />
      </Form.Item>
      <Form.Item label="批次号" name="batchNo">
        <Input placeholder="批次号" style={{ width: 160 }} allowClear />
      </Form.Item>
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit">
            查询
          </Button>
          <Button
            onClick={() => {
              form.resetFields();
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
    <ListPageShell
      title="库存查询"
      filter={filterNode}
      tableProps={{
        rowKey: "id",
        loading,
        columns,
        dataSource: rows,
        scroll: { x: 980 },
        pagination: {
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => onSearch(form.getFieldsValue(), p, ps),
          showTotal: (t) => `共 ${t} 条`,
        },
        rowClassName: (r: StockRow) =>
          Number(r.quantity) === 0 ? "row-zero" : "",
      }}
    />
  );
}