// 物品列表 + 新建/编辑抽屉(SPEC-WEB V2 2.9)
// ProTable 版:筛选字段由 columns 配置驱动(关键字/分类),新建按钮经 optionRender 放筛选行右侧
// 属性 KV 动态行:加行/删行,JSON 自动拼

import { useEffect, useRef, useState } from "react";
import {
  Form,
  Input,
  InputNumber,
  Select,
  Button,
  Drawer,
  Row,
  Col,
  Space,
  App,
  Tooltip,
  theme,
} from "antd";
import { PlusOutlined, DeleteOutlined } from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { itemApi, dictApi } from "../../api";
import type { Item } from "../../types";
import { usePermission } from "../../auth/usePermission";

interface AttrRow {
  key: string;
  value: string;
}

export function ItemListPage() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [attrs, setAttrs] = useState<AttrRow[]>([{ key: "", value: "" }]);
  const [categoryOptions, setCategoryOptions] = useState<Array<{ code: string; label: string }>>([]);
  const [form] = Form.useForm();
  const actionRef = useRef<ActionType>();
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端拦截)
  const canEdit = hasPerm("item:edit");
  const canCreate = hasPerm("item:create");
  const { message } = App.useApp();
  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();

  // 分类下拉数据源(异步加载,仅用于筛选项与列展示)
  useEffect(() => {
    dictApi.getType("itemCategory").then(setCategoryOptions).catch(() => undefined);
  }, []);

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    keyword?: string;
    itemCategory?: string;
  }) => {
    const res = await itemApi.list({
      keyword: params.keyword || undefined,
      itemCategory: params.itemCategory || undefined,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

  const columns: ProColumns<Item>[] = [
    { title: "编码", dataIndex: "itemCode", width: 180, search: false },
    { title: "名称", dataIndex: "itemName", width: 180, search: false },
    { title: "单位", dataIndex: "unit", width: 80, search: false },
    { title: "规格", dataIndex: "spec", width: 140, ellipsis: true, search: false },
    {
      title: "分类",
      dataIndex: "category",
      width: 90,
      search: false,
      render: (_v, r) => categoryOptions.find((d) => d.code === r.category)?.label ?? r.category ?? "-",
    },
    {
      title: "最低库存",
      dataIndex: "minStock",
      width: 90,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) => (r.minStock == null ? "-" : String(r.minStock)),
    },
    {
      title: "默认税率(%)",
      dataIndex: "defaultTaxRate",
      width: 100,
      align: "right",
      className: "num-cell",
      search: false,
      render: (_v, r) =>
        r.defaultTaxRate == null ? "-" : Number(r.defaultTaxRate).toFixed(2),
    },
    {
      title: "扩展属性",
      dataIndex: "attributes",
      width: 240,
      ellipsis: true,
      search: false,
      render: (_v, r) => {
        const v = r.attributes;
        if (!v) return "-";
        return (
          <Tooltip title={v} placement="topLeft">
            <code
              style={{
                fontSize: 12,
                background: "#f8fafc",
                padding: "2px 6px",
                borderRadius: 4,
                whiteSpace: "nowrap",
              }}
            >
              {v}
            </code>
          </Tooltip>
        );
      },
    },
    {
      title: "创建人",
      dataIndex: "creator",
      width: 100,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.creator ?? "-",
    },
    ...(canEdit
      ? [
          {
            title: "操作",
            width: 80,
            search: false,
            render: (_v: unknown, r: Item) => (
              <a className="action-edit" onClick={() => openEdit(r)}>
                编辑
              </a>
            ),
          },
        ]
      : []),
  ];

  /** 把物品扩展属性 JSON 解析为 KV 行。 */
  const attrsFromItem = (raw?: string | null): AttrRow[] => {
    if (!raw) return [{ key: "", value: "" }];
    try {
      const obj = JSON.parse(raw) as Record<string, string>;
      const rows = Object.entries(obj).map(([key, value]) => ({ key, value: String(value) }));
      return rows.length > 0 ? rows : [{ key: "", value: "" }];
    } catch {
      return [{ key: "", value: "" }];
    }
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setAttrs([{ key: "", value: "" }]);
    setDrawerOpen(true);
  };

  const openEdit = (r: Item) => {
    setEditing(r);
    form.setFieldsValue({
      itemCode: r.itemCode,
      itemName: r.itemName,
      unit: r.unit,
      spec: r.spec,
      category: r.category,
      minStock: r.minStock == null ? undefined : Number(r.minStock),
      defaultTaxRate: r.defaultTaxRate == null ? undefined : Number(r.defaultTaxRate),
    });
    setAttrs(attrsFromItem(r.attributes));
    setDrawerOpen(true);
  };

  const onSave = async () => {
    const v = await form.validateFields();
    try {
      // 拼接属性 JSON
      const obj: Record<string, string> = {};
      for (const r of attrs) {
        if (r.key.trim()) obj[r.key.trim()] = r.value;
      }
      const attributes = Object.keys(obj).length > 0 ? JSON.stringify(obj) : undefined;
      if (editing) {
        await itemApi.update(editing.id, {
          itemName: v.itemName,
          unit: v.unit,
          spec: v.spec,
          attributes,
          category: v.category,
          minStock: v.minStock,
          defaultTaxRate: v.defaultTaxRate,
        });
        message.success("物品已更新");
      } else {
        await itemApi.create({
          itemCode: v.itemCode,
          itemName: v.itemName,
          unit: v.unit,
          spec: v.spec,
          attributes,
          category: v.category,
          minStock: v.minStock,
          defaultTaxRate: v.defaultTaxRate,
        });
        message.success("物品创建成功");
      }
      setDrawerOpen(false);
      form.resetFields();
      setAttrs([{ key: "", value: "" }]);
      setEditing(null);
      actionRef.current?.reload();
    } catch {
      // 拦截器已处理
    }
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    form.resetFields();
    setAttrs([{ key: "", value: "" }]);
    setEditing(null);
  };

  return (
    <>
      <ProTable<Item>
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
            title: "分类",
            dataIndex: "itemCategory",
            valueType: "select",
            hideInTable: true,
            fieldProps: {
              allowClear: true,
              placeholder: "全部",
              options: categoryOptions.map((d) => ({ label: d.label, value: d.code })),
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
        title={editing ? "编辑物品" : "新建物品"}
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
          {/* 表头字段两列对齐(统一规格:纯表单抽屉两列上限) */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="编码"
                name="itemCode"
                rules={[{ required: true, message: "编码必填" }]}
              >
                <Input placeholder="如 HW-SCREW-M8" disabled={editing != null} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="名称"
                name="itemName"
                rules={[{ required: true, message: "名称必填" }]}
              >
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="单位"
                name="unit"
                rules={[{ required: true, message: "单位必填" }]}
              >
                <Input placeholder="如 支 / 套 / 公斤" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="规格" name="spec">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="分类" name="category">
                <Select
                  allowClear
                  placeholder="选择分类"
                  options={categoryOptions.map((d) => ({ label: d.label, value: d.code }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="最低库存(预警阈值,留空不预警)" name="minStock">
                <InputNumber min={0} step={1} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="默认税率(%)" name="defaultTaxRate" initialValue={13}>
                <InputNumber min={0} max={100} step={0.01} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
          </Row>
          {/* 动态行区块独占一行 */}
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item label="扩展属性 (key-value 动态行)">
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {attrs.map((row, idx) => (
                <div
                  key={idx}
                  style={{ display: "flex", gap: 8, alignItems: "center" }}
                >
                  <Input
                    placeholder="key (如 material)"
                    style={{ flex: 1 }}
                    value={row.key}
                    onChange={(e) => {
                      const v = e.target.value;
                      setAttrs((prev) =>
                        prev.map((r, i) =>
                          i === idx ? { ...r, key: v } : r,
                        ),
                      );
                    }}
                  />
                  <Input
                    placeholder="value"
                    style={{ flex: 1 }}
                    value={row.value}
                    onChange={(e) => {
                      const v = e.target.value;
                      setAttrs((prev) =>
                        prev.map((r, i) =>
                          i === idx ? { ...r, value: v } : r,
                        ),
                      );
                    }}
                  />
                  <Button
                    danger
                    type="link"
                    icon={<DeleteOutlined />}
                    onClick={() =>
                      setAttrs((prev) =>
                        prev.length > 1
                          ? prev.filter((_, i) => i !== idx)
                          : prev,
                      )
                    }
                    disabled={attrs.length <= 1}
                  />
                </div>
              ))}
              <Button
                type="dashed"
                icon={<PlusOutlined />}
                onClick={() =>
                  setAttrs((prev) => [...prev, { key: "", value: "" }])
                }
                style={{ alignSelf: "flex-start" }}
              >
                添加属性
              </Button>
            </div>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
}