// 仓库管理(SPEC-WEB V2 2.10)
// Drawer 双态:新建 / 编辑(编码锁死不可改);4 个 enable 开关联动:开保质期自动开批次

import { useEffect, useState } from "react";
import {
  Form,
  Input,
  Select,
  Button,
  Table,
  Drawer,
  Row,
  Col,
  Switch,
  Space,
  Tag,
  App,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined } from "@ant-design/icons";
import { warehouseApi, dictApi } from "../../api";
import type { Warehouse } from "../../types";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";
import { StatusTag } from "../../components/StatusTag";

export function WarehouseListPage() {
  const [rows, setRows] = useState<Warehouse[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Warehouse | null>(null);
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const user = getUser();
  const [enableBatch, setEnableBatch] = useState(false);
  const [enableExpiry, setEnableExpiry] = useState(false);
  const [typeOptions, setTypeOptions] = useState<Array<{ code: string; label: string }>>([]);

  const load = async (pg = 1, ps = pageSize) => {
    setLoading(true);
    try {
      const res = await warehouseApi.list({ page: pg, pageSize: ps });
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
    load();
    dictApi.getType("warehouseType").then(setTypeOptions).catch(() => undefined);
  }, []);

  const columns: ColumnsType<Warehouse> = [
    {
      title: "编码",
      dataIndex: "warehouseCode",
      width: 140,
      render: (v: string) => (
        <span style={{ fontFamily: "monospace" }}>{v}</span>
      ),
    },
    { title: "名称", dataIndex: "warehouseName", width: 180 },
    {
      title: "类型",
      dataIndex: "warehouseType",
      width: 100,
      render: (v: string) => <Tag>{typeOptions.find((d) => d.code === v)?.label ?? v}</Tag>,
    },
    {
      title: "配置",
      width: 320,
      render: (_v, r) => (
        <Space wrap size={[4, 4]}>
          <Tag bordered>批次 {r.enableBatch ? "✓" : "✗"}</Tag>
          <Tag bordered>保质期 {r.enableExpiry ? "✓" : "✗"}</Tag>
          <Tag bordered>序列号 {r.enableSerial ? "✓" : "✗"}</Tag>
          <Tag bordered>库位 {r.enableLocation ? "✓" : "✗"}</Tag>
        </Space>
      ),
    },
    {
      title: "创建人",
      dataIndex: "creator",
      width: 100,
      render: (v?: string | null) => v ?? "-",
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      render: (v: number) =>
        v === 1 ? (
          <StatusTag status="enabled" />
        ) : (
          <StatusTag status="disabled" />
        ),
    },
    ...(user?.role === "admin"
      ? [
          {
            title: "操作",
            width: 80,
            render: (_v: unknown, r: Warehouse) => (
              <Button type="link" size="small" onClick={() => openEdit(r)}>
                编辑
              </Button>
            ),
          },
        ]
      : []),
  ];

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setEnableBatch(false);
    setEnableExpiry(false);
    setDrawerOpen(true);
  };

  const openEdit = (r: Warehouse) => {
    setEditing(r);
    form.setFieldsValue({
      warehouseCode: r.warehouseCode,
      warehouseName: r.warehouseName,
      warehouseType: r.warehouseType,
      enableSerial: r.enableSerial,
      enableLocation: r.enableLocation,
      status: r.status === 1,
    });
    setEnableBatch(!!r.enableBatch);
    setEnableExpiry(!!r.enableExpiry);
    setDrawerOpen(true);
  };

  const onSave = async () => {
    const v = await form.validateFields();
    try {
      if (editing) {
        await warehouseApi.update(editing.id, {
          warehouseName: v.warehouseName,
          warehouseType: v.warehouseType,
          enableBatch,
          enableExpiry,
          enableSerial: v.enableSerial,
          enableLocation: v.enableLocation,
          status: v.status ? 1 : 0,
        });
        message.success("仓库已更新");
      } else {
        await warehouseApi.create({
          warehouseCode: v.warehouseCode,
          warehouseName: v.warehouseName,
          warehouseType: v.warehouseType,
          enableBatch,
          enableExpiry,
          enableSerial: v.enableSerial,
          enableLocation: v.enableLocation,
        });
        message.success("仓库创建成功");
      }
      setDrawerOpen(false);
      form.resetFields();
      setEnableBatch(false);
      setEnableExpiry(false);
      setEditing(null);
      load(page, pageSize);
    } catch {
      // 拦截器已处理
    }
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    form.resetFields();
    setEnableBatch(false);
    setEnableExpiry(false);
    setEditing(null);
  };

  return (
    <>
      <ListPageShell
        title="仓库管理"
        extra={
          user?.role === "admin" && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
            >
              新建仓库
            </Button>
          )
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
            onChange: (p, ps) => load(p, ps),
            showTotal: (t) => `共 ${t} 条`,
          },
        }}
      />

      <Drawer
        title={editing ? "编辑仓库" : "新建仓库"}
        open={drawerOpen}
        onClose={closeDrawer}
        width={480}
        extra={
          <Space>
            <Button onClick={closeDrawer}>取消</Button>
            <Button type="primary" onClick={onSave}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="编码"
                name="warehouseCode"
                rules={[{ required: !editing, message: "编码必填" }]}
              >
                <Input
                  placeholder="如 WH-RAW-01"
                  disabled={editing != null}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="名称"
                name="warehouseName"
                rules={[{ required: true, message: "名称必填" }]}
              >
                <Input placeholder="如 原材料仓" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            label="类型"
            name="warehouseType"
            rules={[{ required: true, message: "类型必填" }]}
          >
            <Select
              options={typeOptions.map((d) => ({ label: d.label, value: d.code }))}
            />
          </Form.Item>

          <Form.Item label="启用配置">
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              <Space>
                <span style={{ width: 80 }}>批次</span>
                <Switch
                  checked={enableBatch}
                  onChange={(v) => setEnableBatch(v)}
                />
              </Space>
              <Space>
                <span style={{ width: 80 }}>保质期</span>
                <Switch
                  checked={enableExpiry}
                  onChange={(v) => {
                    setEnableExpiry(v);
                    // 联动:开保质期自动开批次
                    if (v) setEnableBatch(true);
                  }}
                />
                {enableExpiry && (
                  <span style={{ color: "#1d4ed8", fontSize: 12 }}>
                    已自动启用批次
                  </span>
                )}
              </Space>
              <Space>
                <span style={{ width: 80 }}>序列号</span>
                <Form.Item name="enableSerial" valuePropName="checked" noStyle>
                  <Switch />
                </Form.Item>
              </Space>
              <Space>
                <span style={{ width: 80 }}>库位</span>
                <Form.Item name="enableLocation" valuePropName="checked" noStyle>
                  <Switch />
                </Form.Item>
              </Space>
              {editing && (
                <Space>
                  <span style={{ width: 80 }}>启用</span>
                  <Form.Item name="status" valuePropName="checked" noStyle>
                    <Switch />
                  </Form.Item>
                </Space>
              )}
            </div>
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
}