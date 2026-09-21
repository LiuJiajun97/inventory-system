// 导出按钮(V12):点击后 fetch 带 token 下载 xlsx blob 并触发浏览器保存
// url 由页面按当前筛选条件拼好(导出不分页,与列表筛选一致)

import { useState } from "react";
import { Button, App } from "antd";
import { DownloadOutlined } from "@ant-design/icons";
import { downloadBlob } from "../utils/downloadBlob";

interface ExportButtonProps {
  /** 导出端点(相对 /api/v1,含查询串,如 "/items/export?keyword=x") */
  url: string;
  /** 本地保存文件名(含扩展名,如 "物品.xlsx") */
  filename: string;
  /** 按钮文案,默认"导出" */
  label?: string;
}

export function ExportButton({ url, filename, label = "导出" }: ExportButtonProps) {
  const { message } = App.useApp();
  const [exporting, setExporting] = useState(false);

  const onExport = async () => {
    setExporting(true);
    try {
      await downloadBlob(`/inventory/api/v1${url}`, filename);
      message.success("导出完成");
    } catch (e) {
      message.error(e instanceof Error ? e.message : "导出失败");
    } finally {
      setExporting(false);
    }
  };

  return (
    <Button icon={<DownloadOutlined />} loading={exporting} onClick={onExport}>
      {label}
    </Button>
  );
}
