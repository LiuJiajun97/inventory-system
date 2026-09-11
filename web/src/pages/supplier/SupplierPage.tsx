// 供应商管理(一期新增)
// 列表分页 + admin 新建/编辑 Drawer,operator/viewer 只读

import { useEffect, useState } from "react";
import { Button, Drawer, Form, Input, InputNumber, Select, Space, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { dictApi, supplierApi } from "../../api";
import type { Supplier } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";
import { ListPageShell } from "../../components/ListPageShell";
import { StatusTag } from "../../components/StatusTag";

interface FormValues {
  supplierCode: string;
  supplierName: string;
  taxNo?: string;
  defaultTaxRate?: number;
  contact?: string;
  phone?: string;
  address?: string;
  settleMethod?: string;
  payTermDays?: number;
  remark?: string;
}

export function SupplierPage() {
  const user = getUser();
  const isAdmin = user?.role === "admin";
  const [rows, setRows] = useState<Supplier[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState<string>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [settleOptions, setSettleOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [form] = Form.useForm<FormValues>();
  const [filterForm] = Form.useForm();

  const onSearch = async (kw?: string, pg = 1, ps = 20) => {
    setLoading(true);
    try {
      const res = await supplierApi.list({ keyword: kw, page: pg, pageSize: ps });
      setRows(res.rows);
      setTotal(res.total);
    } catch {
      // 拦截器已处理
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    onSearch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    dictApi
      .getType("settleMethod")
      .then((opts) => setSettleOptions(opts.map((o) => ({ label: o.label, value: o.code }))))
      .catch(() => undefined);
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setDrawerOpen(true);
  };

  const openEdit = (row: Supplier) => {
    setEditing(row);
    form.setFieldsValue({
      supplierCode: row.supplierCode,
      supplierName: row.supplierName,
      taxNo: row.taxNo ?? undefined,
      defaultTaxRate: Number(row.defaultTaxRate ?? 13),
      contact: row.contact ?? undefined,
      phone: row.phone ?? undefined,
      address: row.address ?? undefined,
      settleMethod: row.settleMethod ?? undefined,
      payTermDays: row.payTermDays ?? undefined,
      remark: row.remark ?? undefined,
    });
    setDrawerOpen(true);
  };

  const onSubmit = async () => {
    const v = await form.validateFields();
    if (editing) {
      await supplierApi.update(editing.id, { ...v });
    } else {
      await supplierApi.create({ ...v, defaultTaxRate: v.defaultTaxRate ?? 13 });
    }
    setDrawerOpen(false);
    onSearch(keyword, page, pageSize);
  };

  const columns: ColumnsType<Supplier> = [
    { title: "编码", dataIndex: "supplierCode", width: 140 },
    { title: "名称", dataIndex: "supplierName", ellipsis: true },
    { title: "联系人", dataIndex: "contact", width: 100, render: (v) => v ?? "-" },
    { title: "电话", dataIndex: "phone", width: 130, render: (v) => v ?? "-" },
    {
      title: "默认税率(%)",
      dataIndex: "defaultTaxRate",
      width: 110,
      align: "right",
      className: "num-cell",
      render: (v) => (v == null ? "-" : Number(v).toFixed(2)),
    },
    {
      title: "结算方式",
      dataIndex: "settleMethod",
      width: 100,
      render: (v: string | null) =>
        v ? settleOptions.find((o) => o.value === v)?.label ?? v : "-",
    },
    {
      title: "创建人",
      dataIndex: "creator",
      width: 100,
      ellipsis: true,
      render: (v?: string | null) => v ?? "-",
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 80,
      render: (v: number) =>
        v === 1 ? <StatusTag status="enabled" /> : <StatusTag status="disabled" />,
    },
    ...(isAdmin
      ? ([
          {
            title: "操作",
            width: 80,
            fixed: "right" as const,
            render: (_v: unknown, row: Supplier) => (
              <a onClick={() => openEdit(row)}>编辑</a>
            ),
          },
        ] as ColumnsType<Supplier>)
      : []),
  ];

  return (
    <>
      <ListPageShell
        extra={
          isAdmin && (
            <Button type="primary" onClick={openCreate}>
              新建供应商
            </Button>
          )
        }
        filter={
          <Form
            form={filterForm}
            layout="inline"
            onFinish={(v) => {
              setKeyword(v.keyword || undefined);
              setPage(1);
              onSearch(v.keyword || undefined, 1, pageSize);
            }}
          >
            <Form.Item label="关键字" name="keyword">
              <Input allowClear placeholder="编码/名称" style={{ width: 180 }} />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">
                  查询
                </Button>
                <Button
                  onClick={() => {
                    filterForm.resetFields();
                    setKeyword(undefined);
                    setPage(1);
                    onSearch(undefined, 1, pageSize);
                  }}
                >
                  重置
                </Button>
              </Space>
            </Form.Item>
          </Form>
        }
        tableProps={{
          rowKey: "id",
          loading,
          columns,
          dataSource: rows,
          scroll: { x: 900 },
          pagination: {
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
              onSearch(keyword, p, ps);
            },
            showTotal: (t) => `共 ${t} 条`,
          },
        }}
      />

      <Drawer
        title={editing ? `编辑供应商 - ${editing.supplierCode}` : "新建供应商"}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={480}
        extra={
          <Button type="primary" onClick={onSubmit}>
            保存
          </Button>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="供应商编码"
            name="supplierCode"
            rules={[{ required: true, message: "供应商编码必填" }]}
          >
            <Input disabled={!!editing} placeholder="如 SUP-001" />
          </Form.Item>
          <Form.Item
            label="供应商名称"
            name="supplierName"
            rules={[{ required: true, message: "供应商名称必填" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item label="税号" name="taxNo">
            <Input />
          </Form.Item>
          <Form.Item label="默认税率(%)" name="defaultTaxRate" initialValue={13}>
            <InputNumber min={0} max={100} step={0.01} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="联系人" name="contact">
            <Input />
          </Form.Item>
          <Form.Item label="联系电话" name="phone">
            <Input />
          </Form.Item>
          <Form.Item label="地址" name="address">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item label="结算方式" name="settleMethod">
            <Select allowClear showSearch optionFilterProp="label" options={settleOptions} />
          </Form.Item>
          <Form.Item label="账期天数" name="payTermDays">
            <InputNumber min={0} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
}
