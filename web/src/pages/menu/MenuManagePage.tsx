// 菜单管理(RBAC 批 2)
// 可展开 Table 渲染三型合一菜单树(目录/菜单/按钮),Drawer 表单新建/编辑
// 编码(menu_code)与类型(type)编辑时只读(防断权限引用);删除走 API(后端已有子节点禁删保护)+ Popconfirm
// 按钮显隐权限码以 V8 seed 为准:menu:create / menu:edit / menu:delete
import { useCallback, useEffect, useState } from "react";
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Radio,
  Space,
  Switch,
  Table,
  Tag,
  TreeSelect,
  App,
  theme,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined } from "@ant-design/icons";
import { menuApi } from "../../api";
import type { ManageMenuNode } from "../../api";
import { usePermission } from "../../auth/usePermission";

// 类型 Tag 映射
const TYPE_TAG: Record<ManageMenuNode["type"], { text: string; color: string }> = {
  directory: { text: "目录", color: "geekblue" },
  menu: { text: "菜单", color: "blue" },
  button: { text: "按钮", color: "orange" },
};

// 菜单树转 TreeSelect 数据(父级选择用,含顶级"顶层"节点)
interface SelectTreeNode {
  title: string;
  value: number;
  children?: SelectTreeNode[];
}

function toSelectData(nodes: ManageMenuNode[]): SelectTreeNode[] {
  return nodes.map((n) => ({
    title: n.menuName,
    value: n.id,
    children: n.children.length > 0 ? toSelectData(n.children) : undefined,
  }));
}

