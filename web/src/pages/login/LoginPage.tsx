// 登录页(SPEC-WEB V2 2.1)
// 全屏浅灰底 + 居中 380 白卡 + 深蓝 logo + 标题 + 副标题 + 前缀图标表单 + 底部版权

import { useState } from "react";
import { Form, Input, Button, App } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { authApi } from "../../api";
import { setAuth } from "../../auth/useAuth";

export function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { message } = App.useApp();

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await authApi.login(values.username, values.password);
      setAuth(res.token, res.user);
      message.success(`欢迎,${res.user.name}`);
      navigate("/", { replace: true });
    } catch {
      // 错误已被 axios 拦截器统一提示
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-bg">
      <div className="login-card">
        <div style={{ textAlign: "center" }}>
          <span className="brand-logo brand-logo-lg">库</span>
        </div>
        <h1 className="login-title" style={{ textAlign: "center" }}>
          库存管理系统
        </h1>
        <p className="login-subtitle" style={{ textAlign: "center" }}>
          前后端分离 · 多仓库统一管控
        </p>
        <Form layout="vertical" onFinish={onFinish} autoComplete="off" requiredMark={false}>
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: "#9ca3af" }} />}
              placeholder="请输入用户名"
              size="large"
              autoFocus
            />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: "请输入密码" }]}
            style={{ marginBottom: 8 }}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: "#9ca3af" }} />}
              placeholder="请输入密码"
              size="large"
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, marginTop: 16 }}>
            <Button type="primary" htmlType="submit" loading={loading} size="large" block>
              登录
            </Button>
          </Form.Item>
        </Form>
        <div className="login-footer">© 2026 库存管理系统</div>
      </div>
    </div>
  );
}