import React from "react";
import ReactDOM from "react-dom/client";
import dayjs from "dayjs";
import "dayjs/locale/zh-cn";
import App from "./App";
import "antd/dist/reset.css";
import "./styles/global.css";

// 日历组件(星期头/月份名)的文案来自 dayjs 全局 locale,必须显式设为中文,
// antd ConfigProvider 的 locale 只覆盖按钮类文案,不覆盖日历头
dayjs.locale("zh-cn");

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);