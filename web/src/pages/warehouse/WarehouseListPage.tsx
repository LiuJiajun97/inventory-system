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
  Checkbox,
  App,
  theme,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { warehouseApi, dictApi } from "../../api";
import type { Warehouse } from "../../types";
import { usePermission } from "../../auth/usePermission";
import { StatusTag } from "../../components/StatusTag";
import { EmptyHint } from "../../components/EmptyHint";
import { proTableRequest } from "../../utils/proTable";

export function WarehouseListPage() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Warehouse | null>(null);
  const [form] = Form.useForm();
  const actionRef = useRef<ActionType>();
  const { message } = App.useApp();
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端拦截)
  const canEdit = hasPerm("warehouse:edit");
  const canCreate = hasPerm("warehouse:create");
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
  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { keyword?: string; warehouseType?: string }) => ({
      keyword: p.keyword || undefined,
      warehouseType: p.warehouseType || undefined,
    }),
    warehouseApi.list,
  );

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
    ...(canEdit
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
      // V10 默认仓(可空)
      defaultWarehouse: r.defaultWarehouse === true,
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
          // V10 默认仓(排他由后端同事务处理)
          defaultWarehouse: v.defaultWarehouse === true,
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
          // V10 默认仓(排他由后端同事务处理)
          defaultWarehouse: v.defaultWarehouse === true,
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

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(columns, "warehouse-list");
  return (
    <>
      <ProTable<Warehouse>
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无仓库" /> }}
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
          ...rcColumns,
        ]}
        request={request}
        headerTitle={false}
        options={{
          density: false,
          reload: false,
          fullScreen: false,
          setting: rcOptionSetting,
        }}
        columnsState={columnsState}
        onColumnsStateChange={onColumnsChange}
        components={rcComponents}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            canCreate && (
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
              {/* V10 默认仓(全表至多一个,排他由后端处理) */}
              <Space>
                <span style={{ width: 80 }}>默认仓</span>
                <Form.Item name="defaultWarehouse" valuePropName="checked" noStyle>
                  <Checkbox />
                </Form.Item>
              </Space>
            </div>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
}