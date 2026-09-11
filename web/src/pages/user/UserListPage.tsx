// 用户管理(SPEC-WEB V2 2.10)
// 编辑 Drawer(角色 Select、状态 Switch、重置密码按钮+二次确认 Modal,显示新密码一次性)

import { useEffect, useState } from "react";
import {
  Form,
  Input,
  Select,
  Button,
  Table,
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
import type { ColumnsType } from "antd/es/table";
import { userApi } from "../../api";
import type { Role, UserInfo } from "../../types";
import { ListPageShell } from "../../components/ListPageShell";
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
  const [rows, setRows] = useState<UserInfo[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<UserInfo | null>(null);
  const [resetTarget, setResetTarget] = useState<UserInfo | null>(null);
  const [resetPwd, setResetPwd] = useState<string>("");
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const { message } = App.useApp();

  const load = async (pg = 1, ps = pageSize) => {
    setLoading(true);
    try {
      const res = await userApi.list({ page: pg, pageSize: ps });
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
  }, []);

  const columns: ColumnsType<UserInfo> = [
    { title: "ID", dataIndex: "id", width: 60 },
    { title: "用户名", dataIndex: "username", width: 140 },
    { title: "姓名", dataIndex: "name", width: 140 },
    {
      title: "角色",
      dataIndex: "role",
      width: 100,
      render: (v: Role) => <RoleTag role={v} />,
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
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 170,
      render: (v: string) =>
        fmtDateTime(v),
    },
    {
      title: "操作",
      width: 100,
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
      load(page, pageSize);
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
      load(page, pageSize);
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
      <ListPageShell
        title="用户管理"
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            新建用户
          </Button>
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