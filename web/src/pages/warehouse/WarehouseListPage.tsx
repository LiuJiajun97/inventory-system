// 仓库管理(SPEC-WEB V2 2.10)
// ProTable 版:筛选字段由 columns 配置驱动(关键字/类型),新建按钮经 optionRender 放筛选行右侧
// Drawer 双态:新建 / 编辑(编码锁死不可改);4 个 enable 开关联动:开保质期自动开批次

import { useEffect, useRef, useState } from "react";
import {
  Form,
  Input,
  Select,
  Button,
  Drawer,
  Row,
  Col,
  Switch,
  Space,
  Tag,
  App,
  theme,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { warehouseApi, dictApi } from "../../api";
import type { Warehouse } from "../../types";
import { getUser } from "../../auth/useAuth";
import { StatusTag } from "../../components/StatusTag";

export function WarehouseListPage() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Warehouse | null>(null);
  const [form] = Form.useForm();
  const actionRef = useRef<ActionType>();
  const { message } = App.useApp();
  const user = getUser();
  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();
  const [enableBatch, setEnableBatch] = useState(false);
  const [enableExpiry, setEnableExpiry] = useState(false);
  const [typeOptions, setTypeOptions] = useState<Array<{ code: string; label: string }>>([]);

  // 类型下拉数据源(异步加载,仅用于筛选项与列展示)
  useEffect(() => {
    dictApi.getType("warehouseType").then(setTypeOptions).catch(() => undefined);
  }, []);

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    keyword?: string;
    warehouseType?: string;
  }) => {
    const res = await warehouseApi.list({
      keyword: params.keyword || undefined,
      warehouseType: params.warehouseType || undefined,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

  const columns: ProColumns<Warehouse>[] = [
    {
      title: "编码",
      dataIndex: "warehouseCode",
      width: 140,
      search: false,
      render: (_v, r) => (
        <span style={{ fontFamily: "monospace" }}>{r.warehouseCode}</span>
      ),
    },
    { title: "名称", dataIndex: "warehouseName", width: 180, search: false },
    {
      title: "类型",
      dataIndex: "warehouseType",
      width: 100,
      search: false,
      render: (_v, r) => <Tag>{typeOptions.find((d) => d.code === r.warehouseType)?.label ?? r.warehouseType}</Tag>,
    },
    {
      title: "配置",
      width: 320,
      search: false,
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
      ellipsis: true,
      search: false,
      render: (_v, r) => r.creator ?? "-",
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      search: false,
      render: (_v, r) =>
        r.status === 1 ? (
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
            search: false,
            render: (_v: unknown, r: Warehouse) => (
              <a className="action-edit" onClick={() => openEdit(r)}>
                编辑
              </a>
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
      actionRef.current?.reload();
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
      <ProTable<Warehouse>
        rowKey="id"
        actionRef={actionRef}
        columns={[
          {
            title: "关键字",
            dataIndex: "keyword",
            hideInTable: true,
            fieldProps: { placeholder: "编码/名称", allowClear: true },
          },
          {
            title: "类型",
            dataIndex: "warehouseType",
            valueType: "select",
            hideInTable: true,
            fieldProps: {
              allowClear: true,
              placeholder: "全部",
              options: typeOptions.map((d) => ({ label: d.label, value: d.code })),
            },
          },
          ...columns,
        ]}
        request={request}
        headerTitle={false}
        options={false}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            user?.role === "admin" && (
              <Button key="new" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                新建
              </Button>
            ),
          ],
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Drawer
        title={editing ? "编辑仓库" : "新建仓库"}
        open={drawerOpen}
        onClose={closeDrawer}
        width={480}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮"保存"
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={closeDrawer}>取消</Button>
              <Button type="primary" onClick={onSave}>
                保存
              </Button>
            </Space>
          </div>
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
          {/* 类型与启用配置:两列对齐规格下分别占一列/整行 */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="类型"
                name="warehouseType"
                rules={[{ required: true, message: "类型必填" }]}
              >
                <Select
                  options={typeOptions.map((d) => ({ label: d.label, value: d.code }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
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
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
}