// 库位管理(SPEC-WEB V2 2.10)
// ProTable 版:筛选字段由 columns 配置驱动(仓库),新建按钮经 optionRender 放筛选行右侧
// 新建/编辑库位;编辑时编码与所属仓库锁死

import { useEffect, useRef, useState } from "react";
import {
  Form,
  Input,
  Select,
  Button,
  Modal,
  App,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { warehouseApi } from "../../api";
import type { Location, Warehouse } from "../../types";
import { getUser } from "../../auth/useAuth";

export function LocationListPage() {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Location | null>(null);
  const [form] = Form.useForm();
  const actionRef = useRef<ActionType>();
  const user = getUser();
  const { message } = App.useApp();

  // 仓库下拉数据源(异步加载,仅用于筛选项与名称展示)
  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  const warehouseNameOf = (id: number) =>
    warehouses.find((w) => w.id === id)?.warehouseName ?? id;

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    warehouseId?: number;
  }) => {
    const res = await warehouseApi.listLocations({
      warehouseId: params.warehouseId,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    // 返回适配:后端 {rows,total} -> ProTable {data,success,total}
    return { data: res.rows, success: true, total: res.total };
  };

  const columns: ProColumns<Location>[] = [
    {
      title: "仓库",
      dataIndex: "warehouseId",
      width: 200,
      valueType: "select",
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
      render: (_v, r) => warehouseNameOf(r.warehouseId),
    },
    {
      title: "编码",
      dataIndex: "locationCode",
      width: 160,
      search: false,
      render: (_v, r) => (
        <span style={{ fontFamily: "monospace" }}>{r.locationCode}</span>
      ),
    },
    { title: "名称", dataIndex: "locationName", ellipsis: true, search: false },
    ...(user?.role === "admin"
      ? [
          {
            title: "操作",
            width: 80,
            search: false,
            render: (_v: unknown, r: Location) => (
              <Button type="link" size="small" onClick={() => openEdit(r)}>
                编辑
              </Button>
            ),
          },
        ]
      : []),
  ];

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };

  const openEdit = (r: Location) => {
    setEditing(r);
    form.setFieldsValue({
      warehouseId: r.warehouseId,
      warehouseName: warehouseNameOf(r.warehouseId),
      locationCode: r.locationCode,
      locationName: r.locationName,
    });
    setOpen(true);
  };

  const onSave = async () => {
    const v = await form.validateFields();
    try {
      if (editing) {
        await warehouseApi.updateLocation(editing.id, {
          locationName: v.locationName,
        });
        message.success("库位已更新");
      } else {
        await warehouseApi.createLocation({
          warehouseId: v.warehouseId,
          locationCode: v.locationCode,
          locationName: v.locationName,
        });
        message.success("库位创建成功");
      }
      setOpen(false);
      form.resetFields();
      setEditing(null);
      actionRef.current?.reload();
    } catch {
      // 拦截器已处理
    }
  };

  const closeModal = () => {
    setOpen(false);
    form.resetFields();
    setEditing(null);
  };

  return (
    <>
      <ProTable<Location>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={request}
        headerTitle={false}
        options={false}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            user?.role === "admin" && (
              <Button key="new" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
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

      <Modal
        title={editing ? "编辑库位" : "新建库位"}
        open={open}
        onCancel={closeModal}
        onOk={onSave}
        okText="提交"
        cancelText="取消"
        width={420}
        destroyOnClose
      >
        <Form form={form} layout="vertical" requiredMark={false}>
          {editing ? (
            <Form.Item label="所属仓库" name="warehouseName">
              <Input disabled />
            </Form.Item>
          ) : (
            <Form.Item
              label="仓库"
              name="warehouseId"
              rules={[{ required: true, message: "仓库必填" }]}
            >
              <Select
                options={warehouses.map((w) => ({
                  label: w.warehouseName,
                  value: w.id,
                }))}
              />
            </Form.Item>
          )}
          <Form.Item
            label="库位编码"
            name="locationCode"
            rules={[{ required: true, message: "编码必填" }]}
          >
            <Input placeholder="如 A-03" disabled={editing != null} />
          </Form.Item>
          <Form.Item label="库位名称" name="locationName">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
