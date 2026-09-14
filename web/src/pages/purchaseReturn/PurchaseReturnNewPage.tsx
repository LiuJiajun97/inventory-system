// 新建采购退货单(V11):create 即过账,提交同事务生成出库单扣库存
// 布局与采购/销售新建统一:页面头部 + 全宽卡片 + 底部固定操作条
// 步骤:选原采购单(approved/completed/closed) → 拉原单行(单价/税率只读锁原价) →
// 每行填退货数量(上限=已到货-已退,超了红字),提交 = 过账

import { useEffect, useState } from "react";
import { Form, Row, Col, Select, Input, InputNumber, Button, Table, Tag, DatePicker, App } from "antd";
import { PlusOutlined, ArrowLeftOutlined } from "@ant-design/icons";
import { fmtMoney, fmtQty } from "../../utils/format";
import { previewLineMoney } from "../../utils/lineMoney";
import { ProCard } from "@ant-design/pro-components";
import { Link, useNavigate } from "react-router-dom";
import type { ColumnsType } from "antd/es/table";
import dayjs, { type Dayjs } from "dayjs";
import { purchaseApi, purchaseReturnApi, warehouseApi } from "../../api";
import type { Warehouse } from "../../types";
import type { PurchaseOrder } from "../../types/phase1";

interface Row {
  key: number;
  refLineId: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  specSnapshot?: string | null;
  unit: string;
  unitPrice: number;
  // V20 含税单价:继承原订单行快照,只读展示
  taxPrice: number;
  taxRate: number;
  arrivedQty: number;
  returnedQty: number;
  returnableQty: number;
  qty?: number;
  serialNos?: string[];
}

