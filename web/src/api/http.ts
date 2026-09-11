// axios 实例:带 token、401 跳登录、统一错误提示
import axios from "axios";
import { message } from "antd";

export const http = axios.create({
  baseURL: "/api/v1",
  timeout: 30000,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers = config.headers ?? {};
    config.headers["Authorization"] = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (resp) => resp.data,
  (err) => {
    const status = err?.response?.status;
    const data = err?.response?.data;
    if (status === 401) {
      // 未登录 / token 失效
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      if (location.pathname !== "/login") {
        message.error(data?.error ?? "未登录,请重新登录");
        location.href = "/login";
      }
    } else if (status === 403) {
      message.error(data?.error ?? "无权限");
    } else if (data?.error) {
      message.error(data.error);
    } else {
      message.error(err?.message ?? "网络异常");
    }
    return Promise.reject(err);
  },
);
