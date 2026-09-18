// V16 操作日志(审计日志,仅 admin)
// ProTable 版:筛选 = 用户/模块/结果/日期区间;失败行可展开看异常消息
// 模块下拉选项来自后端 LogModule 集中映射(GET /operation-logs/modules)

import { useEffect, useState } from "react";
import { ProTable } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ProColumns } from "@ant-design/pro-components";
import { Tag, Typography } from "antd";
import dayjs from "dayjs";
import { operationLogApi } from "../../api";
import type { OperationLog } from "../../types/phase1";
import { fmtDateTime } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { proTableRequest } from "../../utils/proTable";

// 操作(HTTP 方法)朴素 Tag 配色
const ACTION_COLOR: Record<string, string> = {
  POST: "blue",
  PUT: "orange",
  DELETE: "red",
};

// ProTable dateRange transform 实收值:兼容字符串与 dayjs 两种路径
function dayPart(v: unknown): string | undefined {
  if (v == null) return undefined;
  return typeof v === "string" ? v : (v as dayjs.Dayjs).format("YYYY-MM-DD");
}

export function OperationLogPage() {
  const [modules, setModules] = useState<string[]>([]);

  // 模块筛选项(后端集中映射)
  useEffect(() => {
    operationLogApi
      .modules()
      .then(setModules)
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { username?: string; module?: string; success?: number; from?: string; to?: string }) => ({
      username: p.username,
      module: p.module,
      success: p.success,
      from: p.from,
      to: p.to,
    }),
    operationLogApi.list,
  );

  const columns: ProColumns<OperationLog>[] = [
    {
      title: "时间",
      dataIndex: "createdAt",
      width: 170,
      search: false,
      render: (_v, r) => fmtDateTime(r.createdAt),
    },
    {
      title: "用户",
      dataIndex: "username",
      width: 120,
      ellipsis: true,
      fieldProps: { allowClear: true, placeholder: "输入用户名" },
    },
    {
      title: "模块",
      dataIndex: "module",
      width: 120,
      valueType: "select",
      fieldProps: { allowClear: true, placeholder: "全部模块", options: modules.map((m) => ({ label: m, value: m })) },
    },
    {
      title: "操作",
      dataIndex: "action",
      width: 80,
      search: false,
      render: (_v, r) => <Tag color={ACTION_COLOR[r.action] ?? "default"}>{r.action}</Tag>,
    },
    {
      title: "请求路径",
      dataIndex: "path",
      width: 240,
      ellipsis: true,
      search: false,
    },
    {
      title: "对象",
      dataIndex: "targetId",
      width: 140,
      search: false,
      render: (_v, r) =>
        r.targetId != null ? (
          <span>
            {r.targetType ?? ""} #{r.targetId}
          </span>
        ) : (
          "-"
        ),
    },
    {
      title: "结果",
      dataIndex: "success",
      width: 80,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: [
          { label: "成功", value: 1 },
          { label: "失败", value: 0 },
        ],
      },
      render: (_v, r) =>
        r.success === 1 ? (
          <Typography.Text type="success">成功</Typography.Text>
        ) : (
          <Typography.Text type="danger">失败</Typography.Text>
        ),
    },
    {
      title: "耗时",
      dataIndex: "costMs",
      width: 80,
      align: "right",
      search: false,
      render: (_v, r) => (r.costMs != null ? `${r.costMs} ms` : "-"),
    },
    {
      title: "IP",
      dataIndex: "ip",
      width: 130,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.ip ?? "-",
    },
    {
      title: "日期范围",
      dataIndex: "range",
      valueType: "dateRange",
      hideInTable: true,
      search: {
        // 统一约定:东八区本地时间,空格分隔;开始=当天 00:00:00,结束=当天 23:59:59(含结束当天)
        transform: (value: [unknown, unknown]) => {
          const from = dayPart(value[0]);
          const to = dayPart(value[1]);
          return {
            from: from == null ? undefined : from + " 00:00:00",
            to: to == null ? undefined : to + " 23:59:59",
          };
        },
      },
    },
  ];

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(columns, "operation-log");
  return (
    <ProTable<OperationLog>
      rowKey="id"
      locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无操作日志" /> }}
      columns={rcColumns}
      request={request}
      headerTitle={false}
      options={{
        density: false,
        reload: false,
        fullScreen: false,
        setting: rcOptionSetting,
      }}
      columnsState={columnsState}
      onColumnsStateChange={onColumnsChange}
      components={rcComponents}
      search={{ labelWidth: "auto", defaultCollapsed: false, span: 6 }}
      pagination={{
        pageSize: 20,
        showSizeChanger: true,
        showTotal: (t) => `共 ${t} 条`,
      }}
      expandable={{
        // 失败行可展开看异常消息;成功行不可展开
        rowExpandable: (r) => r.success === 0,
        expandedRowRender: (r) => (
          <div style={{ padding: "8px 12px" }}>
            <Typography.Text type="secondary">异常消息:</Typography.Text>{" "}
            <Typography.Text type="danger">{r.errorMsg || "-"}</Typography.Text>
          </div>
        ),
        expandIcon: ({ expanded, onExpand, record }) =>
          record.success === 0 ? (
            <a style={{ color: "#f5222d" }} onClick={(e) => onExpand(record, e)}>
              {expanded ? "收起" : "详情"}
            </a>
          ) : null,
      }}
    />
  );
}