export function PurchaseReturnNewPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm();

  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [orderId, setOrderId] = useState<number | undefined>();
  const [warehouseId, setWarehouseId] = useState<number | undefined>();
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
    // 可选原单:已审批/已完成/已关闭(有可退量的都会展示)
    Promise.all([
      purchaseApi.list({ status: "approved", page: 1, pageSize: 200 }),
      purchaseApi.list({ status: "completed", page: 1, pageSize: 200 }),
      purchaseApi.list({ status: "closed", page: 1, pageSize: 200 }),
    ])
      .then(([a, b, c]) => {
        const seen = new Set<number>();
        setOrders(
          [...a.rows, ...b.rows, ...c.rows].filter((o) =>
            seen.has(o.id) ? false : (seen.add(o.id), true),
          ),
        );
      })
      .catch(() => undefined);
  }, []);

  const currentWarehouse = warehouses.find((w) => w.id === warehouseId);

  // 选原采购单:拉详情生成行模板(可退量 = 已到货 - 已退)
  const onOrderChange = async (id?: number) => {
    setOrderId(id);
    setRows([]);
    if (!id) return;
    setLoading(true);
    try {
      const order = await purchaseApi.get(id);
      const next: Row[] = (order.items ?? [])
        .map((it, i) => {
          const arrived = Number(it.arrivedQty);
          const returned = Number(it.returnedQty ?? "0");
          return {
            key: i + 1,
            refLineId: it.id,
            itemId: it.itemId,
            itemCode: it.itemCode,
            itemName: it.itemName,
            specSnapshot: it.specSnapshot,
            unit: it.unit,
            unitPrice: Number(it.unitPrice),
            taxPrice: it.taxPrice != null ? Number(it.taxPrice) : 0,
            taxRate: Number(it.taxRate ?? 0),
            arrivedQty: arrived,
            returnedQty: returned,
            returnableQty: arrived - returned,
          };
        })
        .filter((r) => r.returnableQty > 0);
      if (next.length === 0) {
        message.warning("该采购单已无可退数量");
      }
      setRows(next);
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false);
    }
  };

  const updateRow = (idx: number, patch: Partial<Row>) =>
    setRows((prev) => prev.map((r, i) => (i === idx ? { ...r, ...patch } : r)));

  const onSubmit = async () => {
    if (!orderId) {
      message.warning("请先选择原采购订单");
      return;
    }
    if (!warehouseId) {
      message.warning("请选择退货仓库");
      return;
    }
    const validRows = rows.filter((r) => r.qty && r.qty > 0);
    if (validRows.length === 0) {
      message.warning("请至少填写一行的退货数量");
      return;
    }
    for (let i = 0; i < validRows.length; i++) {
      const r = validRows[i];
      if (r.qty! > r.returnableQty) {
        message.warning(`第 ${i + 1} 行:退货数量超过可退数量(${r.returnableQty})`);
        return;
      }
      if (currentWarehouse?.enableSerial && (r.serialNos?.length ?? 0) !== r.qty) {
        message.warning(
          `第 ${i + 1} 行:启用序列号的仓库,序列号数量必须等于退货数量(${r.qty})`,
        );
        return;
      }
    }
    setSubmitting(true);
    try {
      await purchaseReturnApi.create({
        purchaseOrderId: orderId,
        warehouseId,
        docDate: dayjs(form.getFieldValue("docDate") ?? new Date()).format("YYYY-MM-DD"),
        remark: form.getFieldValue("remark") || undefined,
        items: validRows.map((r) => ({
          purchaseOrderItemId: r.refLineId,
          quantity: r.qty!,
          serialNos: currentWarehouse?.enableSerial ? r.serialNos : undefined,
        })),
      });
      message.success("退货单已提交并过账");
      navigate("/purchase-returns?refresh=" + Date.now());
    } catch {
      // 拦截器已处理
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<Row> = [
    { title: "行号", width: 50, render: (_v, _r, idx) => idx + 1 },
    {
      title: "物品",
      width: 200,
      render: (_v, r) => (
        <div>
          <div>{r.itemName}</div>
          <div style={{ fontSize: 12, color: "#9ca3af" }}>{r.itemCode}</div>
        </div>
      ),
    },
    {
      title: "规格",
      width: 120,
      ellipsis: true,
      render: (_v, r) => r.specSnapshot ?? "-",
    },
    {
      // 单价/税率锁原行快照,只读展示
      title: "不含税单价",
      width: 110,
      align: "right",
      render: (_v, r) => fmtMoney(r.unitPrice),
    },
    {
      // V20 含税单价锁原行快照,只读展示
      title: "含税单价",
      width: 110,
      align: "right",
      render: (_v, r) => (r.taxPrice ? fmtMoney(r.taxPrice) : "-"),
    },
    {
      title: "税率(%)",
      width: 110,
      align: "right",
      render: (_v, r) => r.taxRate.toFixed(2),
    },
    {
      // V20 金额三列:按原行快照(不含税单价×退货数量,税率)实时预览,服务端落库快照
      title: "不含税金额",
      width: 110,
      align: "right",
      render: (_v, r) => {
        const pm = previewLineMoney(r.qty ?? 0, r.unitPrice, r.taxPrice, r.taxRate);
        return pm ? fmtMoney(pm.amount) : "-";
      },
    },
    {
      title: "税额",
      width: 100,
      align: "right",
      render: (_v, r) => {
        const pm = previewLineMoney(r.qty ?? 0, r.unitPrice, r.taxPrice, r.taxRate);
        return pm ? fmtMoney(pm.tax) : "-";
      },
    },
    {
      title: "含税金额",
      width: 110,
      align: "right",
      render: (_v, r) => {
        const pm = previewLineMoney(r.qty ?? 0, r.unitPrice, r.taxPrice, r.taxRate);
        return pm ? fmtMoney(pm.inclusive) : "-";
      },
    },
    {
      title: "已到货",
      width: 90,
      align: "right",
      render: (_v, r) => fmtQty(r.arrivedQty),
    },
    {
      title: "已退",
      width: 90,
      align: "right",
      render: (_v, r) => fmtQty(r.returnedQty),
    },
    {
      title: "可退量",
      width: 90,
      align: "right",
      render: (_v, r) => fmtQty(r.returnableQty),
    },
    {
      title: "退货数量",
      width: 130,
      render: (_v, r) => (
        <div>
          <InputNumber
            min={0}
            step={1}
            style={{ width: "100%" }}
            value={r.qty}
            onChange={(v) => updateRow(r.key - 1, { qty: v == null ? undefined : Number(v) })}
          />
          {r.qty != null && r.qty > r.returnableQty && (
            <div className="serial-error-text">
              超过可退数量({r.returnableQty})
            </div>
          )}
        </div>
      ),
    },
    {
      title: "序列号",
      width: 280,
      render: (_v, r) => {
        if (!currentWarehouse?.enableSerial) return <Tag>未启用</Tag>;
        const serials = r.serialNos ?? [];
        const qty = r.qty ?? 0;
        const match = qty > 0 && serials.length === qty;
        return (
          <div>
            <Select
              mode="tags"
              placeholder="输入序列号后回车(可手填)"
              style={{ width: "100%" }}
              value={serials}
              onChange={(v) => updateRow(r.key - 1, { serialNos: v })}
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
  ];

  const validRows = rows.filter((r) => r.qty && r.qty > 0);
  const totalAmount = validRows.reduce((s, r) => s + r.qty! * r.unitPrice, 0);

  return (
    <>
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/purchase-returns">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">新建采购退货单</h1>
      </div>

      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
          <Form form={form} layout="vertical">
            <Row gutter={16}>
              <Col span={6}>
                <Form.Item
                  label="原采购订单"
                  required
                  tooltip="仅已审批(含已完成/已关闭)的采购单可退货"
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择原采购订单"
                    value={orderId}
                    options={orders.map((o) => ({
                      label: `${o.docNo}(${o.status === "approved" ? "已审批" : o.status === "completed" ? "已完成" : "已关闭"})`,
                      value: o.id,
                    }))}
                    onChange={(v) => void onOrderChange(v)}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="退货仓库" required>
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
                <Form.Item label="单据日期" required>
                  <DatePicker
                    style={{ width: "100%" }}
                    defaultValue={dayjs()}
                    onChange={(v: Dayjs | null) => form.setFieldValue("docDate", v ?? undefined)}
                  />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="备注">
                  <Input placeholder="可选" allowClear />
                </Form.Item>
              </Col>
            </Row>
          </Form>

          {currentWarehouse && (
            <div style={{ fontSize: 12, color: "#6b7280", marginBottom: 8 }}>
              当前仓库配置:
              <Tag style={{ marginLeft: 8 }}>批次 {currentWarehouse.enableBatch ? "✓" : "✗"}</Tag>
              <Tag>保质期 {currentWarehouse.enableExpiry ? "✓" : "✗"}</Tag>
              <Tag>序列号 {currentWarehouse.enableSerial ? "✓" : "✗"}</Tag>
              <Tag>库位 {currentWarehouse.enableLocation ? "✓" : "✗"}</Tag>
            </div>
          )}

          <div className="doc-form-section-title">
            退货明细{orderId ? "" : "(请先选择原采购订单)"}
          </div>
          <Table
            rowKey="key"
            columns={columns}
            dataSource={rows}
            loading={loading}
            pagination={false}
            size="small"
            scroll={{ x: 1300 }}
            locale={{ emptyText: orderId ? "该采购单已无可退数量" : "选择原采购订单后自动生成行" }}
          />
        </div>

        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {validRows.length} 行退货
              {validRows.length > 0 && (
                <span className="doc-form-footer-muted">
                  金额约 {fmtMoney(totalAmount)}(以服务端重算为准)
                </span>
              )}
            </div>
            <Button onClick={() => navigate("/purchase-returns")}>取消</Button>
          </div>
          <div className="doc-form-footer-main">
            <Button type="primary" loading={submitting} onClick={onSubmit} icon={<PlusOutlined />}>
              提交并过账
            </Button>
          </div>
        </div>
      </ProCard>
    </>
  );
}
