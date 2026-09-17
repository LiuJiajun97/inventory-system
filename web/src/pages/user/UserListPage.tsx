// 用户管理(SPEC-WEB V2 2.10)
// ProTable 版:筛选字段由 columns 配置驱动(关键字),新建按钮经 optionRender 放筛选行右侧
// 编辑 Drawer(多角色 Select(multiple)、仓库授权多选、状态 Switch、重置密码按钮+二次确认 Modal,显示新密码一次性)

import { useEffect, useRef, useState } from "react";
import {
  Form,
  Input,
  Select,
  Button,
  Drawer,
  Modal,
  Row,
  Col,
  Switch,
  Space,
  App,
  Alert,
  theme,
  Tag,
  Tooltip,
} from "antd";
import {
  PlusOutlined,
  KeyOutlined,
  CopyOutlined,
} from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { userApi, roleApi, warehouseApi } from "../../api";
import type { RoleRow } from "../../api";
import type { UserInfo, UserRoleItem } from "../../types";
import { fmtDateTime } from "../../utils/format";
import { StatusTag } from "../../components/StatusTag";
import { EmptyHint } from "../../components/EmptyHint";
import { proTableRequest } from "../../utils/proTable";

// 内置角色 Tag 样式(自定义角色用默认灰 Tag)
const BUILTIN_ROLE_CLASS: Record<string, string> = {
  admin: "tag-role-admin",
  operator: "tag-role-operator",
  viewer: "tag-role-viewer",
};

// 单个角色 Tag(多角色列表渲染用,优先后端下发的名称)
function RoleTagItem({ code, name }: { code: string; name: string }) {
  return (
    <Tag bordered className={BUILTIN_ROLE_CLASS[code] ?? undefined}>
      {name}
    </Tag>
  );
}

// VO 的角色列表(无角色时回退已废弃的单 role 字段,防旧数据)
function userRoleItems(r: UserInfo): UserRoleItem[] {
  if (r.roles && r.roles.length > 0) return r.roles;
  return [{ code: r.role, name: r.role }];
}

function genPassword(): string {
  // 8 位大小写+数字,易读(避免 0OIl)
  const up = "ABCDEFGHJKMNPQRSTUVWXYZ";
  const lo = "abcdefghjkmnpqrstuvwxyz";
  const num = "23456789";
  const pick = (s: string) => s[Math.floor(Math.random() * s.length)];
  let p = pick(up) + pick(lo) + pick(num) + pick(up) + pick(lo);
  const all = up + lo + num;
  while (p.length < 8) p += pick(all);
  return p
    .split("")
    .sort(() => Math.random() - 0.5)
    .join("");
}

