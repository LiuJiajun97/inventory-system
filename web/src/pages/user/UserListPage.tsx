// 用户管理(SPEC-WEB V2 2.10)
// ProTable 版:筛选字段由 columns 配置驱动(关键字),新建按钮经 optionRender 放筛选行右侧
// 编辑 Drawer(角色 Select、状态 Switch、重置密码按钮+二次确认 Modal,显示新密码一次性)

import { useRef, useState } from "react";
import {
  Form,
  Input,
  Select,
  Button,
  Drawer,
  Modal,
  Switch,
  Space,
  App,
  Alert,
} from "antd";
import {
  PlusOutlined,
  KeyOutlined,
  CopyOutlined,
} from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { userApi } from "../../api";
import type { UserInfo } from "../../types";
import { fmtDateTime } from "../../utils/format";
import { RoleTag, StatusTag } from "../../components/StatusTag";

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

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    keyword?: string;
  }) => {
    const res = await userApi.list({
      keyword: params.keyword || undefined,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

  const columns: ProColumns<UserInfo>[] = [
    { title: "ID", dataIndex: "id", width: 60, search: false },
    { title: "用户名", dataIndex: "username", width: 140, search: false },
    { title: "姓名", dataIndex: "name", width: 140, search: false },
    {
      title: "角色",
      dataIndex: "role",
      width: 100,
      search: false,
      render: (_v, r) => <RoleTag role={r.role} />,
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
            editForm.setFieldsValue({
              name: r.name,
              role: r.role,
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
        role: v.role,
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

      <Modal
        title="新建用户"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={onCreate}
        okText="提交"
        cancelText="取消"
      >
        <Form form={createForm} layout="vertical" requiredMark={false}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, min: 2, message: "用户名至少 2 位" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="姓名"
            name="name"
            rules={[{ required: true, message: "姓名必填" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, min: 6, message: "密码至少 6 位" }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            label="角色"
            name="role"
            rules={[{ required: true, message: "角色必填" }]}
          >
            <Select
              options={[
                { label: "管理员 admin", value: "admin" },
                { label: "库员 operator", value: "operator" },
                { label: "查看 viewer", value: "viewer" },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`编辑用户 - ${editTarget?.username ?? ""}`}
        open={!!editTarget}
        onClose={() => setEditTarget(null)}
        width={420}
        extra={
          <Space>
            <Button onClick={() => setEditTarget(null)}>取消</Button>
            <Button type="primary" onClick={onUpdate}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={editForm} layout="vertical" requiredMark={false}>
          <Form.Item label="姓名" name="name">
            <Input />
          </Form.Item>
          <Form.Item label="角色" name="role">
            <Select
              options={[
                { label: "管理员 admin", value: "admin" },
                { label: "库员 operator", value: "operator" },
                { label: "查看 viewer", value: "viewer" },
              ]}
            />
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
          <Form.Item>
            <Button
              danger
              icon={<KeyOutlined />}
              onClick={() => setResetTarget(editTarget)}
            >
              重置密码
            </Button>
          </Form.Item>
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