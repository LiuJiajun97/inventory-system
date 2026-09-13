// 客户管理(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动(关键字),新建按钮经 optionRender 放筛选行右侧
// 列表分页 + admin 新建/编辑 Drawer,operator/viewer 只读

import { useEffect, useMemo, useRef, useState } from "react";
import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, theme } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { customerApi, dictApi } from "../../api";
import type { Customer } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { ImportButton } from "../../components/ImportButton";
import { ExportButton } from "../../components/ExportButton";
import { StatusTag } from "../../components/StatusTag";

interface FormValues {
  customerCode: string;
  customerName: string;
  taxNo?: string;
  defaultTaxRate?: number;
  contact?: string;
  phone?: string;
  address?: string;
  settleMethod?: string;
  payTermDays?: number;
  // V9 通用字段(可空)
  bankName?: string;
  bankAccount?: string;
  creditLimit?: number;
  deliveryAddress?: string;
  // V10 邮箱(可空)
  email?: string;
  remark?: string;
}

export function CustomerPage() {
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端拦截)
  const canEdit = hasPerm("customer:edit");
  const canCreate = hasPerm("customer:create");
  const canImport = hasPerm("customer:import");
  const canExport = hasPerm("customer:export");
  // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<{ keyword?: string }>({});
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [settleOptions, setSettleOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [form] = Form.useForm<FormValues>();
  const actionRef = useRef<ActionType>();
  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();

  // 结算方式字典数据源(异步加载,仅用于列展示)
  useEffect(() => {
    dictApi
      .getType("settleMethod")
      .then((opts) => setSettleOptions(opts.map((o) => ({ label: o.label, value: o.code }))))
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    keyword?: string;
  }) => {
    setFilterParams({ keyword: params.keyword || undefined });
    const res = await customerApi.list({
      keyword: params.keyword || undefined,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

  // 导出 URL:带当前筛选条件(导出不分页)
  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    if (filterParams.keyword) p.set("keyword", filterParams.keyword);
    const s = p.toString();
    return "/customers/export" + (s ? `?${s}` : "");
  }, [filterParams]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setDrawerOpen(true);
  };

  const openEdit = (row: Customer) => {
    setEditing(row);
    form.setFieldsValue({
      customerCode: row.customerCode,
      customerName: row.customerName,
      taxNo: row.taxNo ?? undefined,
      defaultTaxRate: Number(row.defaultTaxRate ?? 13),
      contact: row.contact ?? undefined,
      phone: row.phone ?? undefined,
      address: row.address ?? undefined,
      settleMethod: row.settleMethod ?? undefined,
      payTermDays: row.payTermDays ?? undefined,
      bankName: row.bankName ?? undefined,
      bankAccount: row.bankAccount ?? undefined,
      creditLimit: row.creditLimit == null ? undefined : Number(row.creditLimit),
      deliveryAddress: row.deliveryAddress ?? undefined,
      email: row.email ?? undefined,
      remark: row.remark ?? undefined,
    });
    setDrawerOpen(true);
  };

  const onSubmit = async () => {
    const v = await form.validateFields();
    if (editing) {
      await customerApi.update(editing.id, { ...v });
    } else {
      await customerApi.create({ ...v, defaultTaxRate: v.defaultTaxRate ?? 13 });
    }
    setDrawerOpen(false);
    actionRef.current?.reload();
  };

  const columns: ProColumns<Customer>[] = [
    { title: "编码", dataIndex: "customerCode", width: 140, search: false },
    { title: "名称", dataIndex: "customerName", ellipsis: true, search: false },
    { title: "联系人", dataIndex: "contact", width: 100, search: false, render: (v) => v ?? "-" },
    { title: "电话", dataIndex: "phone", width: 130, search: false, render: (v) => v ?? "-" },
    {
      title: "默认税率(%)",
      dataIndex: "defaultTaxRate",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      render: (v) => (v == null ? "-" : Number(v).toFixed(2)),
    },
    {
      title: "结算方式",
      dataIndex: "settleMethod",
      width: 100,
      search: false,
      render: (_v, r) =>
        r.settleMethod ? settleOptions.find((o) => o.value === r.settleMethod)?.label ?? r.settleMethod : "-",
    },
    {
      title: "创建人",
      dataIndex: "creator",
      width: 100,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.creator ?? "-",
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 80,
      search: false,
      render: (_v, r) =>
        r.status === 1 ? <StatusTag status="enabled" /> : <StatusTag status="disabled" />,
    },
    ...(canEdit
      ? ([
          {
            title: "操作",
            width: 80,
            fixed: "right" as const,
            search: false,
            render: (_v: unknown, row: Customer) => (
              <a onClick={() => openEdit(row)}>编辑</a>
            ),
          },
        ] as ProColumns<Customer>[])
      : []),
  ];

  return (
    <>
      <ProTable<Customer>
        rowKey="id"
        actionRef={actionRef}
        columns={[
          {
            title: "关键字",
            dataIndex: "keyword",
            hideInTable: true,
            fieldProps: { placeholder: "编码/名称", allowClear: true },
          },
          ...columns,
        ]}
        request={request}
        headerTitle={false}
        options={false}
        scroll={{ x: 900 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            canImport && (
              <ImportButton
                key="import"
                importUrl="/customers/import"
                templateUrl="/customers/template"
                templateFilename="客户导入模板.xlsx"
                onDone={() => actionRef.current?.reload()}
              />
            ),
            canExport && (
              <ExportButton key="export" url={exportUrl} filename="客户.xlsx" />
            ),
            canCreate && (
              <Button key="new" type="primary" onClick={openCreate}>
                新建
              </Button>
            ),
          ],
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Drawer
        title={editing ? `编辑客户 - ${editing.customerCode}` : "新建客户"}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={480}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮"保存"
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setDrawerOpen(false)}>取消</Button>
              <Button type="primary" onClick={onSubmit}>
                保存
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          {/* 表头字段两列对齐(统一规格:纯表单抽屉两列上限) */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="客户编码"
                name="customerCode"
                rules={[{ required: true, message: "客户编码必填" }]}
              >
                <Input disabled={!!editing} placeholder="如 CUS-001" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="客户名称"
                name="customerName"
                rules={[{ required: true, message: "客户名称必填" }]}
              >
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="税号" name="taxNo">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="默认税率(%)" name="defaultTaxRate" initialValue={13}>
                <InputNumber min={0} max={100} step={0.01} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="联系人" name="contact">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="联系电话" name="phone">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          {/* 地址/备注:textarea 独占一行 */}
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item label="地址" name="address">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="结算方式" name="settleMethod">
                <Select allowClear showSearch optionFilterProp="label" options={settleOptions} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="账期天数" name="payTermDays">
                <InputNumber min={0} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
          </Row>
          {/* V9 通用字段(可空):开户行/银行账号/信用额度/交货地址 */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="开户行" name="bankName">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="银行账号" name="bankAccount">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="信用额度" name="creditLimit">
                <InputNumber min={0} precision={2} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="交货地址" name="deliveryAddress">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          {/* V10 邮箱(可空) */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="邮箱" name="email">
                <Input placeholder="如 customer@example.com" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item label="备注" name="remark">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
}
