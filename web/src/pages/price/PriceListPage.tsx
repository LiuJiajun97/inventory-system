// 价目表(V26:供应商/客户维度带价,新建单据选物品自动带价)
// ProTable + Drawer(表头 Form + 明细 EditableProTable),镜像 dict/仓库列表页风格
// 顶部 Segmented「供应商价目/客户价目」;无标题横幅;新建按钮经 optionRender 放筛选行右侧

import { useEffect, useRef, useState } from "react";
import {
  App,
  Button,
  Drawer,
  Form,
  Segmented,
  Space,
  Tag,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import {
  EditableProTable,
  ProForm,
  ProFormDateRangePicker,
  ProFormSelect,
  ProFormText,
  ProTable,
} from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import dayjs, { type Dayjs } from "dayjs";
import { customerApi, itemApi, priceApi, supplierApi } from "../../api";
import type { Customer, Supplier } from "../../types/phase1";
import type { Item, PriceList, PriceListDetail } from "../../types";
import { usePermission } from "../../auth/usePermission";
import { fmtDate } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { useResizableColumns } from "../../utils/tablePrefs";
import { proTableRequest } from "../../utils/proTable";

interface LineRow {
  key: number;
  itemId?: number;
  unitPrice?: number;
  taxRate?: number;
}

let lineSeq = 2;

export function PriceListPage() {
  const { hasPerm } = usePermission();
  const { message, modal } = App.useApp();
  const canEdit = hasPerm("price:edit");
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [lineForm] = ProForm.useForm();

  // 顶部维度切换(供应商价目/客户价目)
  const [ownerType, setOwnerType] = useState<"supplier" | "customer">("supplier");
  // 下拉数据源:供应商/客户/物品
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [items, setItems] = useState<Item[]>([]);

  // 抽屉:新建/编辑
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<PriceList | null>(null);
  const [lineData, setLineData] = useState<LineRow[]>([{ key: 1 }]);
  const [editableKeys, setEditableKeys] = useState<React.Key[]>([1]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    supplierApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setSuppliers(r.rows))
      .catch(() => undefined);
    customerApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setCustomers(r.rows))
      .catch(() => undefined);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setItems(r.rows))
      .catch(() => undefined);
  }, []);

  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { ownerKeyword?: string }) => ({
      ownerType,
      ownerKeyword: p.ownerKeyword || undefined,
    }),
    priceApi.list,
  );

  // 维度切换:重置筛选并刷新
  const onSwitchOwnerType = (v: "supplier" | "customer") => {
    setOwnerType(v);
    actionRef.current?.reload();
  };

  const columns: ProColumns<PriceList>[] = [
    {
      title: "对方单位",
      dataIndex: "ownerKeyword",
      hideInTable: true,
      fieldProps: { placeholder: "编码或名称", allowClear: true },
    },
    {
      title: "名称",
      dataIndex: "name",
      width: 180,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.name || "-",
    },
    {
      title: "对方单位",
      width: 220,
      ellipsis: true,
      search: false,
      render: (_v, r) =>
        r.ownerName ? `${r.ownerName} ${r.ownerCode ?? ""}` : r.ownerCode ?? "-",
    },
    {
      title: "生效起",
      dataIndex: "validFrom",
      width: 110,
      search: false,
      render: (_v, r) => fmtDate(r.validFrom ?? undefined),
    },
    {
      title: "生效止",
      dataIndex: "validUntil",
      width: 110,
      search: false,
      render: (_v, r) => fmtDate(r.validUntil ?? undefined),
    },
    {
      title: "行数",
      dataIndex: "lineCount",
      width: 70,
      align: "right",
      search: false,
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 70,
      search: false,
      render: (_v, r) =>
        r.status === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: "操作",
      width: 100,
      search: false,
      render: (_v, r) =>
        canEdit ? (
          <Space size="small">
            <a onClick={() => openEdit(r)}>编辑</a>
            <a onClick={() => onDelete(r)}>删除</a>
          </Space>
        ) : null,
    },
  ];

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(columns, "price-list");

  // 明细列:物品(Select 搜索)+ 不含税单价 + 税率(可空)
  const lineColumns: ProColumns<LineRow>[] = [
    {
      title: "物品",
      dataIndex: "itemId",
      width: 250,
      valueType: "select",
      fieldProps: {
        showSearch: true,
        optionFilterProp: "label",
        placeholder: "选择物品",
        options: items.map((it) => ({
          label: `${it.itemCode} ${it.itemName}`,
          value: it.id,
        })),
      },
      formItemProps: { rules: [{ required: true, message: "请选择物品" }] },
      render: (_v, r) =>
        items.find((i) => i.id === r.itemId)?.itemName ?? "-",
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
      title: "税率(%)(空=物品默认)",
      dataIndex: "taxRate",
      width: 140,
      valueType: "digit",
      fieldProps: { min: 0, max: 100, step: 0.01 },
    },
    {
      title: "操作",
      width: 60,
      render: (_v, r) => (
        <a onClick={() => removeLine(r.key)}>删除</a>
      ),
    },
  ];

  const ownerOptions = ownerType === "supplier"
    ? suppliers.map((s) => ({ label: `${s.supplierCode} ${s.supplierName}`, value: s.id }))
    : customers.map((c) => ({ label: `${c.customerCode} ${c.customerName}`, value: c.id }));

  // 新建
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setLineData([{ key: 1 }]);
    setEditableKeys([1]);
    lineSeq = 2;
    setDrawerOpen(true);
  };

  // 手动添加一行并进入编辑态(不用 recordCreatorProps:其 record() 回调在受控 dataSource 模式下于渲染期执行,
  // 内部 setEditableKeys 会触发渲染期 setState 无限循环卡死页面——订单新页同款模式)
  const addLine = () => {
    const key = lineSeq++;
    setLineData((d) => [...d, { key }]);
    setEditableKeys((k) => [...k, key]);
  };

  // 删除一行(受控:同步行数据与可编辑键)
  const removeLine = (key: number) => {
    setLineData((d) => d.filter((r) => r.key !== key));
    setEditableKeys((k) => k.filter((x) => x !== key));
  };

  // 编辑:GET 详情回填表头 + 行
  const openEdit = async (row: PriceList) => {
    try {
      const detail: PriceListDetail = await priceApi.get(row.id);
      setEditing(row);
      form.setFieldsValue({
        name: detail.name ?? undefined,
        ownerId: detail.ownerId,
        validRange:
          detail.validFrom != null || detail.validUntil != null
            ? [
                detail.validFrom ? dayjs(detail.validFrom) : undefined,
                detail.validUntil ? dayjs(detail.validUntil) : undefined,
              ]
            : undefined,
      });
      const rows: LineRow[] = (detail.lines ?? []).map((l, i) => ({
        key: i + 1,
        itemId: l.itemId,
        unitPrice: Number(l.unitPrice),
        taxRate: l.taxRate != null ? Number(l.taxRate) : undefined,
      }));
      setLineData(rows);
      setEditableKeys(rows.map((r) => r.key));
      lineSeq = Math.max(lineSeq, rows.length + 1);
      setDrawerOpen(true);
    } catch {
      // 拦截器已提示
    }
  };

  // 保存(新建/编辑)
  const onSave = async () => {
    let values: { name?: string; ownerId?: number; validRange?: (Dayjs | null)[] };
    try {
      values = await form.validateFields();
    } catch {
      message.warning("请填写表头必填项");
      return;
    }
    try {
      await lineForm.validateFields();
    } catch {
      message.warning("请完善价目明细必填项");
      return;
    }
    const lines = lineData.filter((l) => l.itemId != null && l.unitPrice != null);
    if (lines.length === 0) {
      message.warning("请至少填写一行价目明细");
      return;
    }
    const payload = {
      ownerType,
      ownerId: values.ownerId!,
      name: values.name || undefined,
      validFrom: values.validRange?.[0]?.format("YYYY-MM-DD") ?? null,
      validUntil: values.validRange?.[1]?.format("YYYY-MM-DD") ?? null,
      lines: lines.map((l) => ({
        itemId: l.itemId!,
        unitPrice: l.unitPrice!,
        taxRate: l.taxRate ?? null,
      })),
    };
    setSaving(true);
    try {
      if (editing) {
        await priceApi.update(editing.id, payload);
        message.success("价目表已保存");
      } else {
        await priceApi.create(payload);
        message.success("价目表已创建");
      }
      setDrawerOpen(false);
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  // 删除(级联删行,不可逆操作保留确认)
  const onDelete = (row: PriceList) => {
    modal.confirm({
      title: "确认删除价目表?",
      content: `将删除「${row.name || row.ownerName || row.id}」及其全部价目行,不可恢复。`,
      okText: "删除",
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await priceApi.delete(row.id);
          message.success("价目表已删除");
          actionRef.current?.reload();
        } catch {
          // 拦截器已提示
        }
      },
    });
  };

  return (
    <>
      <ProTable<PriceList>
        actionRef={actionRef}
        rowKey="id"
        locale={{ emptyText: <EmptyHint text="当前筛选条件下暂无价目表" /> }}
        columns={rcColumns}
        request={request}
        params={{ ownerType }}
        headerTitle={
          <Segmented
            options={[
              { label: "供应商价目", value: "supplier" },
              { label: "客户价目", value: "customer" },
            ]}
            value={ownerType}
            onChange={(v) => onSwitchOwnerType(v as "supplier" | "customer")}
          />
        }
        options={{
          density: false,
          reload: false,
          fullScreen: false,
          setting: rcOptionSetting,
        }}
        columnsState={columnsState}
        onColumnsStateChange={onColumnsChange}
        components={rcComponents}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            canEdit && (
              <Button key="create" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
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
        scroll={rcScroll}
      />

      {/* 新建/编辑抽屉:表头 + 明细 */}
      <Drawer
        title={editing ? "编辑价目表" : "新建价目表"}
        width={960}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
        footer={
          <div style={{ textAlign: "center" }}>
            <Button type="primary" loading={saving} onClick={onSave}>
              保存
            </Button>
          </div>
        }
      >
        <ProForm form={form} layout="vertical" grid submitter={false}>
          <ProFormText
            name="name"
            label="价目名称"
            colProps={{ span: 8 }}
            placeholder="可空,默认对方单位简称"
          />
          <ProFormSelect
            name="ownerId"
            label="对方单位"
            colProps={{ span: 8 }}
            showSearch
            options={ownerOptions}
            fieldProps={{ optionFilterProp: "label" }}
            rules={[{ required: true, message: "请选择对方单位" }]}
          />
          <ProFormDateRangePicker
            name="validRange"
            label="生效区间"
            colProps={{ span: 8 }}
            fieldProps={{ style: { width: "100%" } }}
            placeholder={["生效起(可空)", "生效止(可空)"]}
          />
        </ProForm>
        <div className="doc-form-section-title">价目明细</div>
        <EditableProTable<LineRow>
          rowKey="key"
          columns={lineColumns}
          value={lineData}
          onChange={(v) => setLineData([...(v as readonly LineRow[])])}
          controlled
          recordCreatorProps={false}
          search={false}
          options={false}
          pagination={false}
          editable={{
            type: "multiple",
            form: lineForm,
            editableKeys,
            onChange: (keys) => setEditableKeys(keys),
          }}
        />
        {/* 添加行按钮:明细表正下方,左对齐(与订单新页同款) */}
        <div className="doc-form-line-adder">
          <Button onClick={addLine}>添加行</Button>
        </div>
      </Drawer>
    </>
  );
}