export function MenuManagePage() {
  const { hasPerm } = usePermission();
  const [tree, setTree] = useState<ManageMenuNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  // 编辑目标(null = 新建;parentForCreate 非空 = 新建子级)
  const [editTarget, setEditTarget] = useState<ManageMenuNode | null>(null);
  const [parentForCreate, setParentForCreate] = useState<number>(0);
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const { token } = theme.useToken();

  const load = useCallback(() => {
    setLoading(true);
    menuApi
      .tree()
      .then(setTree)
      .catch(() => {
        // 拦截器已处理
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = (parentId: number) => {
    setEditTarget(null);
    setParentForCreate(parentId);
    form.resetFields();
    form.setFieldsValue({ parentId, type: "menu", sort: 0 });
    setFormOpen(true);
  };

  const openEdit = (n: ManageMenuNode) => {
    setEditTarget(n);
    form.resetFields();
    form.setFieldsValue({
      parentId: n.parentId,
      menuName: n.menuName,
      menuCode: n.menuCode,
      type: n.type,
      path: n.path ?? undefined,
      sort: n.sort,
      status: n.status === 1,
    });
    setFormOpen(true);
  };

  const onSubmit = async () => {
    const v = await form.validateFields();
    try {
      if (editTarget) {
        // 更新:编码/类型/父级中编码禁改,不提交
        await menuApi.update(editTarget.id, {
          parentId: v.parentId as number,
          menuName: v.menuName as string,
          path:
            (v.type as ManageMenuNode["type"]) === "button"
              ? undefined
              : ((v.path as string) || null),
          sort: v.sort as number,
          status: v.status ? 1 : 0,
        });
        message.success("菜单已更新");
      } else {
        await menuApi.create({
          parentId: v.parentId as number,
          menuCode: v.menuCode as string,
          menuName: v.menuName as string,
          type: v.type as ManageMenuNode["type"],
          path:
            (v.type as ManageMenuNode["type"]) === "button"
              ? undefined
              : ((v.path as string) || null),
          sort: v.sort as number,
        });
        message.success("菜单已创建");
      }
      setFormOpen(false);
      load();
    } catch {
      // 拦截器已处理
    }
  };

  const onDelete = async (n: ManageMenuNode) => {
    try {
      await menuApi.delete(n.id);
      message.success("菜单已删除");
      load();
    } catch {
      // 拦截器已处理
    }
  };

  const columns: ColumnsType<ManageMenuNode> = [
    {
      title: "菜单名称",
      dataIndex: "menuName",
      width: 220,
    },
    { title: "编码", dataIndex: "menuCode", width: 200 },
    {
      title: "类型",
      dataIndex: "type",
      width: 80,
      render: (t: ManageMenuNode["type"]) => (
        <Tag color={TYPE_TAG[t].color}>{TYPE_TAG[t].text}</Tag>
      ),
    },
    {
      title: "路由",
      dataIndex: "path",
      width: 160,
      render: (p: string | null) => p ?? "-",
    },
    { title: "排序", dataIndex: "sort", width: 70 },
    {
      title: "状态",
      dataIndex: "status",
      width: 70,
      render: (s: number) => (s === 1 ? "启用" : "停用"),
    },
    {
      title: "操作",
      width: 180,
      render: (_v, n) => (
        <Space>
          {hasPerm("menu:edit") && (
            <a onClick={() => openEdit(n)}>编辑</a>
          )}
          {hasPerm("menu:create") && n.type !== "button" && (
            <a onClick={() => openCreate(n.id)}>新建子级</a>
          )}
          {hasPerm("menu:delete") && (
            <Popconfirm
              title="确认删除该菜单?"
              description="有子节点的菜单不可删除"
              onConfirm={() => onDelete(n)}
            >
              <a style={{ color: token.colorError }}>删除</a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  // 当前表单类型(控制 path 字段显隐)
  const formType = Form.useWatch("type", form) ?? "menu";

  return (
    <>
      <Table<ManageMenuNode>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={tree}
        columns={columns}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
        title={() => (
          <span>
            菜单树
            {hasPerm("menu:create") && (
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                style={{ marginLeft: 12 }}
                onClick={() => openCreate(0)}
              >
                新建
              </Button>
            )}
          </span>
        )}
      />

      {/* 新建/编辑 Drawer(编码与类型编辑时只读,防断权限引用) */}
      <Drawer
        title={
          editTarget
            ? `编辑菜单 - ${editTarget.menuName}`
            : parentForCreate === 0
              ? "新建顶级菜单"
              : "新建子级菜单"
        }
        open={formOpen}
        onClose={() => setFormOpen(false)}
        width={480}
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setFormOpen(false)}>取消</Button>
              <Button type="primary" onClick={onSubmit}>
                提交
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item
            label="父级"
            name="parentId"
            rules={[{ required: true, message: "请选择父级" }]}
          >
            <TreeSelect
              treeDefaultExpandAll
              disabled={!!editTarget}
              treeData={[
                { title: "顶层", value: 0, children: toSelectData(tree) },
              ]}
            />
          </Form.Item>
          <Form.Item
            label="菜单名称"
            name="menuName"
            rules={[{ required: true, message: "菜单名称必填" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="菜单编码"
            name="menuCode"
            tooltip="唯一;按钮型节点该编码即权限码(如 role:create)"
            rules={[
              { required: true, message: "菜单编码必填" },
              {
                pattern: /^[a-z][a-z0-9:_-]*$/,
                message: "小写字母开头,仅小写字母/数字/冒号/下划线/中划线",
              },
            ]}
          >
            <Input placeholder="编辑时不可改" disabled={!!editTarget} />
          </Form.Item>
          <Form.Item
            label="类型"
            name="type"
            rules={[{ required: true, message: "请选择类型" }]}
          >
            <Radio.Group disabled={!!editTarget}>
              <Radio value="directory">目录</Radio>
              <Radio value="menu">菜单</Radio>
              <Radio value="button">按钮</Radio>
            </Radio.Group>
          </Form.Item>
          {formType !== "button" && (
            <Form.Item
              label="前端路由"
              name="path"
              tooltip="目录/菜单有路由,按钮留空"
            >
              <Input placeholder="如 /menus" />
            </Form.Item>
          )}
          <Form.Item label="排序" name="sort">
            <InputNumber min={0} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item
            label="状态"
            name="status"
            valuePropName="checked"
            getValueFromEvent={(v) => (v ? 1 : 0)}
            getValueProps={(v) => ({ checked: v === 1 })}
          >
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
}
