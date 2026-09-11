// 新建销售订单(一期新增)
// 服务端重算价税三列,前端仅展示输入;行级:物品/数量/单价/税率

import { useEffect, useState } from "react";
import { Button, Card, DatePicker, Form, Input, InputNumber, message, Select, Space, Table } from "antd";
import { Link, useNavigate, useParams } from "react-router-dom";
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, salesApi, customerApi, userApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { Item, UserInfo } from "../../types";
import type { Customer } from "../../types/phase1";

interface LineRow {
  key: number;
  itemId?: number;
  orderedQty?: number;
  customerDeliveryDate?: Dayjs;
  unitPrice?: number;
  taxRate?: number;
  lineRemark?: string;
}

let lineSeq = 1;

export function SalesOrderNewPage() {
  const navigate = useNavigate();
  const { id: editIdParam } = useParams<{ id?: string }>();
  // 路由 /sales-orders/new/:id? 携带 id 时为编辑模式(草稿/已驳回单)
  const editId = editIdParam ? Number(editIdParam) : null;
  const [docNo, setDocNo] = useState("");
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [lines, setLines] = useState<LineRow[]>([{ key: lineSeq++ }]);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    customerApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setCustomers(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
    userApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setUsers(r.rows))
      .catch(() => undefined);
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 编辑模式:GET 详情回填表头 + 行明细(行 key 用 1..n,与新建的自增 key 规则一致)
  useEffect(() => {
    if (editId == null) return;
    salesApi
      .get(editId)
      .then((doc) => {
        setDocNo(doc.docNo);
        form.setFieldsValue({
          docDate: dayjs(doc.docDate),
          customerId: doc.customerId,
          salespersonId: doc.salespersonId,
          warehouseId: doc.warehouseId,
          remark: doc.remark ?? undefined,
        });
        const rows: LineRow[] = (doc.items ?? []).map((l, i) => ({
          key: i + 1,
          itemId: l.itemId,
          orderedQty: Number(l.orderedQty),
          customerDeliveryDate: l.customerDeliveryDate ? dayjs(l.customerDeliveryDate) : undefined,
          unitPrice: Number(l.unitPrice),
          taxRate: Number(l.taxRate),
          lineRemark: l.lineRemark ?? undefined,
        }));
        setLines(rows);
        // 防止新建时"添加行"自增 key 与回填行 key 冲突
        lineSeq = Math.max(lineSeq, rows.length + 1);
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId]);

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
      const payload = {
        docDate: v.docDate.format("YYYY-MM-DD"),
        customerId: v.customerId,
        salespersonId: v.salespersonId,
        warehouseId: v.warehouseId,
        remark: v.remark,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          orderedQty: l.orderedQty!,
          customerDeliveryDate: l.customerDeliveryDate?.format("YYYY-MM-DD"),
          unitPrice: l.unitPrice!,
          taxRate: l.taxRate ?? 13,
          lineRemark: l.lineRemark,
        })),
      };
      if (editId != null) {
        await salesApi.update(editId, payload);
        message.success("销售订单已保存(仍为可编辑状态)");
      } else {
        await salesApi.create(payload);
        message.success("销售订单已创建(草稿)");
      }
      navigate("/sales-orders");
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
      title: "要求交付日",
      width: 160,
      render: (_v: unknown, l: LineRow) => (
        <DatePicker
          style={{ width: "100%" }}
          value={l.customerDeliveryDate}
          onChange={(d) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, customerDeliveryDate: d ?? undefined } : x)))
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
      title={editId != null ? `编辑销售订单 - ${docNo || "..."}` : "新建销售订单"}
      extra={
        <Link to="/sales-orders">
          <Button>返回列表</Button>
        </Link>
      }
    >
      <Form form={form} layout="vertical" style={{ maxWidth: 720 }}>
        <Space wrap size={24}>
          <Form.Item
            label="开单日期"
            name="docDate"
            initialValue={dayjs()}
            rules={[{ required: true, message: "请选择开单日期" }]}
          >
            <DatePicker />
          </Form.Item>
          <Form.Item
            label="客户"
            name="customerId"
            rules={[{ required: true, message: "请选择客户" }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              style={{ width: 240 }}
              placeholder="选择客户"
              options={customers.map((s) => ({ label: `${s.customerCode} ${s.customerName}`, value: s.id }))}
            />
          </Form.Item>
          <Form.Item label="销售员" name="salespersonId" rules={[{ required: true, message: "请选择销售员" }]}>
            <Select
              style={{ width: 160 }}
              placeholder="选择销售员"
              options={users.map((u) => ({ label: u.name, value: u.id }))}
            />
          </Form.Item>
          <Form.Item
            label="发货仓库"
            name="warehouseId"
            rules={[{ required: true, message: "请选择发货仓库" }]}
          >
            <Select
              style={{ width: 180 }}
              placeholder="选择发货仓库"
              options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
            />
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
          {editId != null ? "保存" : "保存为草稿"}
        </Button>
      </Space>
    </Card>
  );
}
