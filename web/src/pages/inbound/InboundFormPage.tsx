// 新建入库单(SPEC-WEB V2 2.4)
// 布局与采购/销售统一:页面头部 + 单卡片(表头 + 明细 + 底部固定操作条)
// 序列号与数量联动校验:启用序列号时,序列号数量必须 = 总数量,行内红色提示
// 明细保留原 antd Table 手工编辑机制(关联采购单行模板/仓库配置联动不改)

import { useEffect, useState } from "react";
import {
  Form,
  Row,
  Col,
  Select,
  Input,
  InputNumber,
  DatePicker,
  Button,
  Table,
  Tag,
  App,
} from "antd";
import { PlusOutlined, DeleteOutlined, InfoCircleOutlined, ArrowLeftOutlined } from "@ant-design/icons";
import { ProCard } from "@ant-design/pro-components";
import { Link, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import type { ColumnsType } from "antd/es/table";
import { inboundApi, itemApi, purchaseApi, warehouseApi } from "../../api";
import type { Item, Location, Warehouse } from "../../types";
import type { PurchaseOrder } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";

interface Line {
  itemId?: number;
  qty?: number;
  batchNo?: string;
  productionDate?: string;
  expiryDate?: string;
  supplier?: string;
  locationId?: number;
  serialNos?: string[];
  refLineId?: number;
}

export function InboundFormPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const user = getUser();

  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [purchaseOrders, setPurchaseOrders] = useState<PurchaseOrder[]>([]);
  const [refOrderId, setRefOrderId] = useState<number | undefined>();
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const [lines, setLines] = useState<Line[]>([{}]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    itemApi.list({ page: 1, pageSize: 200 }).then((r) => setItems(r.rows)).catch(() => undefined);
    // 可选关联:待审批/已审批的采购单
    Promise.all([
      purchaseApi.list({ status: "pending", page: 1, pageSize: 200 }),
      purchaseApi.list({ status: "approved", page: 1, pageSize: 200 }),
    ])
      .then(([a, b]) => {
        const seen = new Set<number>();
        setPurchaseOrders([...a.rows, ...b.rows].filter((o) => (seen.has(o.id) ? false : (seen.add(o.id), true))));
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (warehouseId) {
      warehouseApi.listLocations({ warehouseId, page: 1, pageSize: 200 })
        .then((r) => setLocations(r.rows))
        .catch(() => undefined);
    } else {
      setLocations([]);
    }
  }, [warehouseId]);

  const currentWarehouse = warehouses.find((w) => w.id === warehouseId);

  const addLine = () => setLines([...lines, {}]);
  const removeLine = (idx: number) =>
    setLines((prev) => prev.filter((_, i) => i !== idx));

  // 选关联采购单:加载订单行作为明细模板(物品/未收数量预填,行绑 refLineId)
  const onRefOrderChange = async (id?: number) => {
    setRefOrderId(id);
    if (!id) return;
    try {
      const order = await purchaseApi.get(id);
      const template: Line[] = (order.items ?? [])
        .filter((it) => Number(it.orderedQty) - Number(it.arrivedQty) > 0)
        .map((it) => ({
          itemId: it.itemId,
          qty: Number(it.orderedQty) - Number(it.arrivedQty),
          refLineId: it.id,
        }));
      if (template.length === 0) {
        message.warning("该采购单已无未收数量,已不生成模板行");
      } else {
        setLines(template);
      }
    } catch {
      // 拦截器已提示
    }
  };

  const updateLine = <K extends keyof Line>(idx: number, key: K, val: Line[K]) => {
    setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, [key]: val } : l)));
  };

  const onSubmit = async () => {
    if (!warehouseId) {
      message.warning("请先选择仓库");
      return;
    }
    if (lines.length === 0) {
      message.warning("至少添加 1 行");
      return;
    }
    for (let i = 0; i < lines.length; i++) {
      const l = lines[i];
      if (!l.itemId) {
        message.warning(`第 ${i + 1} 行:请选择物品`);
        return;
      }
      if (!l.qty || l.qty <= 0) {
        message.warning(`第 ${i + 1} 行:请填写数量`);
        return;
      }
      if (currentWarehouse?.enableExpiry && !l.batchNo) {
        message.warning(`第 ${i + 1} 行:启用保质期的仓库必须填写批次`);
        return;
      }
      if (currentWarehouse?.enableSerial) {
        if (!l.serialNos || l.serialNos.length !== l.qty) {
          message.warning(
            `第 ${i + 1} 行:启用序列号的仓库,序列号数量(${l.serialNos?.length ?? 0})必须等于入库数量(${l.qty})`,
          );
          return;
        }
      }
      if (currentWarehouse?.enableLocation && !l.locationId) {
        message.warning(`第 ${i + 1} 行:启用库位的仓库必须填写库位`);
        return;
      }
    }

    setSubmitting(true);
    try {
      await inboundApi.create({
        warehouseId,
        remark: form.getFieldValue("remark"),
        items: lines.map((l) => ({
          itemId: l.itemId!,
          qty: l.qty!,
          batchNo: l.batchNo,
          productionDate: l.productionDate,
          expiryDate: l.expiryDate,
          supplier: l.supplier,
          locationId: l.locationId,
          serialNos: l.serialNos,
          refLineId: l.refLineId,
        })),
        refType: refOrderId ? "purchase" : undefined,
        refDocId: refOrderId,
      });
      message.success("入库成功");
      navigate("/inbound?refresh=" + Date.now());
    } catch {
      // 拦截器已处理
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<{ idx: number; line: Line }> = [
    {
      title: "#",
      width: 50,
      render: (_v, _r, idx) => idx + 1,
    },
    {
      title: "物品",
      width: 220,
      render: (_v, r) => (
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="选择物品"
          style={{ width: "100%" }}
          value={r.line.itemId}
          options={items.map((it) => ({
            label: `${it.itemCode} - ${it.itemName}`,
            value: it.id,
          }))}
          onChange={(v) => updateLine(r.idx, "itemId", v)}
        />
      ),
    },
    {
      title: "数量",
      width: 100,
      render: (_v, r) => (
        <InputNumber
          min={0}
          step={1}
          precision={0}
          style={{ width: "100%" }}
          value={r.line.qty}
          onChange={(v) =>
            updateLine(r.idx, "qty", v == null ? undefined : Number(v))
          }
        />
      ),
    },
    {
      title: "关联订单行",
      width: 110,
      render: (_v, r) =>
        r.line.refLineId ? <Tag color="blue">已绑行</Tag> : <span style={{ color: "#999" }}>-</span>,
    },
    {
      title: "批次号",
      width: 160,
      render: (_v, r) =>
        currentWarehouse?.enableBatch ? (
          <Input
            placeholder="留空自动建批"
            value={r.line.batchNo}
            onChange={(e) => updateLine(r.idx, "batchNo", e.target.value)}
          />
        ) : (
          <Tag>未启用</Tag>
        ),
    },
    {
      title: "到期日期",
      width: 160,
      render: (_v, r) =>
        currentWarehouse?.enableExpiry ? (
          <DatePicker
            style={{ width: "100%" }}
            value={r.line.expiryDate ? dayjs(r.line.expiryDate) : null}
            onChange={(d) =>
              updateLine(
                r.idx,
                "expiryDate",
                d ? d.format("YYYY-MM-DD") : undefined,
              )
            }
          />
        ) : (
          <Tag>未启用</Tag>
        ),
    },
    {
      title: "库位",
      width: 150,
      render: (_v, r) =>
        currentWarehouse?.enableLocation ? (
          <Select
            allowClear
            placeholder="选择库位"
            style={{ width: "100%" }}
            value={r.line.locationId}
            options={locations.map((l) => ({
              label: l.locationCode,
              value: l.id,
            }))}
            onChange={(v) => updateLine(r.idx, "locationId", v)}
          />
        ) : (
          <Tag>未启用</Tag>
        ),
    },
    {
      title: "序列号",
      width: 280,
      render: (_v, r) => {
        if (!currentWarehouse?.enableSerial) {
          return <Tag>未启用</Tag>;
        }
        const serials = r.line.serialNos ?? [];
        const qty = r.line.qty ?? 0;
        const match = qty > 0 && serials.length === qty;
        return (
          <div>
            <Select
              mode="tags"
              placeholder="输入序列号后回车"
              style={{ width: "100%" }}
              value={serials}
              onChange={(v) => updateLine(r.idx, "serialNos", v)}
              tokenSeparators={[",", " ", "\n"]}
            />
            <div className="serial-error-text">
              {qty > 0 && !match
                ? `需 ${qty} 个序列号,当前 ${serials.length} 个`
                : qty > 0 && match
                  ? "已匹配"
                  : "填写数量后将自动校验"}
            </div>
          </div>
        );
      },
    },
    {
      title: "操作",
      width: 50,
      render: (_v, r) => (
        <Button
          danger
          type="link"
          icon={<DeleteOutlined />}
          onClick={() => removeLine(r.idx)}
          disabled={lines.length <= 1}
        />
      ),
    },
  ];

  return (
    <>
      {/* 页面头部:返回 + 标题 */}
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/inbound">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">新建入库单</h1>
      </div>

      {/* 单卡片布局:表头 + 明细 + 底部固定操作条 */}
      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
          {/* 表头:统一 grid + md 宽度,四列对齐 */}
          <Form form={form} layout="vertical" requiredMark={false}>
            <Row gutter={16}>
              <Col span={6}>
                <Form.Item label="仓库" required>
                  <Select
                    placeholder="选择仓库"
                    value={warehouseId}
                    options={warehouses.map((w) => ({
                      label: `${w.warehouseCode} - ${w.warehouseName}`,
                      value: w.id,
                    }))}
                    onChange={setWarehouseId}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item
                  label="关联采购订单"
                  tooltip="选后自动加载订单行作为明细模板(物品/未收数量),提交时回写采购单到货进度"
                >
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="可选,选后加载订单行模板"
                    value={refOrderId}
                    options={purchaseOrders.map((o) => ({
                      label: `${o.docNo} (${o.status === "pending" ? "待审批" : "已审批"})`,
                      value: o.id,
                    }))}
                    onChange={(v) => void onRefOrderChange(v)}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="操作人">
                  <Input
                    value={`${user?.name ?? ""} (${user?.username ?? ""})`}
                    disabled
                    prefix={<InfoCircleOutlined style={{ color: "#9ca3af" }} />}
                  />
                </Form.Item>
              </Col>
            </Row>
            {/* 备注独占一行,与采购/销售页统一 */}
            <Form.Item label="备注" name="remark">
              <Input placeholder="可选" />
            </Form.Item>
          </Form>
          {currentWarehouse && (
            <div style={{ fontSize: 12, color: "#6b7280", marginBottom: 8 }}>
              当前仓库配置:
              <Tag style={{ marginLeft: 8 }}>
                批次 {currentWarehouse.enableBatch ? "✓" : "✗"}
              </Tag>
              <Tag>保质期 {currentWarehouse.enableExpiry ? "✓" : "✗"}</Tag>
              <Tag>序列号 {currentWarehouse.enableSerial ? "✓" : "✗"}</Tag>
              <Tag>库位 {currentWarehouse.enableLocation ? "✓" : "✗"}</Tag>
            </div>
          )}

          <div className="doc-form-section-title">入库明细</div>
          {/* 明细保留原手工编辑 Table,加纵向滚动使底部操作条始终可见 */}
          <Table
            rowKey="idx"
            columns={columns}
            dataSource={lines.map((l, i) => ({ idx: i, line: l }))}
            pagination={false}
            size="small"
            scroll={{ x: 1300 }}
          />
        </div>
        {/* 底部固定操作条:左摘要,右 取消/添加行/提交 */}
        <div className="doc-form-footer">
          <div className="doc-form-footer-summary">共 {lines.length} 行明细</div>
          <div className="doc-form-footer-actions">
            <Button onClick={() => navigate("/inbound")}>取消</Button>
            <Button icon={<PlusOutlined />} onClick={addLine}>
              添加行
            </Button>
            <Button type="primary" loading={submitting} onClick={onSubmit}>
              提交入库
            </Button>
          </div>
        </div>
      </ProCard>
    </>
  );
}
