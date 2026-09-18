// 全局消息桥:让 http 拦截器/下载工具等"非 React 组件上下文"也能复用
// App.useApp().message(带 ConfigProvider 主题),避免 antd 5 静态 message
// 丢失主题样式。顶层 MessageBridge 组件(须置于 <AntdApp> 内)用
// App.useApp() 注册实例;未注册前降级为 antd 静态 message(功能仍可用)。
import { App, message } from "antd";

type ApiMessage = ReturnType<typeof App.useApp>["message"];

let instance: ApiMessage | null = null;

/** 由 <AntdApp> 内的组件调用,注册带主题的 message 实例。 */
export function setApiMessage(mi: ApiMessage): void {
  instance = mi;
}

/** 非组件上下文(axios 拦截器、downloadBlob)统一弹 error 的出口。 */
export function apiMessageError(text: string): void {
  (instance ?? message).error(text);
}
