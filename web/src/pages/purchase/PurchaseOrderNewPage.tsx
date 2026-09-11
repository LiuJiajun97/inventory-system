// 新建采购订单(一期新增)
// 服务端重算价税三列,前端仅展示输入;行级:物品/数量/单价/税率

import { useEffect, useState } from "react";
import { Button, Card, DatePicker, Form, Input, InputNumber, message, Select, Space, Table } from "antd";
import { Link, useNavigate } from "react-router-dom";
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, purchaseApi, supplierApi, userApi } from "../../api";
import type { Item, UserInfo } from "../../types";
import type { Supplier } from "../../types/phase1";

interface LineRow {
  key: number;
  itemId?: number;
  orderedQty?: number;
  expectedDeliveryDate?: Dayjs;
  unitPrice?: number;
  taxRate?: number;
  lineRemark?: string;
}

let lineSeq = 1;

export function PurchaseOrderNewPage() {
  const navigate = useNavigate();
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [lines, setLines] = useState<LineRow[]>([{ key: lineSeq++ }]);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

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

  const itemOptions = items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }));

  const onItemChange = (key: number, itemId: number) => {
    const it = items.find((x) => x.id === itemId);
    setLines((ls) =>
      ls.map((l) =>
        l.key === key
          ? { ...l, itemId, taxRate: it ? Number(it.defaultTaxRate ?? 13) : l.taxRate }
          : l,
      ),
    );
  };

  const onSubmit = async () => {
    const v = await form.validateFields();
    const validLines = lines.filter((l) => l.itemId && l.orderedQty && l.unitPrice != null);
    if (validLines.length === 0) {
      message.error("请至少填写一行订单明细");
      return;
    }
    setSaving(true);
    try {
      await purchaseApi.create({
        docDate: v.docDate.format("YYYY-MM-DD"),
        supplierId: v.supplierId,
        buyerId: v.buyerId,
        allowOverReceiptRate: v.allowOverReceiptRate ?? 0,
        remark: v.remark,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          orderedQty: l.orderedQty!,
          expectedDeliveryDate: l.expectedDeliveryDate?.format("YYYY-MM-DD"),
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

  const lineColumns = [
    {
      title: "物品",
      width: 240,
      render: (_v: unknown, l: LineRow) => (
        <Select
          showSearch
          placeholder="选择物品"
          optionFilterProp="label"
          style={{ width: "100%" }}
          options={itemOptions}
          value={l.itemId}
          onChange={(v) => onItemChange(l.key, v)}
        />
      ),
    },
    {
      title: "订购数量",
      width: 120,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0.0001}
          step={1}
          style={{ width: "100%" }}
          value={l.orderedQty}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, orderedQty: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "期望到货日",
      width: 160,
      render: (_v: unknown, l: LineRow) => (
        <DatePicker
          style={{ width: "100%" }}
          value={l.expectedDeliveryDate}
          onChange={(d) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, expectedDeliveryDate: d ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "不含税单价",
      width: 120,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0}
          step={0.01}
          style={{ width: "100%" }}
          value={l.unitPrice}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, unitPrice: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "税率(%)",
      width: 110,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0}
          max={100}
          step={0.01}
          style={{ width: "100%" }}
          value={l.taxRate}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, taxRate: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "行备注",
      render: (_v: unknown, l: LineRow) => (
        <Input
          value={l.lineRemark}
          onChange={(e) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, lineRemark: e.target.value } : x)))
          }
        />
      ),
    },
    {
      title: "",
      width: 60,
      render: (_v: unknown, l: LineRow) => (
        <a
          onClick={() => setLines((ls) => (ls.length > 1 ? ls.filter((x) => x.key !== l.key) : ls))}
        >
          删除
        </a>
      ),
    },
  ];

  return (
    <Card
      title="新建采购订单"
      extra={
        <Link to="/purchase-orders">
          <Button>返回列表</Button>
        </Link>
      }
    >
      <Form form={form} layout="vertical" style={{ maxWidth: 720 }}>
        <Space wrap size={24}>
          <Form.Item
            label="下单日期"
            name="docDate"
            initialValue={dayjs()}
            rules={[{ required: true, message: "请选择下单日期" }]}
          >
            <DatePicker />
          </Form.Item>
          <Form.Item
            label="供应商"
            name="supplierId"
            rules={[{ required: true, message: "请选择供应商" }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              style={{ width: 240 }}
              placeholder="选择供应商"
              options={suppliers.map((s) => ({ label: `${s.supplierCode} ${s.supplierName}`, value: s.id }))}
            />
          </Form.Item>
          <Form.Item label="采购员" name="buyerId" rules={[{ required: true, message: "请选择采购员" }]}>
            <Select
              style={{ width: 160 }}
              placeholder="选择采购员"
              options={users.map((u) => ({ label: u.name, value: u.id }))}
            />
          </Form.Item>
          <Form.Item
            label="允许超收比例(%)"
            name="allowOverReceiptRate"
            initialValue={0}
            tooltip="到货量上限 = 订购量 × (1 + 比例)"
          >
            <InputNumber min={0} max={100} step={0.1} style={{ width: 140 }} />
          </Form.Item>
        </Space>
        <Form.Item label="备注" name="remark">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>

      <div style={{ fontWeight: 600, marginBottom: 8 }}>订单明细(金额由服务端按价税分离重算)</div>
      <Table
        rowKey="key"
        size="small"
        dataSource={lines}
        pagination={false}
        columns={lineColumns}
        scroll={{ x: 900 }}
      />
      <Space style={{ marginTop: 16 }}>
        <Button
          onClick={() => setLines((ls) => [...ls, { key: lineSeq++ }])}
        >
          添加行
        </Button>
        <Button type="primary" loading={saving} onClick={onSubmit}>
          保存为草稿
        </Button>
      </Space>
    </Card>
  );
}
