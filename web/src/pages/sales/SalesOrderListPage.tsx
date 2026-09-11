// 销售订单列表(一期新增)
// 状态机操作列:提交(草稿/驳回) / 审批 / 驳回 / 关闭 / 作废(admin)
// 详情 Drawer 展示价税三列 + 行发货进度

import { useEffect, useState } from "react";
import { Button, DatePicker, Descriptions, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Link } from "react-router-dom";
import dayjs from "dayjs";
import { fmtDate, fmtDateTime } from "../../utils/format";
import { salesApi, customerApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { SalesOrder } from "../../types/phase1";
import type { Customer } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";
import { DocStatusTag } from "../../components/DocStatusTag";

const { RangePicker } = DatePicker;

export function SalesOrderListPage() {
  const user = getUser();
  const isAdmin = user?.role === "admin";
  const isWriter = user?.role === "admin" || user?.role === "operator";
  const [rows, setRows] = useState<SalesOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [detail, setDetail] = useState<SalesOrder | null>(null);
  const [rejectTarget, setRejectTarget] = useState<SalesOrder | null>(null);
  const [form] = Form.useForm();

  const onSearch = async (values: Record<string, unknown>, pg = 1, ps = 20) => {
    setLoading(true);
    try {
      const range = values.range as [dayjs.Dayjs, dayjs.Dayjs] | null;
      const res = await salesApi.list({
        customerId: values.customerId as number | undefined,
        docNo: values.docNo as string | undefined,
        status: values.status as string | undefined,
        from: range?.[0]?.format("YYYY-MM-DD"),
        to: range?.[1]?.format("YYYY-MM-DD"),
        page: pg,
        pageSize: ps,
      });
      setRows(res.rows);
      setTotal(res.total);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    customerApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setCustomers(r.rows))
      .catch(() => undefined);
    onSearch({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refresh = () => onSearch(form.getFieldsValue(), page, pageSize);

  const doAction = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      Modal.success({ content: msg });
      refresh();
    } catch {
      // 拦截器已提示
    }
  };

  const columns: ColumnsType<SalesOrder> = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      render: (v: string, row) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => setDetail(row)}>
          {v}
        </a>
      ),
    },
    { title: "开单日期", dataIndex: "docDate", width: 110, render: (v: string) => fmtDate(v) },
    {
      title: "客户",
      width: 150,
      ellipsis: true,
      render: (_v, r) =>
        customers.find((s) => s.id === r.customerId)?.customerName ?? `#${r.customerId}`,
    },
    {
      title: "发货仓库",
      width: 130,
      ellipsis: true,
      render: (_v, r) =>
        warehouses.find((w) => w.id === r.warehouseId)?.warehouseName ?? `#${r.warehouseId}`,
    },
    {
      title: "金额/税额/价税合计",
      width: 220,
      align: "right",
      className: "num-cell",
      render: (_v, r) => (
        <span>
          {Number(r.totalAmount).toFixed(2)} / {Number(r.totalTaxAmount).toFixed(2)} /{" "}
          <b>{Number(r.totalTaxInclusive).toFixed(2)}</b>
        </span>
      ),
    },
    { title: "创建人", dataIndex: "creator", width: 90 },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 160,
      render: (v: string) => fmtDateTime(v),
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      render: (v: string) => <DocStatusTag status={v} />,
    },
    {
      title: "操作",
      width: 260,
      fixed: "right" as const,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (isWriter && (s === "draft" || s === "rejected")) {
          btns.push(
            <a
              key="submit"
              onClick={() => doAction(() => salesApi.submit(row.id), "已提交审批")}
            >
              提交
            </a>,
          );
        }
        if (isWriter && s === "draft") {
          btns.push(
            <Popconfirm key="void" title="确认作废该草稿?" onConfirm={() => doAction(() => salesApi.voidDoc(row.id), "已作废")}>
              <a>作废</a>
            </Popconfirm>,
          );
        }
        if (isWriter && s === "pending") {
          btns.push(
            <Popconfirm
              key="approve"
              title="审批通过后将按 FEFO 预占库存,库存不足将回退草稿。确认审批?"
              onConfirm={() => doAction(() => salesApi.approve(row.id), "已审批并通过预占")}
            >
              <a>审批</a>
            </Popconfirm>,
            <a key="reject" onClick={() => setRejectTarget(row)}>
              驳回
            </a>,
          );
        }
        if (isWriter && s === "approved") {
          btns.push(
            <Popconfirm key="close" title="关闭后未发货部分不再发货,确认关闭?" onConfirm={() => doAction(() => salesApi.close(row.id), "已关闭")}>
              <a>关闭</a>
            </Popconfirm>,
            <Popconfirm key="void" title="确认作废该订单?" onConfirm={() => doAction(() => salesApi.voidDoc(row.id), "已作废")}>
              <a>作废</a>
            </Popconfirm>,
          );
        }
        return <Space size={12}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  const statusOptions = [
    { label: "草稿", value: "draft" },
    { label: "待审批", value: "pending" },
    { label: "已审批", value: "approved" },
    { label: "已完成", value: "completed" },
    { label: "已驳回", value: "rejected" },
    { label: "已关闭", value: "closed" },
    { label: "已作废", value: "voided" },
  ];

  const filterNode = (
    <Form form={form} layout="inline" onFinish={(v) => { setPage(1); onSearch(v); }}>
      <Form.Item label="单号" name="docNo">
        <Input allowClear placeholder="订单号" style={{ width: 150 }} />
      </Form.Item>
      <Form.Item label="客户" name="customerId">
        <Select
          allowClear
          placeholder="全部"
          style={{ width: 180 }}
          options={customers.map((s) => ({ label: s.customerName, value: s.id }))}
        />
      </Form.Item>
      <Form.Item label="状态" name="status">
        <Select allowClear placeholder="全部" style={{ width: 120 }} options={statusOptions} />
      </Form.Item>
      <Form.Item label="日期" name="range">
        <RangePicker />
      </Form.Item>
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit">查询</Button>
          <Button
            onClick={() => {
              form.resetFields();
              setPage(1);
              onSearch({});
            }}
          >
            重置
          </Button>
        </Space>
      </Form.Item>
    </Form>
  );

  return (
    <>
      <ListPageShell
        title="销售订单"
        extra={
          isWriter && (
            <Link to="/sales-orders/new">
              <Button type="primary">新建销售订单</Button>
            </Link>
          )
        }
        filter={filterNode}
        tableProps={{
          rowKey: "id",
          loading,
          columns,
          dataSource: rows,
          scroll: { x: 1200 },
          pagination: {
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
              onSearch(form.getFieldsValue(), p, ps);
            },
            showTotal: (t) => `共 ${t} 条`,
          },
        }}
      />

      <Modal
        title={`销售订单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={860}
      >
        {detail && (
          <>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="开单日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="发货仓库">
                {warehouses.find((w) => w.id === detail.warehouseId)?.warehouseName ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <DocStatusTag status={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="金额">{Number(detail.totalAmount).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="税额">{Number(detail.totalTaxAmount).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="价税合计">{Number(detail.totalTaxInclusive).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="审批人">{detail.approver ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="驳回原因">{detail.rejectReason ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="备注" span={3}>{detail.remark ?? "-"}</Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 12, fontWeight: 600 }}>订单行(价税分离)</div>
            <Table
              style={{ marginTop: 8 }}
              size="small"
              rowKey="id"
              dataSource={detail.items ?? []}
              pagination={false}
              scroll={{ x: 900 }}
              columns={[
                { title: "行号", dataIndex: "lineNo", width: 50 },
                { title: "物品", dataIndex: "itemName", width: 140, render: (v: string, r) => `${r.itemCode} ${v}` },
                { title: "规格快照", dataIndex: "specSnapshot", width: 90, render: (v) => v ?? "-" },
                { title: "订购量", dataIndex: "orderedQty", width: 80, align: "right", className: "num-cell" },
                { title: "已到货", dataIndex: "arrivedQty", width: 80, align: "right", className: "num-cell" },
                { title: "单价", dataIndex: "unitPrice", width: 80, align: "right", className: "num-cell" },
                { title: "税率(%)", dataIndex: "taxRate", width: 70, align: "right", className: "num-cell" },
                { title: "金额", dataIndex: "amount", width: 90, align: "right", className: "num-cell" },
                { title: "税额", dataIndex: "taxAmount", width: 90, align: "right", className: "num-cell" },
                { title: "价税合计", dataIndex: "taxInclusiveTotal", width: 90, align: "right", className: "num-cell" },
                {
                  title: "行状态",
                  dataIndex: "closed",
                  width: 80,
                  render: (v: boolean) => (
                    <Tag color={v ? "success" : "default"}>{v ? "已到满" : "未到满"}</Tag>
                  ),
                },
              ]}
            />
          </>
        )}
      </Modal>

      <Modal
        title={`驳回订单 - ${rejectTarget?.docNo ?? ""}`}
        open={!!rejectTarget}
        onCancel={() => setRejectTarget(null)}
        onOk={() => {
          const v = form.getFieldValue("reason") as string;
          if (!v || !v.trim()) return;
          doAction(() => salesApi.reject(rejectTarget!.id, v.trim()), "已驳回");
          setRejectTarget(null);
          form.setFieldValue("reason", "");
        }}
        okButtonProps={{
          disabled: !form.getFieldValue("reason") || !form.getFieldValue("reason").trim(),
        }}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="驳回原因(必填)" name="reason">
            <Input.TextArea rows={3} placeholder="请填写驳回原因" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
