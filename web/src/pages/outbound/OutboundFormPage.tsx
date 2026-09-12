// 新建出库单(SPEC-WEB V2 2.6)
// 布局与采购/销售统一:页面头部 + 单卡片(表头 + 明细 + 底部固定操作条)
// 明细保留原 antd Table 手工编辑机制(关联销售单行模板/预占批次提示联动不改)

import { useEffect, useState } from "react";
import {
  Form,
  Row,
  Col,
  Select,
  Input,
  InputNumber,
  Button,
  Table,
  Tag,
  App,
} from "antd";
import { PlusOutlined, DeleteOutlined, InfoCircleOutlined, ArrowLeftOutlined } from "@ant-design/icons";
import { ProCard } from "@ant-design/pro-components";
import { Link, useNavigate } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import { itemApi, outboundApi, salesApi, stockApi, warehouseApi } from "../../api";
import type { Item, Location, Warehouse } from "../../types";
import type { SalesOrder } from "../../types/phase1";
import { getUser } from "../../auth/useAuth";

interface Line {
  itemId?: number;
  qty?: number;
  batchNo?: string;
  locationId?: number;
  serialNos?: string[];
  refLineId?: number;
  /** 预占批次提示(关联销售单时展示) */
  preAllocHint?: string;
}

export function OutboundFormPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const user = getUser();

  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [salesOrders, setSalesOrders] = useState<SalesOrder[]>([]);
  const [refOrderId, setRefOrderId] = useState<number | undefined>();
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const [lines, setLines] = useState<Line[]>([{}]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    itemApi.list({ page: 1, pageSize: 200 }).then((r) => setItems(r.rows)).catch(() => undefined);
    // 可选关联:待审批/已审批的销售单
    Promise.all([
      salesApi.list({ status: "pending", page: 1, pageSize: 200 }),
      salesApi.list({ status: "approved", page: 1, pageSize: 200 }),
    ])
      .then(([a, b]) => {
        const seen = new Set<number>();
        setSalesOrders([...a.rows, ...b.rows].filter((o) => (seen.has(o.id) ? false : (seen.add(o.id), true))));
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

  // 选关联销售单:仓库跟随订单仓;加载订单行模板(未发数量),并提示预占批次
  const onRefOrderChange = async (id?: number) => {
    setRefOrderId(id);
    if (!id) return;
    try {
      const order = await salesApi.get(id);
      // 仓库跟随销售订单(可手动改)
      if (order.warehouseId) setWarehouseId(order.warehouseId);
      const pending: Line[] = (order.items ?? [])
        .filter((it) => Number(it.orderedQty) - Number(it.shippedQty) > 0)
        .map((it) => ({
          itemId: it.itemId,
          qty: Number(it.orderedQty) - Number(it.shippedQty),
          refLineId: it.id,
        }));
      if (pending.length === 0) {
        message.warning("该销售单已无未发数量,已不生成模板行");
        setLines(pending);
        return;
      }
      // 逐行查库存(按物品编码关键词),预占批次优先提示并预填批次号
      const withHint: Line[] = [];
      for (const l of pending) {
        try {
          const itemCode = items.find((it) => it.id === l.itemId)?.itemCode;
          const r = await stockApi.query({
            warehouseId: order.warehouseId,
            itemKeyword: itemCode,
            page: 1,
            pageSize: 50,
          });
          const alloc = (r.rows ?? [])
            .filter((s) => Number(s.preAllocatedQty) > 0 && s.batch?.batchNo)
            .sort((a, b) => Number(b.preAllocatedQty) - Number(a.preAllocatedQty));
          if (alloc.length > 0) {
            const b0 = alloc[0];
            withHint.push({
              ...l,
              batchNo: b0.batch?.batchNo ?? undefined,
              preAllocHint: `预占 ${Number(b0.preAllocatedQty).toLocaleString()} 件,已优先预填 ${b0.batch?.batchNo}`,
            });
          } else {
            withHint.push(l);
          }
        } catch {
          withHint.push(l);
        }
      }
      setLines(withHint);
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
      if (currentWarehouse?.enableSerial) {
        if (!l.serialNos || l.serialNos.length !== l.qty) {
          message.warning(
            `第 ${i + 1} 行:启用序列号的仓库,序列号数量(${l.serialNos?.length ?? 0})必须等于出库数量(${l.qty})`,
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
      await outboundApi.create({
        warehouseId,
        remark: form.getFieldValue("remark"),
        items: lines.map((l) => ({
          itemId: l.itemId!,
          qty: l.qty!,
          batchNo: l.batchNo,
          locationId: l.locationId,
          serialNos: l.serialNos,
          refLineId: l.refLineId,
        })),
        refType: refOrderId ? "sales" : undefined,
        refDocId: refOrderId,
      });
      message.success("出库成功");
      navigate("/outbound?refresh=" + Date.now());
    } catch {
      // 拦截器已处理
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<{ idx: number; line: Line }> = [
    { title: "#", width: 50, render: (_v, _r, idx) => idx + 1 },
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
      title: "批次号",
      width: 180,
      render: (_v, r) =>
        currentWarehouse?.enableBatch ? (
          <div>
            <Input
              placeholder="留空按 FEFO/FIFO 自动选批"
              value={r.line.batchNo}
              onChange={(e) => updateLine(r.idx, "batchNo", e.target.value)}
            />
            {r.line.preAllocHint && (
              <div style={{ fontSize: 12, color: "#1d4ed8", marginTop: 2 }}>
                {r.line.preAllocHint}
              </div>
            )}
          </div>
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
              placeholder="输入序列号后回车(可手填)"
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
        <Link className="doc-page-back" to="/outbound">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">新建出库单</h1>
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
                  label="关联销售订单"
                  tooltip="选后仓库跟随订单仓、自动加载订单行模板(未发数量),提交时回写发货进度并释放预占"
                >
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="可选,选后加载订单行模板"
                    value={refOrderId}
                    options={salesOrders.map((o) => ({
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

          <div className="doc-form-section-title">出库明细</div>
          {/* 明细保留原手工编辑 Table,加纵向滚动使底部操作条始终可见 */}
          <Table
            rowKey="idx"
            columns={columns}
            dataSource={lines.map((l, i) => ({ idx: i, line: l }))}
            pagination={false}
            size="small"
            scroll={{ x: 1200 }}
          />
        </div>
        {/* 底部固定操作条:左摘要,右 取消/添加行/提交 */}
        <div className="doc-form-footer">
          <div className="doc-form-footer-summary">共 {lines.length} 行明细</div>
          <div className="doc-form-footer-actions">
            <Button onClick={() => navigate("/outbound")}>取消</Button>
            <Button icon={<PlusOutlined />} onClick={addLine}>
              添加行
            </Button>
            <Button type="primary" loading={submitting} onClick={onSubmit}>
              提交出库
            </Button>
          </div>
        </div>
      </ProCard>
    </>
  );
}
