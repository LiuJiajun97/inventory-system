// 新建期初单(V13,创建即过账)
// 表头:仓库(必填)/单据日期/备注;明细行:物品(下拉过滤已期初物品)/数量/期初单价(可选)
// 批次号(批次/保质期仓必填)/生产日期/到期日/库位(库位仓必填),底部"提交并过账"
// 后端校验为准:每物品×仓库限一次期初,过账走现有入库链路,失败整单回滚

import { useEffect, useMemo, useState } from "react";
import { Button } from "antd";
import { App } from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";
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
import dayjs, { type Dayjs } from "dayjs";
import { itemApi, openingApi, warehouseApi } from "../../api";
import type { Item, Location, Warehouse } from "../../types";

interface LineRow {
  key: number;
  itemId?: number;
  quantity?: number;
  unitPrice?: number;
  batchNo?: string;
  productionDate?: Dayjs | string;
  expiryDate?: Dayjs | string;
  locationId?: number;
}

let lineSeq = 2;

const toDay = (v?: Dayjs | string): string | undefined =>
  v == null ? undefined : typeof v === "string" ? v : v.format("YYYY-MM-DD");

export function OpeningStockNewPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  // 路由 /opening/new/:id? 携带 id 时为只读详情模式(复用新建表单回填)
  const { id: viewIdParam } = useParams<{ id?: string }>();
  const readonly = viewIdParam != null;
  const viewId = readonly ? Number(viewIdParam) : null;
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [openedItemIds, setOpenedItemIds] = useState<Set<number>>(new Set());
  // 当前选中仓库(驱动批次/库位必填与下拉数据)
  const [currentWh, setCurrentWh] = useState<Warehouse | null>(null);
  // 只读详情:表头仓库 id(等仓库列表加载完成后驱动 loadWhContext)
  const [headWhId, setHeadWhId] = useState<number | undefined>();
  const [data, setData] = useState<LineRow[]>(readonly ? [] : [{ key: 1 }]);
  const [editableKeys, setEditableKeys] = useState<React.Key[]>(readonly ? [] : [1]);
  const [saving, setSaving] = useState(false);
  const [headerForm] = ProForm.useForm();
  const [lineForm] = ProForm.useForm();

  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
  }, []);

  // 只读详情:GET 回填表头 + 明细行(行 key 用 1..n)
  useEffect(() => {
    if (viewId == null) return;
    let cancelled = false;
    openingApi
      .get(viewId)
      .then((d) => {
        if (cancelled) return;
        setHeadWhId(d.warehouseId);
        headerForm.setFieldsValue({
          warehouseId: d.warehouseId,
          docDate: dayjs(d.docDate),
          remark: d.remark ?? undefined,
        });
        setData(
          (d.items ?? []).map((it, i) => ({
            key: i + 1,
            itemId: it.itemId,
            quantity: Number(it.quantity),
            unitPrice: it.unitPrice == null ? undefined : Number(it.unitPrice),
            batchNo: it.batchNo ?? undefined,
            productionDate: it.productionDate ?? undefined,
            expiryDate: it.expiryDate ?? undefined,
            locationId: it.locationId ?? undefined,
          })),
        );
        setEditableKeys([]);
      })
      .catch(() => {
        if (cancelled) return;
        message.error("加载期初单详情失败");
        navigate("/opening");
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewId]);

  // 只读详情:仓库列表加载完成后补驱动 loadWhContext(加载 currentWh/库位/已期初过滤)
  useEffect(() => {
    if (readonly && warehouses.length > 0 && headWhId != null) {
      loadWhContext(headWhId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [warehouses, headWhId]);

  // 切换仓库:加载该仓"已期初物品"(下拉过滤)与库位下拉,后端校验为准
  const loadWhContext = (whId: number | undefined) => {
    if (whId == null) {
      setCurrentWh(null);
      setLocations([]);
      setOpenedItemIds(new Set());
      return;
    }
    const wh = warehouses.find((w) => w.id === whId) ?? null;
    setCurrentWh(wh);
    warehouseApi
      .listLocations({ warehouseId: whId, page: 1, pageSize: 200 })
      .then((r) => setLocations(r.rows))
      .catch(() => setLocations([]));
    // 分页拉取该仓全部期初单,汇总已期初物品 ID
    let page = 1;
    const pageSize = 200;
    const ids = new Set<number>();
    const fetchLoop = (): Promise<void> =>
      openingApi
        .list({ warehouseId: whId, page, pageSize })
        .then((r) => {
          for (const doc of r.rows) {
            for (const it of doc.items ?? []) ids.add(it.itemId);
          }
          if (page * pageSize < r.total && page < 50) {
            page += 1;
            return fetchLoop();
          }
          setOpenedItemIds(ids);
        })
        .catch(() => setOpenedItemIds(new Set()));
    fetchLoop();
  };

  // 物品下拉:过滤该仓已做过期初的物品(前端过滤可选,后端校验为准)
  const itemOptions = useMemo(
    () =>
      items
        .filter((it) => !openedItemIds.has(it.id))
        .map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id })),
    [items, openedItemIds],
  );

  const batchRequired = Boolean(currentWh?.enableBatch || currentWh?.enableExpiry);
  const locationRequired = Boolean(currentWh?.enableLocation);

  // 行值变化:写回数据流(受控 EditableProTable,联动只依赖数据)
  const handleLinesChange = (values: readonly LineRow[]) => {
    setData([...values]);
  };

  const addLine = () => {
    const key = lineSeq++;
    setData((d) => [...d, { key }]);
    setEditableKeys((k) => [...k, key]);
  };

  const removeLine = (key: number) => {
    setData((d) => d.filter((r) => r.key !== key));
    setEditableKeys((k) => k.filter((x) => x !== key));
  };

  const columns: ProColumns<LineRow>[] = useMemo(() => {
    const cols: ProColumns<LineRow>[] = [
      {
        title: "物品",
        dataIndex: "itemId",
        width: 240,
        valueType: "select",
        fieldProps: {
          showSearch: true,
          optionFilterProp: "label",
          placeholder: "选择物品(已期初物品不显示)",
          options: itemOptions,
        },
        formItemProps: { rules: [{ required: true, message: "请选择物品" }] },
        render: (_v, r) =>
          items.find((i) => i.id === r.itemId)?.itemName ?? "-",
      },
      {
        title: "期初数量",
        dataIndex: "quantity",
        width: 120,
        valueType: "digit",
        fieldProps: { min: 0.0001, step: 1 },
        formItemProps: { rules: [{ required: true, message: "请填写数量" }] },
      },
      {
        title: "期初单价(可选)",
        dataIndex: "unitPrice",
        width: 140,
        valueType: "digit",
        fieldProps: { min: 0, step: 0.01 },
        tooltip: "期初成本参考,仅记录快照,不影响库存",
      },
      {
        title: "批次号",
        dataIndex: "batchNo",
        width: 140,
        valueType: "text",
        formItemProps: batchRequired
          ? { rules: [{ required: true, message: "批次仓必填批次号" }] }
          : undefined,
      },
      {
        title: "生产日期",
        dataIndex: "productionDate",
        width: 150,
        valueType: "date",
        fieldProps: { style: { width: "100%" } },
      },
      {
        title: "保质期到期日",
        dataIndex: "expiryDate",
        width: 150,
        valueType: "date",
        fieldProps: { style: { width: "100%" } },
      },
    ];
    if (currentWh?.enableLocation) {
      cols.push({
        title: "库位",
        dataIndex: "locationId",
        width: 160,
        valueType: "select",
        fieldProps: {
          allowClear: true,
          options: locations.map((l) => ({
            label: `${l.locationCode} ${l.locationName ?? ""}`.trim(),
            value: l.id,
          })),
        },
        formItemProps: locationRequired
          ? { rules: [{ required: true, message: "库位仓必填库位" }] }
          : undefined,
      });
    }
    // 显式操作列:EditableProTable 自动 option 列在 scroll 布局下不渲染 td,与入库/销售页同款显式删除;
    // 只读详情不渲染删除列
    if (!readonly) {
      cols.push({
        title: "操作",
        width: 80,
        editable: false,
        render: (_v: unknown, r: LineRow) => (
          <a onClick={() => removeLine(r.key)}>删除</a>
        ),
      });
    }
    return cols;
  }, [items, itemOptions, batchRequired, locationRequired, currentWh, locations, removeLine, readonly]);

  const onSubmit = async () => {
    let values: Record<string, unknown>;
    try {
      values = await headerForm.validateFields();
    } catch {
      message.warning("请填写表头必填项(仓库必填)");
      return;
    }
    try {
      await lineForm.validateFields();
    } catch {
      message.warning("请完善期初明细必填项");
      return;
    }
    const validLines = data.filter((l) => l.itemId != null && l.quantity != null);
    if (validLines.length === 0) {
      message.error("请至少填写一行期初明细");
      return;
    }
    // 与后端同口径的前置校验(后端为准)
    if (batchRequired && validLines.some((l) => !l.batchNo)) {
      message.error("批次/保质期仓期初必须填写批次号");
      return;
    }
    if (locationRequired && validLines.some((l) => l.locationId == null)) {
      message.error("库位仓期初必须选择库位");
      return;
    }
    setSaving(true);
    try {
      await openingApi.create({
        warehouseId: values.warehouseId as number,
        docDate: (values.docDate as Dayjs).format("YYYY-MM-DD"),
        remark: values.remark as string | undefined,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          quantity: l.quantity!,
          unitPrice: l.unitPrice,
          batchNo: l.batchNo || undefined,
          productionDate: toDay(l.productionDate),
          expiryDate: toDay(l.expiryDate),
          locationId: l.locationId,
        })),
      });
      message.success("期初单已提交并过账");
      navigate("/opening?refresh=1");
    } catch {
      // 拦截器已提示(如"该物品在此仓库已有期初",整单回滚)
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      {/* 页面头部:返回 + 标题 */}
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/opening">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">{readonly ? "期初单详情" : "新建期初单"}</h1>
      </div>

      {/* 单卡片布局:表头 + 明细 + 底部固定操作条 */}
      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
          <ProForm
            form={headerForm}
            layout="vertical"
            grid
            submitter={false}
            disabled={readonly}
          >
            <ProFormDatePicker
              name="docDate"
              label="单据日期"
              colProps={{ span: 6 }}
              initialValue={dayjs()}
              rules={[{ required: true, message: "请选择单据日期" }]}
            />
            <ProFormSelect
              name="warehouseId"
              label="仓库"
              colProps={{ span: 6 }}
              showSearch
              options={warehouses.map((w) => ({
                label: `${w.warehouseCode} ${w.warehouseName}`,
                value: w.id,
              }))}
              rules={[{ required: true, message: "请选择仓库" }]}
              fieldProps={{
                optionFilterProp: "label",
                onChange: (v?: number) => loadWhContext(v),
              }}
            />
            <ProFormTextArea
              name="remark"
              label="备注"
              colProps={{ span: 12 }}
              fieldProps={{ rows: 2 }}
            />
          </ProForm>
          <div className="doc-form-section-title">期初明细</div>
          <EditableProTable<LineRow>
            rowKey="key"
            columns={columns}
            value={data}
            onChange={handleLinesChange}
            controlled
            recordCreatorProps={false}
            search={false}
            options={false}
            pagination={false}
            scroll={{ x: 1040, y: "calc(100vh - 520px)" }}
            editable={{
              type: "multiple",
              form: lineForm,
              editableKeys,
              onChange: (keys) => setEditableKeys(keys),
              // 行删除走显式操作列(removeLine),自动 option 列在 scroll 布局下不渲染 body 已弃用
            }}
          />
          {/* 添加行按钮:明细表正下方,左对齐(只读详情不渲染) */}
          {!readonly && (
            <div className="doc-form-line-adder">
              <Button onClick={addLine}>添加行</Button>
            </div>
          )}
        </div>
        {/* 底部固定操作条:左摘要,中间主按钮居中 */}
        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {data.length} 行明细
              {!readonly && (
                <span className="doc-form-footer-muted">
                  提交后即时过账生成期初入库单,失败整单回滚
                </span>
              )}
            </div>
          </div>
          <div className="doc-form-footer-main">
            {readonly ? (
              <Button onClick={() => navigate("/opening")}>返回</Button>
            ) : (
              <Button type="primary" loading={saving} onClick={onSubmit}>
                提交并过账
              </Button>
            )}
          </div>
        </div>
      </ProCard>
    </>
  );
}