export function UserListPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<UserInfo | null>(null);
  const [resetTarget, setResetTarget] = useState<UserInfo | null>(null);
  const [resetPwd, setResetPwd] = useState<string>("");
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const actionRef = useRef<ActionType>();
  const { message } = App.useApp();
  // 多角色选项(内置 + 自定义)与仓库授权选项(全量)
  const [roleOptions, setRoleOptions] = useState<RoleRow[]>([]);
  const [whOptions, setWhOptions] = useState<
    { label: string; value: number }[]
  >([]);
  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();

  // 拉角色列表与仓库列表(下拉选项)
  useEffect(() => {
    roleApi.list()
      .then(setRoleOptions)
      .catch(() => {
        // 拦截器已处理
      });
    warehouseApi
      .list({ pageSize: 200 })
      .then((res) =>
        setWhOptions(
          res.rows.map((w) => ({ label: w.warehouseName, value: w.id })),
        ),
      )
      .catch(() => {
        // 拦截器已处理
      });
  }, []);

  // 仓库 ID -> 名称(列表列渲染用)
  const whName = (id: number) =>
    whOptions.find((w) => w.value === id)?.label ?? `#${id}`;

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { keyword?: string }) => ({
      keyword: p.keyword || undefined,
    }),
    userApi.list,
  );

  const columns: ProColumns<UserInfo>[] = [
    { title: "ID", dataIndex: "id", width: 60, search: false },
    { title: "用户名", dataIndex: "username", width: 140, search: false },
    { title: "姓名", dataIndex: "name", width: 140, search: false },
    {
      title: "角色",
      dataIndex: "roles",
      width: 160,
      search: false,
      render: (_v, r) => (
        <Space size={[0, 4]} wrap>
          {userRoleItems(r).map((ri) => (
            <RoleTagItem key={ri.code} code={ri.code} name={ri.name} />
          ))}
        </Space>
      ),
    },
    {
      title: "仓库授权",
      dataIndex: "warehouseIds",
      width: 160,
      search: false,
      render: (_v, r) => {
        const ids = r.warehouseIds ?? [];
        if (ids.length === 0) return "-";
        const text = ids.map(whName).join(", ");
        return (
          <Tooltip title={text}>
            <span
              style={{
                display: "inline-block",
                maxWidth: 140,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                verticalAlign: "bottom",
              }}
            >
              {text}
            </span>
          </Tooltip>
        );
      },
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
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 170,
      search: false,
      render: (_v, r) =>
        r.createdAt ? fmtDateTime(r.createdAt) : "-",
    },
    {
      title: "操作",
      width: 100,
      search: false,
      render: (_v, r) => (
        <a
          onClick={() => {
            setEditTarget(r);
            // 回显:角色 ID 由 code 映射(VO roles 仅 code/name,选项来自 /roles)
            const roleIds = userRoleItems(r)
              .map((ri) => roleOptions.find((ro) => ro.roleCode === ri.code)?.id)
              .filter((id): id is number => id != null);
            editForm.setFieldsValue({
              name: r.name,
              roleIds,
              warehouseIds: r.warehouseIds ?? [],
              status: r.status === 1,
            });
          }}
        >
          编辑
        </a>
      ),
    },
  ];

  const onCreate = async () => {
    const v = await createForm.validateFields();
    try {
      await userApi.create(v);
      message.success("用户创建成功");
      setCreateOpen(false);
      createForm.resetFields();
      actionRef.current?.reload();
    } catch {
      // 拦截器已处理
    }
  };

  const onUpdate = async () => {
    if (!editTarget) return;
    const v = await editForm.validateFields();
    try {
      await userApi.update(editTarget.id, {
        name: v.name,
        roleIds: v.roleIds,
        warehouseIds: v.warehouseIds ?? [],
        status: v.status ? 1 : 0,
      });
      message.success("更新成功");
      setEditTarget(null);
      actionRef.current?.reload();
    } catch {
      // 拦截器已处理
    }
  };

  const onConfirmReset = async () => {
    if (!resetTarget) return;
    const newPwd = genPassword();
    try {
      await userApi.update(resetTarget.id, { password: newPwd });
      setResetPwd(newPwd);
      setResetTarget(null);
    } catch {
      // 拦截器已处理
    }
  };

  return (
    <>
      <ProTable<UserInfo>
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无用户" /> }}
        actionRef={actionRef}
        columns={[
          {
            title: "关键字",
            dataIndex: "keyword",
            hideInTable: true,
            fieldProps: { placeholder: "用户名/姓名", allowClear: true },
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
            <Button key="new" type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              新建
            </Button>,
          ],
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      {/* 新建用户抽屉(原 Modal 统一为 Drawer,宽度 480 档) */}
      <Drawer
        title="新建用户"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={480}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮"提交"(文案保持现状)
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
          {/* 表头字段两列对齐(统一规格:纯表单抽屉两列上限) */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="用户名"
                name="username"
                rules={[{ required: true, min: 2, message: "用户名至少 2 位" }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="姓名"
                name="name"
                rules={[{ required: true, message: "姓名必填" }]}
              >
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="密码"
                name="password"
                rules={[{ required: true, min: 6, message: "密码至少 6 位" }]}
              >
                <Input.Password />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="角色(可多选)"
                name="roleIds"
                rules={[{ required: true, message: "请至少选择一个角色" }]}
              >
                <Select mode="multiple" options={roleOptions.map((r) => ({
                  label: `${r.roleName} ${r.roleCode}`,
                  value: r.id,
                }))} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>

      <Drawer
        title={`编辑用户 - ${editTarget?.username ?? ""}`}
        open={!!editTarget}
        onClose={() => setEditTarget(null)}
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
              <Button onClick={() => setEditTarget(null)}>取消</Button>
              <Button type="primary" onClick={onUpdate}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={editForm} layout="vertical" requiredMark={false}>
          {/* 表头字段两列对齐(统一规格:纯表单抽屉两列上限) */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="姓名" name="name">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="角色(可多选)" name="roleIds">
                <Select mode="multiple" options={roleOptions.map((r) => ({
                  label: `${r.roleName} ${r.roleCode}`,
                  value: r.id,
                }))} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="仓库授权"
                name="warehouseIds"
                tooltip="不勾选任何仓库时保存将清空授权(该用户列表查空;admin 角色豁免)"
              >
                <Select
                  mode="multiple"
                  allowClear
                  placeholder="不授权任何仓库 = 列表查空"
                  options={whOptions}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="状态"
                name="status"
                valuePropName="checked"
                getValueFromEvent={(v) => (v ? 1 : 0)}
                getValueProps={(v) => ({ checked: v === 1 })}
              >
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
          </Row>
          {/* 操作类字段独占一行 */}
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item>
                <Button
                  danger
                  icon={<KeyOutlined />}
                  onClick={() => setResetTarget(editTarget)}
                >
                  重置密码
                </Button>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>

      <Modal
        title="确认重置密码"
        open={!!resetTarget}
        onCancel={() => setResetTarget(null)}
        onOk={onConfirmReset}
        okText="确认重置"
        cancelText="取消"
      >
        <p>
          将为用户 <b>{resetTarget?.name}</b>({resetTarget?.username}){" "}
          生成新的随机密码。该操作不可撤销。
        </p>
      </Modal>

      <Modal
        title="密码重置成功"
        open={!!resetPwd}
        onCancel={() => setResetPwd("")}
        footer={[
          <Button
            key="copy"
            icon={<CopyOutlined />}
            onClick={() => {
              navigator.clipboard?.writeText(resetPwd);
              message.success("已复制");
            }}
          >
            复制密码
          </Button>,
          <Button
            key="close"
            type="primary"
            onClick={() => setResetPwd("")}
          >
            我已记录,关闭
          </Button>,
        ]}
      >
        <Alert
          type="warning"
          showIcon
          message="新密码仅显示一次,请立即记录或复制"
          style={{ marginBottom: 12 }}
        />
        <Input
          value={resetPwd}
          readOnly
          style={{
            fontFamily: "monospace",
            fontSize: 16,
            textAlign: "center",
            letterSpacing: 2,
          }}
        />
      </Modal>
    </>
  );
}