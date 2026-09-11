// 库位管理(SPEC-WEB V2 2.10)
// 筛选仓库 + 新建库位

import { useEffect, useState } from "react";
import {
  Form,
  Input,
  Select,
  Button,
  Table,
  Modal,
  Space,
  App,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { warehouseApi } from "../../api";
import type { Location, Warehouse } from "../../types";
import { ListPageShell } from "../../components/ListPageShell";

export function LocationListPage() {
  const [rows, setRows] = useState<Location[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const { message } = App.useApp();

  const load = async (wid?: number, pg = 1, ps = pageSize) => {
    setLoading(true);
    try {
      const res = await warehouseApi.listLocations({ warehouseId: wid, page: pg, pageSize: ps });
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

  useEffect(() => {
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const columns: ColumnsType<Location> = [
    {
      title: "仓库",
      dataIndex: "warehouseId",
      width: 200,
      render: (id: number) =>
        warehouses.find((w) => w.id === id)?.warehouseName ?? id,
    },
    {
      title: "编码",
      dataIndex: "locationCode",
      width: 160,
      render: (v: string) => (
        <span style={{ fontFamily: "monospace" }}>{v}</span>
      ),
    },
    { title: "名称", dataIndex: "locationName", ellipsis: true },
  ];

  const onCreate = async () => {
    const v = await form.validateFields();
    try {
      await warehouseApi.createLocation(v);
      message.success("库位创建成功");
      setOpen(false);
      form.resetFields();
      load(warehouseId, page, pageSize);
    } catch {
      // 拦截器已处理
    }
  };

  return (
    <>
      <ListPageShell
        title="库位管理"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="筛选仓库"
              style={{ width: 200 }}
              value={warehouseId}
              options={warehouses.map((w) => ({
                label: w.warehouseName,
                value: w.id,
              }))}
              onChange={(v) => {
                setWarehouseId(v);
                load(v, 1, pageSize);
              }}
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setOpen(true)}
            >
              新建库位
            </Button>
          </Space>
        }
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
            onChange: (p, ps) => load(warehouseId, p, ps),
            showTotal: (t) => `共 ${t} 条`,
          },
        }}
      />

      <Modal
        title="新建库位"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={onCreate}
        okText="提交"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item
            label="仓库"
            name="warehouseId"
            rules={[{ required: true, message: "仓库必填" }]}
          >
            <Select
              options={warehouses.map((w) => ({
                label: w.warehouseName,
                value: w.id,
              }))}
            />
          </Form.Item>
          <Form.Item
            label="库位编码"
            name="locationCode"
            rules={[{ required: true, message: "编码必填" }]}
          >
            <Input placeholder="如 A-03" />
          </Form.Item>
          <Form.Item label="库位名称" name="locationName">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}