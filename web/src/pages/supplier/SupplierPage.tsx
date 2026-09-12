// 供应商管理(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动(关键字),新建按钮经 optionRender 放筛选行右侧
// 列表分页 + admin 新建/编辑 Drawer,operator/viewer 只读

import { useEffect, useRef, useState } from "react";
import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, theme } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { dictApi, supplierApi } from "../../api";
import type { Supplier } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
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
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端拦截)
  const canEdit = hasPerm("supplier:edit");
  const canCreate = hasPerm("supplier:create");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Supplier | null>(null);
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
    const res = await supplierApi.list({
      keyword: params.keyword || undefined,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

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
    actionRef.current?.reload();
  };

  const columns: ProColumns<Supplier>[] = [
    { title: "编码", dataIndex: "supplierCode", width: 140, search: false },
    { title: "名称", dataIndex: "supplierName", ellipsis: true, search: false },
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
            render: (_v: unknown, row: Supplier) => (
              <a onClick={() => openEdit(row)}>编辑</a>
            ),
          },
        ] as ProColumns<Supplier>[])
      : []),
  ];

  return (
    <>
      <ProTable<Supplier>
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
        title={editing ? `编辑供应商 - ${editing.supplierCode}` : "新建供应商"}
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
                label="供应商编码"
                name="supplierCode"
                rules={[{ required: true, message: "供应商编码必填" }]}
              >
                <Input disabled={!!editing} placeholder="如 SUP-001" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="供应商名称"
                name="supplierName"
                rules={[{ required: true, message: "供应商名称必填" }]}
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
