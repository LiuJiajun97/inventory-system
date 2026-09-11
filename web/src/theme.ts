// 全局设计令牌(SPEC-WEB V2 1.1)
// 唯一来源:所有页面通过 ConfigProvider 注入

import type { ThemeConfig } from "antd";

export const themeConfig: ThemeConfig = {
  token: {
    colorPrimary: "#1d4ed8",
    colorInfo: "#1d4ed8",
    colorSuccess: "#16a34a",
    colorWarning: "#d97706",
    colorError: "#dc2626",
    colorTextBase: "#1f2937",
    colorBgLayout: "#f3f5f9",
    borderRadius: 6,
    fontSize: 14,
    fontFamily:
      "-apple-system, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
  },
  components: {
    Layout: {
      headerBg: "#ffffff",
      siderBg: "#ffffff",
      bodyBg: "#f3f5f9",
    },
    Menu: {
      itemSelectedBg: "#eff4ff",
      itemSelectedColor: "#1d4ed8",
      itemColor: "#374151",
      itemBorderRadius: 6,
    },
    Table: {
      headerBg: "#f8fafc",
      rowHoverBg: "#f5f7fa",
      headerSplitColor: "transparent",
    },
    Button: {
      primaryShadow: "none",
      defaultShadow: "none",
    },
    Card: {
      paddingLG: 20,
    },
  },
};