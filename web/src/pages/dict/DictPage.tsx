// 字典管理页(传统类型列表 + Drawer 二级管理弹窗)
// ProTable 版:主表全量返回(非分页,pagination=false),关键字筛选保持前端内存过滤
// admin 可见操作列;viewer 只读

import { useState, useRef } from "react";
import {
  Table,
  Switch,
  Input,
  InputNumber,
  Modal,
  Form,
  Button,
  Badge,
  Space,
  App,
  Drawer,
  Tag,
  Popconfirm,
  Typography,
} from "antd";
import { PlusOutlined, EditOutlined, ToolOutlined } from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { dictApi } from "../../api";
import type { DictItem, DictTypeItem } from "../../types/phase1";

export function DictPage() {
  const { message } = App.useApp();
  // 新建类型弹窗
  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [typeForm] = Form.useForm();
  // 编辑类型弹窗
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<DictTypeItem | null>(null);
  const [editForm] = Form.useForm();
  // Drawer 状态
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerType, setDrawerType] = useState<DictTypeItem | null>(null);
  // Drawer 内字典项
  const [items, setItems] = useState<DictItem[]>([]);
  const [itemsLoading, setItemsLoading] = useState(false);
  // 新建字典项弹窗
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [itemForm] = Form.useForm();
  // 角色(接口全量返回成功即视为 admin,保持原逻辑)
  const [isAdmin, setIsAdmin] = useState(false);
  const actionRef = useRef<ActionType>();

  // 数据全量返回(非分页):后端 getTypes() + 前端内存按关键字过滤
  const request = async (params: { current?: number; pageSize?: number; keyword?: string }) => {
    try {
      const types = await dictApi.getTypes();
      setIsAdmin(true);
      const kw = (params.keyword ?? "").trim().toLowerCase();
      const data = types.filter(
        (t) => !kw || t.typeCode.toLowerCase().includes(kw) || t.typeName.toLowerCase().includes(kw)
      );
      return { data, success: true, total: data.length };
    } catch {
      setIsAdmin(false);
      return { data: [] as DictTypeItem[], success: true, total: 0 };
    }
  };

  // 加载 Drawer 内字典项
  const loadItems = async (typeCode: string) => {
    setItemsLoading(true);
    try {
      const res = await dictApi.getAll(typeCode);
      setItems(res);
    } catch {
      setItems([]);
    } finally {
      setItemsLoading(false);
    }
  };

  // 打开 Drawer
  const openDrawer = (record: DictTypeItem) => {
    setDrawerType(record);
    setDrawerOpen(true);
    loadItems(record.typeCode);
  };

  // 关闭 Drawer 后刷新主表格
  const closeDrawer = () => {
    setDrawerOpen(false);
    setDrawerType(null);
    setItems([]);
    actionRef.current?.reload();
  };

  // 新建类型
  const onCreateType = async () => {
    const v = await typeForm.validateFields();
    try {
      await dictApi.createType({
        typeCode: v.typeCode,
        typeName: v.typeName,
        remark: v.remark || "",
      });
      message.success("类型创建成功");
      setTypeModalOpen(false);
      typeForm.resetFields();
      actionRef.current?.reload();
    } catch {
      // 拦截器已处理
    }
  };

  // 打开编辑类型弹窗
  const openEditModal = (record: DictTypeItem) => {
    setEditingType(record);
    editForm.setFieldsValue({
      typeName: record.typeName,
      remark: record.remark,
    });
    setEditModalOpen(true);
  };

  // 保存编辑类型
  const onSaveEditType = async () => {
    if (!editingType) return;
    const v = await editForm.validateFields();
    try {
      await dictApi.updateType(editingType.typeCode, {
        typeName: v.typeName,
        remark: v.remark || "",
      });
      message.success("类型更新成功");
      setEditModalOpen(false);
      setEditingType(null);
      editForm.resetFields();
      actionRef.current?.reload();
      // 如果 Drawer 打开着该类型，同步更新标题
      if (drawerType?.typeCode === editingType.typeCode) {
        setDrawerType((prev) =>
          prev ? { ...prev, typeName: v.typeName, remark: v.remark || "" } : prev
        );
      }
    } catch {
      // 拦截器已处理
    }
  };

  // 停用/启用类型
  const onToggleTypeStatus = async (typeCode: string, currentStatus: number) => {
    const newStatus = currentStatus === 1 ? 0 : 1;
    try {
      await dictApi.updateType(typeCode, { status: newStatus });
      message.success(newStatus === 1 ? "已启用" : "已停用");
      actionRef.current?.reload();
    } catch {
      // 拦截器已处理
    }
  };

  // 新建字典项
  const onCreateItem = async () => {
    const v = await itemForm.validateFields();
    try {
      await dictApi.create({
        dictType: drawerType!.typeCode,
        dictKey: v.dictKey,
        dictLabel: v.dictLabel,
        sortOrder: v.sortOrder || 0,
        status: 1,
      });
      message.success("字典项创建成功");
      setItemModalOpen(false);
      itemForm.resetFields();
      loadItems(drawerType!.typeCode);
      actionRef.current?.reload(); // 刷新 enabledCount
    } catch {
      // 拦截器已处理
    }
  };

  // 类型列表表格列(关键字为纯筛选项,不入表格)
  const typeColumns: ProColumns<DictTypeItem>[] = [
    {
      title: "关键字",
      dataIndex: "keyword",
      hideInTable: true,
      fieldProps: { placeholder: "编码/名称", allowClear: true },
    },
    {
      title: "类型编码",
      dataIndex: "typeCode",
      key: "typeCode",
      search: false,
      render: (_v, record) => (
        <Typography.Text code style={{ fontSize: 13 }}>
          {record.typeCode}
        </Typography.Text>
      ),
    },
    {
      title: "类型名称",
      dataIndex: "typeName",
      key: "typeName",
      search: false,
    },
    {
      title: "备注",
      dataIndex: "remark",
      key: "remark",
      search: false,
      render: (_v, record) => record.remark || <span style={{ color: "#ccc" }}>-</span>,
    },
    {
      title: "启用项数",
      dataIndex: "enabledCount",
      key: "enabledCount",
      width: 100,
      search: false,
      render: (_v, record) => (
        <Badge count={record.enabledCount} style={{ backgroundColor: "#52c41a" }} showZero />
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 80,
      search: false,
      render: (_v, record) =>
        record.status === 1 ? (
          <Tag color="success">启用</Tag>
        ) : (
          <Tag color="default">停用</Tag>
        ),
    },
    ...(isAdmin
      ? [
          {
            title: "操作",
            key: "action",
            width: 220,
            search: false,
            render: (_: unknown, record: DictTypeItem) => (
              <Space size="small">
                <Button
                  type="link"
                  size="small"
                  icon={<ToolOutlined />}
                  onClick={() => openDrawer(record)}
                >
                  管理项
                </Button>
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openEditModal(record)}
                >
                  编辑
                </Button>
                {record.status === 1 ? (
                  <Popconfirm
                    title="确认停用该类型?"
                    onConfirm={() => onToggleTypeStatus(record.typeCode, record.status)}
                    okText="停用"
                    cancelText="取消"
                  >
                    <Button type="link" size="small" danger>
                      停用
                    </Button>
                  </Popconfirm>
                ) : (
                  <Popconfirm
                    title="确认启用该类型?"
                    onConfirm={() => onToggleTypeStatus(record.typeCode, record.status)}
                    okText="启用"
                    cancelText="取消"
                  >
                    <Button type="link" size="small">
                      启用
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]
      : []),
  ];

  return (
    <>
      <ProTable<DictTypeItem>
        rowKey="typeCode"
        actionRef={actionRef}
        columns={typeColumns}
        request={request}
        headerTitle={false}
        options={false}
        size="middle"
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建类型按钮放筛选行右侧(替代默认工具栏行,仅 admin)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            isAdmin && (
              <Button
                key="new"
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  typeForm.resetFields();
                  setTypeModalOpen(true);
                }}
              >
                新建
              </Button>
            ),
          ],
        }}
        pagination={false}
      />

      {/* 新建类型弹窗 */}
      <Modal
        title="新建字典类型"
        open={typeModalOpen}
        onOk={onCreateType}
        onCancel={() => {
          setTypeModalOpen(false);
          typeForm.resetFields();
        }}
        width={420}
      >
        <Form form={typeForm} layout="vertical" requiredMark={false}>
          <Form.Item
            label="类型编码"
            name="typeCode"
            rules={[
              { required: true, message: "类型编码必填" },
              {
                pattern: /^[a-z][a-z0-9_]*$/,
                message: "只能含小写字母、数字、下划线,且以字母开头",
              },
            ]}
          >
            <Input placeholder="如 warehouse_type" />
          </Form.Item>
          <Form.Item
            label="类型名称"
            name="typeName"
            rules={[{ required: true, message: "类型名称必填" }]}
          >
            <Input placeholder="如 仓库类型" />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑类型弹窗 */}
      <Modal
        title="编辑字典类型"
        open={editModalOpen}
        onOk={onSaveEditType}
        onCancel={() => {
          setEditModalOpen(false);
          setEditingType(null);
          editForm.resetFields();
        }}
        width={420}
      >
        <Form form={editForm} layout="vertical" requiredMark={false}>
          <Form.Item
            label="类型名称"
            name="typeName"
            rules={[{ required: true, message: "类型名称必填" }]}
          >
            <Input placeholder="如 仓库类型" />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Drawer: 字典项管理 */}
      <Drawer
        title={drawerType ? `${drawerType.typeName} · 字典项` : "字典项"}
        open={drawerOpen}
        onClose={closeDrawer}
        width={720}
        destroyOnClose
        extra={
          isAdmin && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                itemForm.resetFields();
                itemForm.setFieldsValue({ sortOrder: 0 });
                setItemModalOpen(true);
              }}
            >
              新增项
            </Button>
          )
        }
      >
        {/* 类型备注 */}
        {drawerType?.remark && (
          <div style={{ marginBottom: 12, color: "#999", fontSize: 13 }}>
            {drawerType.remark}
          </div>
        )}

        {/* 字典项表格 */}
        <div className="table-card">
          <Table
            rowKey="id"
            dataSource={items}
            loading={itemsLoading}
            pagination={false}
            size="small"
            columns={[
              {
                title: "中文标签",
                dataIndex: "dictLabel",
                key: "dictLabel",
                render: (_: unknown, record: DictItem) => (
                  <DictLabelCell item={record} onReload={() => loadItems(drawerType!.typeCode)} />
                ),
              },
              {
                title: "编码",
                dataIndex: "dictKey",
                key: "dictKey",
                render: (text: string) => (
                  <Typography.Text code style={{ fontSize: 12, color: "#999" }}>
                    {text}
                  </Typography.Text>
                ),
              },
              {
                title: "排序",
                dataIndex: "sortOrder",
                key: "sortOrder",
                width: 80,
                render: (_: unknown, record: DictItem) => (
                  <DictSortCell item={record} onReload={() => loadItems(drawerType!.typeCode)} />
                ),
              },
              {
                title: "状态",
                key: "status",
                width: 80,
                render: (_: unknown, record: DictItem) => (
                  <Switch
                    checked={record.status === 1}
                    onChange={async () => {
                      const newStatus = record.status === 1 ? 0 : 1;
                      try {
                        await dictApi.setStatus(record.id, newStatus);
                        message.success(newStatus === 1 ? "已启用" : "已停用");
                        loadItems(drawerType!.typeCode);
                        actionRef.current?.reload();
                      } catch {
                        // 拦截器已处理
                      }
                    }}
                    size="small"
                  />
                ),
              },
            ]}
            rowClassName={(record) => (record.status === 0 ? "dict-row-disabled" : "")}
          />
      </div>

        {/* 新建字典项弹窗 */}
        <Modal
          title="新增字典项"
          open={itemModalOpen}
          onOk={onCreateItem}
          onCancel={() => {
            setItemModalOpen(false);
            itemForm.resetFields();
          }}
          width={420}
        >
          <Form form={itemForm} layout="vertical" requiredMark={false}>
            <Form.Item
              label="编码"
              name="dictKey"
              rules={[{ required: true, message: "编码必填" }]}
            >
              <Input placeholder="如 raw / cold" />
            </Form.Item>
            <Form.Item
              label="中文标签"
              name="dictLabel"
              rules={[{ required: true, message: "标签必填" }]}
            >
              <Input placeholder="如 原材料仓" />
            </Form.Item>
            <Form.Item label="排序" name="sortOrder">
              <InputNumber min={0} style={{ width: "100%" }} />
            </Form.Item>
          </Form>
        </Modal>
      </Drawer>

      {/* 全局样式:停用行半透明 */}
      <style>{`
        .dict-row-disabled td { opacity: 0.5; }
      `}</style>
    </>
  );
}

