// 新建采购订单(Pro 版:ProForm 表头 + EditableProTable 受控行明细)
// 服务端重算价税三列,前端仅展示输入;行级:物品/数量/单价/税率
// 选物品后自动带出默认税率(联动走数据流,不依赖 setFields)

import { useEffect, useState } from "react";
import { Button, Space } from "antd";
import {
  EditableProTable,
  ProCard,
  ProForm,
  ProFormDatePicker,
  ProFormDigit,
  ProFormSelect,
  ProFormTextArea,
} from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import { Link, useNavigate } from "react-router-dom";
import { App } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, purchaseApi, supplierApi, userApi } from "../../api";
import type { Item, UserInfo } from "../../types";
import type { Supplier } from "../../types/phase1";

interface LineRow {
  key: number;
  itemId?: number;
  orderedQty?: number;
  expectedDeliveryDate?: Dayjs | string;
  unitPrice?: number;
  taxRate?: number;
  lineRemark?: string;
}

let lineSeq = 2;

export function PurchaseOrderNewPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [users, setUsers] = useState<UserInfo[]>([]);
  // 首行 key 固定为 1,与 editableKeys 初始值对应;新增行从 2 起
  const [data, setData] = useState<LineRow[]>([{ key: 1 }]);
  // 可编辑行(受控):初始行即处于编辑态
  const [editableKeys, setEditableKeys] = useState<React.Key[]>([1]);
  const [saving, setSaving] = useState(false);
  const [headerForm] = ProForm.useForm();
  const [lineForm] = ProForm.useForm();

  useEffect(() => {
    supplierApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setSuppliers(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
    userApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setUsers(r.rows))
      .catch(() => undefined);
  }, []);

  const itemOptions = items.map((it) => ({
    label: `${it.itemCode} ${it.itemName}`,
    value: it.id,
  }));

  // 行值变化:联动(选物品且税率未填→带出默认税率)后写回数据流。
  // EditableProTable 受控 value 驱动,控件随 data 刷新,
  // 不依赖 form.setFields(外部 setFields 不会触发已挂载控件重渲染)
  const handleLinesChange = (values: readonly LineRow[]) => {
    const merged: LineRow[] = values.map((row) => {
      if (row.itemId != null && row.taxRate == null) {
        const item = items.find((i) => i.id === row.itemId);
        if (item) {
          return { ...row, taxRate: Number(item.defaultTaxRate ?? 13) };
        }
      }
      return { ...row };
    });
    setData(merged);
  };

  // 手动添加一行并进入编辑态
  const addLine = () => {
    const key = lineSeq++;
    setData((d) => [...d, { key }]);
    setEditableKeys((k) => [...k, key]);
  };

  // 删除一行(受控:同步 data 与 editableKeys)
  const removeLine = (key: number) => {
    setData((d) => d.filter((r) => r.key !== key));
    setEditableKeys((k) => k.filter((x) => x !== key));
  };

  // 行明细列:编辑态由 valueType 自动渲染,展示态由 render 渲染
  const columns: ProColumns<LineRow>[] = [
    {
      title: "物品",
      dataIndex: "itemId",
      width: 240,
      valueType: "select",
      fieldProps: {
        showSearch: true,
        optionFilterProp: "label",
        placeholder: "选择物品",
        options: itemOptions,
      },
      formItemProps: { rules: [{ required: true, message: "请选择物品" }] },
      render: (_v, r) => items.find((i) => i.id === r.itemId)?.itemName ?? "-",
    },
    {
      title: "订购数量",
      dataIndex: "orderedQty",
      width: 120,
      valueType: "digit",
      fieldProps: { min: 0.0001, step: 1 },
      formItemProps: { rules: [{ required: true, message: "请填写数量" }] },
    },
    {
      title: "期望到货日",
      dataIndex: "expectedDeliveryDate",
      width: 160,
      valueType: "date",
      fieldProps: { style: { width: "100%" } },
    },
    {
      title: "不含税单价",
      dataIndex: "unitPrice",
      width: 120,
      valueType: "digit",
      fieldProps: { min: 0, step: 0.01 },
      formItemProps: { rules: [{ required: true, message: "请填写单价" }] },
    },
    {
      title: "税率(%)",
      dataIndex: "taxRate",
      width: 110,
      valueType: "digit",
      fieldProps: { min: 0, max: 100, step: 0.01 },
    },
    {
      title: "行备注",
      dataIndex: "lineRemark",
      valueType: "text",
    },
  ];

  const onSubmit = async () => {
    // 表头校验
    let values: Record<string, unknown>;
    try {
      values = await headerForm.validateFields();
    } catch {
      message.warning("请填写表头必填项");
      return;
    }
    // 行明细校验(编辑态 form)
    try {
      await lineForm.validateFields();
    } catch {
      message.warning("请完善订单明细必填项");
      return;
    }
    const validLines = data.filter(
      (l) => l.itemId && l.orderedQty && l.unitPrice != null,
    );
    if (validLines.length === 0) {
      message.error("请至少填写一行订单明细");
      return;
    }
    setSaving(true);
    try {
      await purchaseApi.create({
        docDate: (values.docDate as Dayjs).format("YYYY-MM-DD"),
        supplierId: values.supplierId as number,
        buyerId: values.buyerId as number,
        allowOverReceiptRate: (values.allowOverReceiptRate as number) ?? 0,
        remark: values.remark as string | undefined,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          orderedQty: l.orderedQty!,
          expectedDeliveryDate:
            typeof l.expectedDeliveryDate === "string"
              ? l.expectedDeliveryDate
              : l.expectedDeliveryDate?.format("YYYY-MM-DD"),
          unitPrice: l.unitPrice!,
          taxRate: l.taxRate ?? 13,
          lineRemark: l.lineRemark,
        })),
      });
      message.success("采购订单已创建(草稿)");
      navigate("/purchase-orders");
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <ProCard
        title="表头信息"
        extra={
          <Link to="/purchase-orders">
            <Button>返回列表</Button>
          </Link>
        }
      >
        <ProForm
          form={headerForm}
          layout="horizontal"
          grid
          submitter={false}
          style={{ maxWidth: 960 }}
        >
          <ProFormDatePicker
            name="docDate"
            label="下单日期"
            width="md"
            initialValue={dayjs()}
            rules={[{ required: true, message: "请选择下单日期" }]}
          />
          <ProFormSelect
            name="supplierId"
            label="供应商"
            width="lg"
            showSearch
            options={suppliers.map((s) => ({
              label: `${s.supplierCode} ${s.supplierName}`,
              value: s.id,
            }))}
            rules={[{ required: true, message: "请选择供应商" }]}
            fieldProps={{ optionFilterProp: "label" }}
          />
          <ProFormSelect
            name="buyerId"
            label="采购员"
            width="md"
            options={users.map((u) => ({ label: u.name, value: u.id }))}
            rules={[{ required: true, message: "请选择采购员" }]}
          />
          <ProFormDigit
            name="allowOverReceiptRate"
            label="允许超收比例(%)"
            width="md"
            initialValue={0}
            min={0}
            max={100}
            fieldProps={{ step: 0.1 }}
            tooltip="到货量上限 = 订购量 × (1 + 比例)"
          />
          <ProFormTextArea
            name="remark"
            label="备注"
            colProps={{ span: 24 }}
            fieldProps={{ rows: 2 }}
          />
        </ProForm>
      </ProCard>

      <ProCard title="订单明细(金额由服务端按价税分离重算)">
        <EditableProTable<LineRow>
          rowKey="key"
          columns={columns}
          value={data}
          onChange={handleLinesChange}
          // 受控:value 变化时同步回 form(官方机制),
          // 联动写回 data 后控件随 form 刷新
          controlled
          recordCreatorProps={false}
          search={false}
          options={false}
          pagination={false}
          scroll={{ x: 900 }}
          editable={{
            type: "multiple",
            form: lineForm,
            editableKeys,
            onChange: (keys) => setEditableKeys(keys),
            onDelete: async (key) => removeLine(Number(key)),
            // 无逐行"保存"按钮:值即时生效,保留行删除
            actionRender: (_row, _config, dom) => [dom.delete],
          }}
        />
        <Space style={{ marginTop: 16 }} align="center">
          <Button onClick={addLine}>+ 添加行</Button>
          <Button type="primary" loading={saving} onClick={onSubmit}>
            保存为草稿
          </Button>
        </Space>
      </ProCard>
    </Space>
  );
}
