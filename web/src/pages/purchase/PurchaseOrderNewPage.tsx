// 新建采购订单(Pro 版:ProForm 表头 + EditableProTable 受控行明细)
// 服务端重算价税三列,前端仅展示输入;行级:物品/数量/单价/税率
// 选物品后自动带出默认税率(联动走数据流,不依赖 setFields)

import { useEffect, useState, type ComponentProps } from "react";
import { Button, InputNumber, Tooltip } from "antd";
import { ArrowLeftOutlined, ExclamationCircleFilled } from "@ant-design/icons";
import {
  EditableProTable,
  ProCard,
  ProForm,
  ProFormDatePicker,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from "@ant-design/pro-components";
import type { ProColumns } from "@ant-design/pro-components";
import { Link, useNavigate, useParams } from "react-router-dom";
import { App } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, purchaseApi, supplierApi, userApi } from "../../api";
import type { Item, UserInfo } from "../../types";
import type { Supplier } from "../../types/phase1";
import { fmtMoney } from "../../utils/format";
import { priceMismatchHint, previewLineMoney, recalcPricePair } from "../../utils/lineMoney";

interface LineRow {
  key: number;
  itemId?: number;
  orderedQty?: number;
  expectedDeliveryDate?: Dayjs | string;
  unitPrice?: number;
  // V20 含税单价:与不含税单价二选一,都填时服务端按不含税优先
  taxPrice?: number;
  taxRate?: number;
  lineRemark?: string;
}

let lineSeq = 2;

// V23.1:含税单价输入框,与不含税单价按税率推算不一致时在输入框右侧同行渲染小图标 + Tooltip 悬浮展示完整文案
// value/onChange 由行表单 Form.Item 注入,unitPrice/taxRate 由列 renderFormItem 从行记录带入
function TaxPriceField(props: ComponentProps<typeof InputNumber> & {
  unitPrice?: number;
  taxRate?: number;
}) {
  const { unitPrice, taxRate, value, ...inputProps } = props;
  const hint = priceMismatchHint(unitPrice, value == null ? undefined : Number(value), taxRate);
  return (
    <span style={{ display: "inline-flex", alignItems: "center", width: "100%" }}>
      <InputNumber {...inputProps} value={value} style={{ width: "calc(100% - 22px)" }} />
      {hint != null ? (
        <Tooltip title={hint}>
          <ExclamationCircleFilled
            style={{ color: "#fa8c16", fontSize: 14, marginLeft: 6, cursor: "help" }}
          />
        </Tooltip>
      ) : null}
    </span>
  );
}

