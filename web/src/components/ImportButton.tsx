// 导入按钮(V12):antd Upload 自定义请求 + 结果提示(message) + 失败行 Modal
// "模板"下载收进主按钮下拉菜单(单按钮,省筛选行宽度)。成功/失败均 HTTP 200,失败行汇总进 Modal。

import { useState } from "react";
import { Button, Dropdown, Modal, Space, Table, Upload, App } from "antd";
import { DownloadOutlined, DownOutlined, UploadOutlined } from "@ant-design/icons";
import type { UploadProps } from "antd";
import type { ImportFailedRow } from "../types/phase1";
import { downloadBlob } from "../utils/downloadBlob";

interface ImportButtonProps {
  /** 导入端点(相对 /api/v1,如 "/items/import") */
  importUrl: string;
  /** 模板端点(相对 /api/v1,如 "/items/template") */
  templateUrl: string;
  /** 本地模板文件名 */
  templateFilename: string;
  /** 导入完成后回调(刷新列表) */
  onDone?: () => void;
}

export function ImportButton({
  importUrl,
  templateUrl,
  templateFilename,
  onDone,
}: ImportButtonProps) {
  const { message } = App.useApp();
  const [failures, setFailures] = useState<ImportFailedRow[] | null>(null);
  const [uploading, setUploading] = useState(false);

  // 自定义上传:fetch 带 token 传 multipart(axios 实例不便控制 multipart 进度)
  const customRequest: UploadProps["customRequest"] = async (options) => {
    const file = options.file as File;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      message.error("仅支持 .xlsx 文件");
      options.onError?.(new Error("仅支持 .xlsx 文件"));
      return;
    }
    const fd = new FormData();
    fd.append("file", file);
    setUploading(true);
    try {
      const token = localStorage.getItem("token");
      const resp = await fetch(`/api/v1${importUrl}`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: fd,
      });
      if (!resp.ok) {
        let msg = `导入失败(HTTP ${resp.status})`;
        try {
          const data = (await resp.json()) as { error?: string; message?: string };
          msg = data?.error ?? data?.message ?? msg;
        } catch {
          // 非 JSON 响应体,用默认消息
        }
        message.error(msg);
        options.onError?.(new Error(msg));
        return;
      }
      const data = (await resp.json()) as { imported: number; failed: ImportFailedRow[] };
      if (data.failed.length > 0) {
        setFailures(data.failed);
      }
      message.success(`导入 ${data.imported} 条,失败 ${data.failed.length} 条`);
      if (data.imported > 0) {
        onDone?.();
      }
      options.onSuccess?.({});
    } catch (e) {
      message.error(e instanceof Error ? e.message : "导入请求异常");
      options.onError?.(e as Error);
    } finally {
      setUploading(false);
    }
  };

  const onTemplate = async () => {
    try {
      await downloadBlob(`/api/v1${templateUrl}`, templateFilename);
    } catch (e) {
      message.error(e instanceof Error ? e.message : "模板下载失败");
    }
  };

  const templateMenu = {
    items: [
      {
        key: "template",
        icon: <DownloadOutlined />,
        label: "下载导入模板",
        onClick: onTemplate,
      },
    ],
  };

  return (
    <>
      <Space size={4}>
        <Upload
          accept=".xlsx"
          showUploadList={false}
          customRequest={customRequest}
          maxCount={1}
        >
          <Button icon={<UploadOutlined />} loading={uploading}>
            导入
          </Button>
        </Upload>
        <Dropdown trigger={["click"]} menu={templateMenu} placement="bottomRight">
          <Button size="small" onClick={(e) => e.stopPropagation()}>
            <DownOutlined />
          </Button>
        </Dropdown>
      </Space>

      <Modal
        title="导入失败行"
        open={failures !== null}
        footer={null}
        width={640}
        onCancel={() => setFailures(null)}
      >
        <Table
          size="small"
          rowKey={(r) => `${r.row}-${r.code}`}
          pagination={false}
          scroll={{ y: 360 }}
          dataSource={failures ?? []}
          columns={[
            { title: "行号", dataIndex: "row", width: 70 },
            { title: "编码", dataIndex: "code", width: 180 },
            { title: "原因", dataIndex: "reason", ellipsis: true },
          ]}
        />
      </Modal>
    </>
  );
}
