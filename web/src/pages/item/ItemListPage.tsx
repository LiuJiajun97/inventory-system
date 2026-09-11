// 物品列表 + 新建/编辑抽屉(SPEC-WEB V2 2.9)
// 属性 KV 动态行:加行/删行,JSON 自动拼

import { useEffect, useState } from "react";
import {
  Form,
  Input,
  InputNumber,
  Select,
  Button,
  Table,
  Drawer,
  Space,
  App,
  Tooltip,
} from "antd";
import { PlusOutlined, DeleteOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { itemApi, dictApi } from "../../api";
import type { Item } from "../../types";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";

interface AttrRow {
  key: string;
  value: string;
}

export function ItemListPage() {
  const [rows, setRows] = useState<Item[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [attrs, setAttrs] = useState<AttrRow[]>([{ key: "", value: "" }]);
  const [categoryOptions, setCategoryOptions] = useState<Array<{ code: string; label: string }>>([]);
  const [form] = Form.useForm();
  const user = getUser();
  const { message } = App.useApp();

  const load = async (kw?: string, pg = 1, ps = pageSize) => {
    setLoading(true);
    try {
      const res = await itemApi.list({ keyword: kw, page: pg, pageSize: ps });
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
    dictApi.getType("itemCategory").then(setCategoryOptions).catch(() => undefined);
  }, []);

  const columns: ColumnsType<Item> = [
    { title: "编码", dataIndex: "itemCode", width: 180 },
    { title: "名称", dataIndex: "itemName", width: 180 },
    { title: "单位", dataIndex: "unit", width: 80 },
    { title: "规格", dataIndex: "spec", width: 140, ellipsis: true },
    {
      title: "分类",
      dataIndex: "category",
      width: 90,
      render: (v?: string | null) => categoryOptions.find((d) => d.code === v)?.label ?? v ?? "-",
    },
    {
      title: "最低库存",
      dataIndex: "minStock",
      width: 90,
      align: "right",
      className: "num-cell",
      render: (v?: string | number | null) => (v == null ? "-" : String(v)),
    },
    {
      title: "默认税率(%)",
      dataIndex: "defaultTaxRate",
      width: 100,
      align: "right",
      className: "num-cell",
      render: (v?: string | number | null) =>
        v == null ? "-" : Number(v).toFixed(2),
    },
    {
      title: "扩展属性",
      dataIndex: "attributes",
      width: 240,
      render: (v?: string | null) => {
        if (!v) return "-";
        const display = v.length > 40 ? v.slice(0, 40) + "..." : v;
        return (
          <Tooltip title={v} placement="topLeft">
            <code
              style={{
                fontSize: 12,
                background: "#f8fafc",
                padding: "2px 6px",
                borderRadius: 4,
              }}
            >
              {display}
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
      render: (v?: string | null) => v ?? "-",
    },
    ...(user?.role === "admin"
      ? [
          {
            title: "操作",
            width: 80,
            render: (_v: unknown, r: Item) => (
              <Button
                type="link"
                size="small"
                onClick={() => openEdit(r)}
              >
                编辑
              </Button>
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
      load(keyword, page, pageSize);
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

  const filterNode = (
    <Space>
      <Input.Search
        placeholder="搜索编码或名称"
        allowClear
        style={{ width: 280 }}
        onSearch={(v) => {
          setKeyword(v);
          load(v, 1, pageSize);
        }}
      />
    </Space>
  );

  return (
    <>
      <ListPageShell
        title="物品管理"
        extra={
          user?.role === "admin" && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
            >
              新建物品
            </Button>
          )
        }
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
            onChange: (p, ps) => load(keyword, p, ps),
            showTotal: (t) => `共 ${t} 条`,
          },
        }}
      />

      <Drawer
        title={editing ? "编辑物品" : "新建物品"}
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
          <Form.Item
            label="编码"
            name="itemCode"
            rules={[{ required: true, message: "编码必填" }]}
          >
            <Input placeholder="如 HW-SCREW-M8" disabled={editing != null} />
          </Form.Item>
          <Form.Item
            label="名称"
            name="itemName"
            rules={[{ required: true, message: "名称必填" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="单位"
            name="unit"
            rules={[{ required: true, message: "单位必填" }]}
          >
            <Input placeholder="如 支 / 套 / 公斤" />
          </Form.Item>
          <Form.Item label="规格" name="spec">
            <Input />
          </Form.Item>
          <Form.Item label="分类" name="category">
            <Select
              allowClear
              placeholder="选择分类"
              options={categoryOptions.map((d) => ({ label: d.label, value: d.code }))}
            />
          </Form.Item>
          <Form.Item label="最低库存(预警阈值,留空不预警)" name="minStock">
            <InputNumber min={0} step={1} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="默认税率(%)" name="defaultTaxRate" initialValue={13}>
            <InputNumber min={0} max={100} step={0.01} style={{ width: "100%" }} />
          </Form.Item>

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
        </Form>
      </Drawer>
    </>
  );
}