/** Drawer 内:中文标签(行内编辑) */
function DictLabelCell({
  item,
  onReload,
}: {
  item: DictItem;
  onReload: () => void;
}) {
  const { message } = App.useApp();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(item.dictLabel);

  const save = async () => {
    if (value === item.dictLabel) {
      setEditing(false);
      return;
    }
    try {
      await dictApi.update(item.id, { dictLabel: value });
      message.success("标签更新成功");
      setEditing(false);
      onReload();
    } catch {
      message.error("标签更新失败");
      setValue(item.dictLabel);
      setEditing(false);
    }
  };

  if (editing) {
    return (
      <Input
        size="small"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onPressEnter={save}
        onBlur={save}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            setValue(item.dictLabel);
            setEditing(false);
          }
        }}
        autoFocus
        style={{ width: 120 }}
      />
    );
  }

  return (
    <span
      style={{
        cursor: "pointer",
        color: item.status === 0 ? "#999" : undefined,
        textDecoration: item.status === 0 ? "line-through" : undefined,
      }}
      onClick={() => setEditing(true)}
    >
      {item.dictLabel}
    </span>
  );
}

/** Drawer 内:排序(行内编辑) */
function DictSortCell({
  item,
  onReload,
}: {
  item: DictItem;
  onReload: () => void;
}) {
  const { message } = App.useApp();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(item.sortOrder);

  const save = async () => {
    if (value === item.sortOrder) {
      setEditing(false);
      return;
    }
    try {
      await dictApi.update(item.id, { sortOrder: value });
      message.success("排序更新成功");
      setEditing(false);
      onReload();
    } catch {
      message.error("排序更新失败");
      setValue(item.sortOrder);
      setEditing(false);
    }
  };

  if (editing) {
    return (
      <InputNumber
        size="small"
        value={value}
        onChange={(v) => setValue(v ?? 0)}
        onPressEnter={save}
        onBlur={save}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            setValue(item.sortOrder);
            setEditing(false);
          }
        }}
        autoFocus
        min={0}
        style={{ width: 60 }}
      />
    );
  }

  return (
    <span
      style={{ cursor: "pointer", color: "#666" }}
      onClick={() => setEditing(true)}
    >
      {item.sortOrder}
    </span>
  );
}