export function PurchaseOrderNewPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { id: editIdParam } = useParams<{ id?: string }>();
  // 路由 /purchase-orders/new/:id? 携带 id 时为编辑模式(草稿/已驳回单)
  const editId = editIdParam ? Number(editIdParam) : null;
  const [docNo, setDocNo] = useState("");
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

  // 编辑模式:GET 详情回填表头 + 行明细(行 key 用 1..n,与新建的自增 key 规则一致)
  useEffect(() => {
    if (editId == null) return;
    purchaseApi
      .get(editId)
      .then((doc) => {
        setDocNo(doc.docNo);
        headerForm.setFieldsValue({
          docDate: dayjs(doc.docDate),
          supplierId: doc.supplierId,
          buyerId: doc.buyerId,
          // 服务端小数口径(0.1=10%)→ UI 百分数回填
          allowOverReceiptRate: Number(doc.allowOverReceiptRate) * 100,
          contractNo: doc.contractNo ?? undefined,
          freight: doc.freight == null ? undefined : Number(doc.freight),
          shippingAddress: doc.shippingAddress ?? undefined,
          // V10 通用字段(可空):币种/汇率/折扣额
          currencyCode: doc.currencyCode ?? undefined,
          exchangeRate: doc.exchangeRate == null ? undefined : Number(doc.exchangeRate),
          discountAmount: doc.discountAmount == null ? undefined : Number(doc.discountAmount),
          remark: doc.remark ?? undefined,
        });
        const rows: LineRow[] = (doc.items ?? []).map((l, i) => ({
          key: i + 1,
          itemId: l.itemId,
          orderedQty: Number(l.orderedQty),
          expectedDeliveryDate: l.expectedDeliveryDate ? dayjs(l.expectedDeliveryDate) : undefined,
          unitPrice: Number(l.unitPrice),
          taxPrice: l.taxPrice != null ? Number(l.taxPrice) : undefined,
          taxRate: Number(l.taxRate),
          lineRemark: l.lineRemark ?? undefined,
        }));
        setData(rows);
        setEditableKeys(rows.map((r) => r.key));
        // 防止新建时"添加行"自增 key 与回填行 key 冲突
        lineSeq = Math.max(lineSeq, rows.length + 1);
      })
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId]);

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
    const merged: LineRow[] = values.map((row, idx) => {
      const prev = data[idx];
      let next = { ...row };
      if (row.itemId != null) {
        const item = items.find((i) => i.id === row.itemId);
        // 联动 1:选物品且税率未填 → 带出默认税率
        if (next.taxRate == null && item) {
          next.taxRate = Number(item.defaultTaxRate ?? 13);
        }
        // 联动 2(V10):选物品且单价未填且物品有参考采购价 → 预填(仅预填,用户可改)
        if (next.unitPrice == null && item?.referencePurchasePrice != null) {
          next.unitPrice = Number(item.referencePurchasePrice);
        }
      }
      // V23.1:双单价双向联动——按用户刚改的字段重算另一个(最后修改的字段是主字段);
      // 清空某框时 recalcPricePair 返回空对象不动另一个(否则无法清空重填)
      if (next.taxRate != null) {
        const changed: "unit" | "tax" | "rate" | null =
          prev?.unitPrice !== next.unitPrice
            ? "unit"
            : prev?.taxPrice !== next.taxPrice
              ? "tax"
              : prev?.taxRate !== next.taxRate
                ? "rate"
                : null;
        if (changed) {
          next = { ...next, ...recalcPricePair(next.unitPrice, next.taxPrice, next.taxRate, changed) };
        }
      }
      return next;
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
      formItemProps: { rules: [{ required: false, message: "单价至少填一个" }] },
    },
    {
      // V20 含税单价:与不含税单价二选一(都填时服务端按不含税优先)
      // V23.1:自定义 renderFormItem,输入框右侧同行按行实时值渲染同值提示小图标 + Tooltip(纯展示,不阻断提交);
      // 行值变化走受控 data 刷新,cell 随表单行级 shouldUpdate 重渲染,提示始终反映当前行值
      title: "含税单价",
      dataIndex: "taxPrice",
      width: 120,
      valueType: "digit",
      renderFormItem: (_schema, config) => (
        <TaxPriceField
          min={0}
          step={0.01}
          style={{ width: "100%" }}
          unitPrice={config.record?.unitPrice}
          taxRate={config.record?.taxRate}
        />
      ),
    },
    {
      title: "税率(%)",
      dataIndex: "taxRate",
      width: 110,
      valueType: "digit",
      fieldProps: { min: 0, max: 100, step: 0.01 },
    },
    {
      // V20 金额三列:只读预览(与后端同口径),提交后以服务端重算为准
      title: "不含税金额",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      editable: false,
      render: (_v: unknown, r: LineRow) => {
        const pm = previewLineMoney(r.orderedQty ?? 0, r.unitPrice, r.taxPrice, r.taxRate);
        return pm ? fmtMoney(pm.amount) : "-";
      },
    },
    {
      title: "税额",
      width: 100,
      align: "right",
      className: "num-cell",
      search: false,
      editable: false,
      render: (_v: unknown, r: LineRow) => {
        const pm = previewLineMoney(r.orderedQty ?? 0, r.unitPrice, r.taxPrice, r.taxRate);
        return pm ? fmtMoney(pm.tax) : "-";
      },
    },
    {
      title: "含税金额",
      width: 110,
      align: "right",
      className: "num-cell",
      search: false,
      editable: false,
      render: (_v: unknown, r: LineRow) => {
        const pm = previewLineMoney(r.orderedQty ?? 0, r.unitPrice, r.taxPrice, r.taxRate);
        return pm ? fmtMoney(pm.inclusive) : "-";
      },
    },
    {
      title: "行备注",
      dataIndex: "lineRemark",
      valueType: "text",
    },
    {
      // 显式操作列:EditableProTable 自动 option 列在 scroll 布局下不渲染 td,与入库/销售页同款显式删除;
      // editable: false 使操作列不参与行表单(否则删除按钮被 Form.Item 包裹,空行无法点删)
      title: "操作",
      width: 80,
      editable: false,
      render: (_v: unknown, r: LineRow) => (
        <a onClick={() => removeLine(r.key)}>删除</a>
      ),
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
    // V20:不含税单价/含税单价至少填一个(都填时服务端按不含税优先)
    const validLines = data.filter(
      (l) => l.itemId && l.orderedQty && (l.unitPrice != null || l.taxPrice != null),
    );
    if (validLines.length === 0) {
      message.error("请至少填写一行订单明细");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        docDate: (values.docDate as Dayjs).format("YYYY-MM-DD"),
        supplierId: values.supplierId as number,
        buyerId: values.buyerId as number,
        // UI 百分数(0~100)→ 提交服务端小数口径(0.1=10%)
        allowOverReceiptRate: ((values.allowOverReceiptRate as number) ?? 0) / 100,
        contractNo: values.contractNo as string | undefined,
        freight: values.freight as number | undefined,
        shippingAddress: values.shippingAddress as string | undefined,
        // V10 通用字段(可空)
        currencyCode: values.currencyCode as string | undefined,
        exchangeRate: values.exchangeRate as number | undefined,
        discountAmount: values.discountAmount as number | undefined,
        remark: values.remark as string | undefined,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          orderedQty: l.orderedQty!,
          expectedDeliveryDate:
            typeof l.expectedDeliveryDate === "string"
              ? l.expectedDeliveryDate
              : l.expectedDeliveryDate?.format("YYYY-MM-DD"),
          unitPrice: l.unitPrice ?? undefined,
          taxPrice: l.taxPrice ?? undefined,
          taxRate: l.taxRate ?? 13,
          lineRemark: l.lineRemark,
        })),
      };
      if (editId != null) {
        await purchaseApi.update(editId, payload);
        message.success("采购订单已保存(仍为可编辑状态)");
      } else {
        await purchaseApi.create(payload);
        message.success("采购订单已创建(草稿)");
      }
      navigate("/purchase-orders");
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      {/* 页面头部:返回 + 标题 + 编辑态单号摘要 */}
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/purchase-orders">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">
          {editId != null ? "编辑采购订单" : "新建采购订单"}
        </h1>
        {editId != null && <span className="doc-page-meta">单号 {docNo || "..."}</span>}
      </div>

      {/* 单卡片布局:表头 + 明细 + 底部固定操作条 */}
      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
        <ProForm
          form={headerForm}
          layout="vertical"
          grid
          submitter={false}
        >
          <ProFormDatePicker
            name="docDate"
            label="下单日期"
            colProps={{ span: 6 }}
            initialValue={dayjs()}
            rules={[{ required: true, message: "请选择下单日期" }]}
          />
          <ProFormSelect
            name="supplierId"
            label="供应商"
            colProps={{ span: 6 }}
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
            colProps={{ span: 6 }}
            options={users.map((u) => ({ label: u.name, value: u.id }))}
            rules={[{ required: true, message: "请选择采购员" }]}
          />
          <ProFormDigit
            name="allowOverReceiptRate"
            label="允许超收比例(%)"
            colProps={{ span: 6 }}
            initialValue={0}
            min={0}
            max={100}
            fieldProps={{ step: 1 }}
            tooltip="到货量上限 = 订购量 × (1 + 比例)"
          />
          {/* V9 通用字段(可空):合同号/运费/交货地址 */}
          <ProFormText
            name="contractNo"
            label="合同号"
            colProps={{ span: 6 }}
          />
          <ProFormDigit
            name="freight"
            label="运费"
            colProps={{ span: 6 }}
            min={0}
            fieldProps={{ step: 0.01 }}
          />
          <ProFormText
            name="shippingAddress"
            label="交货地址"
            colProps={{ span: 12 }}
          />
          {/* V10 通用字段(可空):币种(默认 CNY)/汇率(默认 1)/折扣额 */}
          <ProFormText
            name="currencyCode"
            label="币种"
            colProps={{ span: 6 }}
            initialValue="CNY"
            fieldProps={{ placeholder: "默认 CNY" }}
          />
          <ProFormDigit
            name="exchangeRate"
            label="汇率"
            colProps={{ span: 6 }}
            initialValue={1}
            min={0}
            fieldProps={{ step: 0.000001 }}
          />
          <ProFormDigit
            name="discountAmount"
            label="折扣额"
            colProps={{ span: 6 }}
            min={0}
            fieldProps={{ step: 0.01 }}
            tooltip="仅存字段,不参与合计计算"
          />
          <ProFormTextArea
            name="remark"
            label="备注"
            colProps={{ span: 24 }}
            fieldProps={{ rows: 2 }}
          />
        </ProForm>
        <div className="doc-form-section-title">订单明细</div>
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
          scroll={{ x: 900, y: "calc(100vh - 520px)" }}
          editable={{
            type: "multiple",
            form: lineForm,
            editableKeys,
            onChange: (keys) => setEditableKeys(keys),
            // 行删除走显式操作列(removeLine),自动 option 列在 scroll 布局下不渲染 body 已弃用
          }}
        />
        {/* 添加行按钮:明细表正下方,左对齐 */}
        <div className="doc-form-line-adder">
          <Button onClick={addLine}>添加行</Button>
        </div>
        </div>
        {/* 底部固定操作条:左摘要,中间主按钮居中 */}
        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {data.length} 行明细
              <span className="doc-form-footer-muted">金额以服务端价税重算为准</span>
            </div>
          </div>
          <div className="doc-form-footer-main">
            <Button type="primary" loading={saving} onClick={onSubmit}>
              {editId != null ? "保存" : "保存为草稿"}
            </Button>
          </div>
        </div>
      </ProCard>
    </>
  );
}
