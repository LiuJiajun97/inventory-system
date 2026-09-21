// 角色权限管理(RBAC 批 2)
// 布局:左角色列表(内置角色禁删/禁改编码,只能改备注/权限) + 右权限树
// 权限树数据 = GET /menus/tree 三型合一目录树,回写 PUT /roles/{id}/menus,回显 GET /roles/{id}/menus
// 按钮显隐权限码以 V8 seed 为准:role:create / role:edit / role:delete / role:assign
import { useCallback, useEffect, useState } from "react";
import {
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  Tree,
  App,
  theme,
} from "antd";
import type { DataNode } from "antd/es/tree";
import { PlusOutlined } from "@ant-design/icons";
import { roleApi, menuApi } from "../../api";
import type { RoleRow, ManageMenuNode } from "../../api";
import { usePermission } from "../../auth/usePermission";

// /menus/tree 三型节点转 antd Tree 数据(按钮型节点带权限码提示)
function toTreeData(nodes: ManageMenuNode[]): DataNode[] {
  return nodes.map((n) => ({
    key: n.id,
    title:
      n.type === "button" ? `${n.menuName}(${n.menuCode})` : n.menuName,
    children: n.children.length > 0 ? toTreeData(n.children) : undefined,
  }));
}

export function RoleManagePage() {
  const { hasPerm } = usePermission();
  const [roles, setRoles] = useState<RoleRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  // 权限树(全量菜单树,一次拉取)
  const [treeData, setTreeData] = useState<DataNode[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  // 新建 / 编辑 Drawer
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<RoleRow | null>(null);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const { message } = App.useApp();
  const { token } = theme.useToken();

  const loadRoles = useCallback(() => {
    setLoading(true);
    roleApi
      .list()
      .then((list) => {
        setRoles(list);
        // 默认选中第一个(方便直接看权限树)
        setSelectedId((prev) =>
          prev != null && list.some((r) => r.id === prev)
            ? prev
            : (list[0]?.id ?? null),
        );
      })
      .catch(() => {
        // 拦截器已处理
      })
      .finally(() => setLoading(false));
  }, []);

  // 全量菜单树(一次拉取)
  useEffect(() => {
    menuApi
      .tree()
      .then((tree) => setTreeData(toTreeData(tree)))
      .catch(() => {
        // 拦截器已处理
      });
  }, []);

  useEffect(() => {
    loadRoles();
  }, [loadRoles]);

  // 选中角色变化 → 回显已绑定菜单
  useEffect(() => {
    if (selectedId == null) {
      setCheckedKeys([]);
      return;
    }
    setTreeLoading(true);
    roleApi
      .menuIds(selectedId)
      .then((ids) => setCheckedKeys(ids))
      .catch(() => {
        // 拦截器已处理
      })
      .finally(() => setTreeLoading(false));
  }, [selectedId]);

  const onAssign = async () => {
    if (selectedId == null) return;
    try {
      await roleApi.assignMenus(selectedId, checkedKeys.map(Number));
      message.success("权限分配已保存");
    } catch {
      // 拦截器已处理
    }
  };

  const onCreate = async () => {
    const v = await createForm.validateFields();
    try {
      await roleApi.create(v);
      message.success("角色创建成功");
      setCreateOpen(false);
      createForm.resetFields();
      loadRoles();
    } catch {
      // 拦截器已处理
    }
  };

  const onUpdate = async () => {
    if (!editTarget) return;
    const v = await editForm.validateFields();
    try {
      // 内置角色禁改编码:roleCode 只在非内置时提交
      await roleApi.update(editTarget.id, {
        roleName: v.roleName,
        roleCode: editTarget.isBuiltin ? undefined : v.roleCode,
        remark: v.remark ?? null,
      });
      message.success("更新成功");
      setEditTarget(null);
      loadRoles();
    } catch {
      // 拦截器已处理
    }
  };

  const onDelete = async (r: RoleRow) => {
    try {
      await roleApi.delete(r.id);
      message.success("角色已删除");
      loadRoles();
    } catch {
      // 拦截器已处理
    }
  };

  const columns = [
    {
      title: "角色名称",
      dataIndex: "roleName",
      width: 110,
      render: (_v: unknown, r: RoleRow) => (
        <a
          onClick={() => setSelectedId(r.id)}
          style={{ fontWeight: selectedId === r.id ? 600 : 400 }}
        >
          {r.roleName}
        </a>
      ),
    },
    { title: "编码", dataIndex: "roleCode", width: 100 },
    {
      title: "内置",
      dataIndex: "isBuiltin",
      width: 60,
      render: (v: boolean) => (v ? <Tag>内置</Tag> : "-"),
    },
    {
      title: "备注",
      dataIndex: "remark",
      ellipsis: true,
      render: (v: string | null) => v ?? "-",
    },
    {
      title: "操作",
      width: 110,
      render: (_v: unknown, r: RoleRow) => (
        <Space>
          {hasPerm("role:edit") && (
            <a
              onClick={() => {
                setEditTarget(r);
                editForm.setFieldsValue({
                  roleName: r.roleName,
                  roleCode: r.roleCode,
                  remark: r.remark ?? undefined,
                });
              }}
            >
              编辑
            </a>
          )}
          {!r.isBuiltin && hasPerm("role:delete") && (
            <Popconfirm
              title="确认删除该角色?"
              description="有用户绑定的角色不可删除"
              onConfirm={() => onDelete(r)}
            >
              <a style={{ color: token.colorError }}>删除</a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Row gutter={16}>
      <Col flex="380px">
        <Card
          size="small"
          title="角色列表"
          extra={
            hasPerm("role:create") ? (
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => setCreateOpen(true)}
              >
                新建
              </Button>
            ) : null
          }
        >
          <Table<RoleRow>
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={roles}
            columns={columns}
            pagination={false}
            rowClassName={(r) =>
              selectedId === r.id ? "ant-table-row-selected" : ""
            }
          />
        </Card>
      </Col>
      <Col flex="auto">
        <Card
          size="small"
          title={`权限树 - ${roles.find((r) => r.id === selectedId)?.roleName ?? ""}`}
          extra={
            hasPerm("role:assign") && selectedId != null ? (
              <Button type="primary" size="small" onClick={onAssign}>
                保存分配
              </Button>
            ) : null
          }
        >
          {treeData.length > 0 ? (
            <Spin spinning={treeLoading}>
              <Tree
                checkable
                checkedKeys={checkedKeys}
                onCheck={(keys) =>
                  setCheckedKeys(Array.isArray(keys) ? keys : keys.checked)
                }
                treeData={treeData}
              />
            </Spin>
          ) : (
            <span style={{ color: token.colorTextTertiary }}>
              菜单树加载中...
            </span>
          )}
        </Card>
      </Col>

      {/* 新建角色 Drawer */}
      <Drawer
        title="新建角色"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={480}
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setCreateOpen(false)}>取消</Button>
              <Button type="primary" onClick={onCreate}>
                提交
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={createForm} layout="vertical" requiredMark={false}>
          <Form.Item
            label="角色名称"
            name="roleName"
            rules={[{ required: true, message: "角色名称必填" }]}
          >
            <Input placeholder="如:仓库主管" />
          </Form.Item>
          <Form.Item
            label="角色编码"
            name="roleCode"
            rules={[
              { required: true, message: "角色编码必填" },
              {
                pattern: /^[a-z][a-z0-9_]*$/,
                message: "小写字母开头,仅小写字母/数字/下划线",
              },
            ]}
          >
            <Input placeholder="如:wh_manager" />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 编辑角色 Drawer(内置角色编码禁改) */}
      <Drawer
        title={`编辑角色 - ${editTarget?.roleName ?? ""}`}
        open={!!editTarget}
        onClose={() => setEditTarget(null)}
        width={480}
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setEditTarget(null)}>取消</Button>
              <Button type="primary" onClick={onUpdate}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={editForm} layout="vertical" requiredMark={false}>
          <Form.Item
            label="角色名称"
            name="roleName"
            rules={[{ required: true, message: "角色名称必填" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="角色编码"
            name="roleCode"
            rules={[{ required: true, message: "角色编码必填" }]}
          >
            <Input disabled={editTarget?.isBuiltin} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>
    </Row>
  );
}
