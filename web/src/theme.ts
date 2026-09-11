// 全局设计令牌(SPEC-WEB V2 1.2)
// 唯一来源:所有页面通过 ConfigProvider 注入

import type { ThemeConfig } from "antd";

export const themeConfig: ThemeConfig = {
  token: {
    colorPrimary: "#3056d3",
    colorInfo: "#3056d3",
    colorSuccess: "#16a34a",
    colorWarning: "#d97706",
    colorError: "#dc2626",
    colorTextBase: "#1f2937",
    colorBgLayout: "#f5f6fa",
    borderRadius: 10,
    fontSize: 14,
    fontFamily:
      "-apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
  },
  components: {
    Layout: {
      headerBg: "#ffffff",
      siderBg: "#ffffff",
      bodyBg: "#f5f6fa",
    },
    Menu: {
      itemSelectedBg: "#eef2ff",
      itemSelectedColor: "#3056d3",
      itemColor: "#374151",
      itemBorderRadius: 8,
    },
    Table: {
      headerBg: "#f8fafc",
      rowHoverBg: "#f5f7fa",
      headerSplitColor: "transparent",
      borderRadius: 10,
    },
    Button: {
      primaryShadow: "0 2px 8px rgba(48,86,211,.35)",
      defaultShadow: "none",
      borderRadius: 8,
    },
    Card: {
      paddingLG: 20,
      borderRadiusLG: 10,
    },
  },
};