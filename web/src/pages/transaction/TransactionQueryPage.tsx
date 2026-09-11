// 流水查询(SPEC-WEB V2 2.8)
// 变动量正绿负红 + tabular-nums + 业务 Tag 入库=blue 出库=orange

import { useEffect, useState } from "react";
import { Form, Select, DatePicker, Button, Table, Space } from "antd";
import type { ColumnsType } from "antd/es/table";
import { itemApi, transactionApi, warehouseApi } from "../../api";
import type { Item, StockTransaction, Warehouse } from "../../types";
import { ListPageShell } from "../../components/ListPageShell";
import { fmtDateTime } from "../../utils/format";
import { BizTag, BIZ_OPTIONS } from "../../components/StatusTag";

export function TransactionQueryPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [rows, setRows] = useState<StockTransaction[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<Item[]>([]);
  const [form] = Form.useForm();

  useEffect(() => {
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    itemApi.list({ page: 1, pageSize: 200 }).then((r) => setItems(r.rows)).catch(() => undefined);
    // 进入页面即按默认条件(最新流水,每页 20)拉取,避免初始"暂无数据"
    onSearch({}, 1, 20);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // p/ps 显式传参,避免读到 setState 前的旧闭包值
  const onSearch = async (
    values: {
      warehouseId?: number;
      itemId?: number;
      bizCode?: string;
      range?: [string, string];
    },
    p: number = page,
    ps: number = pageSize,
  ) => {
    setLoading(true);
    try {
      const res = await transactionApi.query({
        warehouseId: values.warehouseId,
        itemId: values.itemId,
        bizCode: values.bizCode as "inbound" | "outbound" | undefined,
        from: values.range?.[0],
        to: values.range?.[1],
        page: p,
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

  const columns: ColumnsType<StockTransaction> = [
    {
      title: "时间",
      dataIndex: "createdAt",
      width: 170,
      render: (v: string) =>
        fmtDateTime(v),
    },
    {
      title: "仓库",
      dataIndex: ["warehouse", "warehouseName"],
      width: 140,
      ellipsis: true,
    },
    {
      title: "物品",
      width: 200,
      ellipsis: true,
      render: (_v, r) => (
        <span>
          {r.item?.itemName ?? "-"}
          {r.item?.itemCode && (
            <span style={{ color: "#9ca3af", marginLeft: 4, fontSize: 12 }}>
              ({r.item.itemCode})
            </span>
          )}
        </span>
      ),
    },
    {
      title: "批次",
      dataIndex: ["batch", "batchNo"],
      width: 130,
      ellipsis: true,
    },
    {
      title: "业务",
      dataIndex: "bizCode",
      width: 80,
      render: (v: string) => <BizTag biz={v} />,
    },
    {
      title: "单号",
      dataIndex: "docNo",
      width: 180,
      ellipsis: true,
      render: (v?: string | null) =>
        v ? (
          <span style={{ fontFamily: "monospace", fontSize: 12 }}>{v}</span>
        ) : (
          "-"
        ),
    },
    {
      title: "变动量",
      dataIndex: "changeQty",
      width: 130,
      align: "right",
      className: "num-cell",
      render: (v: string | number) => {
        const n = Number(v);
        return (
          <span className={n >= 0 ? "qty-positive" : "qty-negative"}>
            {n >= 0 ? "+" : ""}
            {n.toFixed(4)}
          </span>
        );
      },
    },
    {
      title: "结存",
      dataIndex: "afterQty",
      width: 120,
      align: "right",
      className: "num-cell",
      render: (v: string | number) => Number(v).toFixed(4),
    },
    { title: "操作人", dataIndex: "operator", width: 100, ellipsis: true },
  ];

  const filterNode = (
    <Form
      form={form}
      layout="inline"
      onFinish={(v) => {
        setPage(1);
        onSearch(v, 1, pageSize);
      }}
    >
      <Form.Item label="仓库" name="warehouseId">
        <Select
          allowClear
          placeholder="全部仓库"
          style={{ width: 180 }}
          options={warehouses.map((w) => ({
            label: w.warehouseName,
            value: w.id,
          }))}
        />
      </Form.Item>
      <Form.Item label="物品" name="itemId">
        <Select
          allowClear
          showSearch
          placeholder="全部"
          style={{ width: 180 }}
          optionFilterProp="label"
          options={items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }))}
        />
      </Form.Item>
      <Form.Item label="业务" name="bizCode">
        <Select
          allowClear
          placeholder="全部"
          style={{ width: 140 }}
          options={BIZ_OPTIONS}
        />
      </Form.Item>
      <Form.Item label="日期范围" name="range">
        <DatePicker.RangePicker />
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
              setPageSize(20);
              onSearch({}, 1, 20);
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
      filter={filterNode}
      tableProps={{
        rowKey: "id",
        loading,
        columns,
        dataSource: rows,
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
  );
}