// 新建请购单(V25,无审批)
// 镜像 PurchaseOrderNewPage 但更轻:无金额合计、行无税;申请部门字典下拉;
// 操作条:"保存草稿 / 提交"。
// 详情:id 非空且非 draft → 只读。

import { useEffect, useState } from "react";
import {
  Button,
  DatePicker,
  Input,
  InputNumber,
  message,
  Select,
  Table,
} from "antd";
import {
  ProCard,
  ProForm,
  ProFormDatePicker,
  ProFormSelect,
  ProFormTextArea,
} from "@ant-design/pro-components";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { Link, useNavigate, useParams } from "react-router-dom";
import dayjs, { type Dayjs } from "dayjs";
import { requisitionApi, itemApi, userApi, warehouseApi, dictApi } from "../../api";
import type { Warehouse } from "../../types";
import type { Item, UserInfo } from "../../types";

interface LineRow {
  key: number;
  itemId?: number;
  quantity?: number;
  expectedDate?: Dayjs;
  unitPrice?: number;
  remark?: string;
}

let lineSeq = 1;

function isEditable(status: string | undefined): boolean {
  return status === "draft" || status === undefined;
}

export function PurchaseRequisitionNewPage() {
  const navigate = useNavigate();
  const { id: editIdParam } = useParams<{ id?: string }>();
  const editId = editIdParam ? Number(editIdParam) : null;
  const [docNo, setDocNo] = useState("");
  const [items, setItems] = useState<Item[]>([]);
  const [users, setUsers] = useState<UserInfo[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [deptOptions, setDeptOptions] = useState<Array<{ label: string; value: string }>>([]);
  const [lines, setLines] = useState<LineRow[]>([{ key: lineSeq++ }]);
  const [saving, setSaving] = useState(false);
  const [docStatus, setDocStatus] = useState<string | undefined>();
  const readonly = editId != null && docStatus != null && !isEditable(docStatus);
  const [form] = ProForm.useForm();

  useEffect(() => {
    itemApi.list({ page: 1, pageSize: 200 }).then((r) => setItems(r.rows)).catch(() => undefined);
    userApi.list({ page: 1, pageSize: 200 }).then((r) => setUsers(r.rows)).catch(() => undefined);
    warehouseApi.list({ page: 1, pageSize: 200 }).then((r) => setWarehouses(r.rows)).catch(() => undefined);
    dictApi.getType("dept").then((arr) => setDeptOptions(arr.map((d) => ({ label: d.label, value: d.code })))).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (editId == null) return;
    requisitionApi.get(editId).then((doc) => {
      setDocNo(doc.docNo);
      setDocStatus(doc.status);
      form.setFieldsValue({
        docDate: dayjs(doc.docDate),
        warehouseId: doc.warehouseId,
        applicantId: doc.applicantId,
        department: doc.department ?? undefined,
        remark: doc.remark ?? undefined,
      });
      const rows: LineRow[] = (doc.items ?? []).map((l, i) => ({
        key: i + 1,
        itemId: l.itemId,
        quantity: Number(l.quantity),
        expectedDate: l.expectedDate ? dayjs(l.expectedDate) : undefined,
        unitPrice: l.unitPrice != null ? Number(l.unitPrice) : undefined,
        remark: l.remark ?? undefined,
      }));
      setLines(rows);
      lineSeq = Math.max(lineSeq, rows.length + 1);
    }).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId]);

  const itemOptions = items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }));

  const doSave = async (submitAfter: boolean) => {
    const v = await form.validateFields();
    const validLines = lines.filter((l) => l.itemId && l.quantity);
    if (validLines.length === 0) {
      message.error("请至少填写一行请购明细");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        docDate: v.docDate.format("YYYY-MM-DD"),
        warehouseId: v.warehouseId,
        applicantId: v.applicantId,
        department: v.department,
        remark: v.remark,
        items: validLines.map((l) => ({
          itemId: l.itemId!,
          quantity: l.quantity!,
          expectedDate: l.expectedDate?.format("YYYY-MM-DD"),
          unitPrice: l.unitPrice,
          remark: l.remark,
        })),
      };
      let id = editId;
      if (editId != null) {
        await requisitionApi.update(editId, payload);
        message.success("请购单已保存");
      } else {
        const created = await requisitionApi.create(payload);
        id = created.id;
        message.success("请购单已创建(草稿)");
      }
      if (submitAfter && id != null) {
        await requisitionApi.submit(id);
        message.success("已提交");
      }
      navigate("/purchase-requisitions");
    } catch {
      // ignore
    } finally {
      setSaving(false);
    }
  };

  const lineColumns = [
    {
      title: "物品",
      width: 250,
      render: (_v: unknown, l: LineRow) => (
        <Select
          showSearch
          placeholder="选择物品"
          optionFilterProp="label"
          style={{ width: "100%" }}
          options={itemOptions}
          value={l.itemId}
          disabled={readonly}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, itemId: v } : x)))
          }
        />
      ),
    },
    {
      title: "数量",
      width: 100,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0.0001}
          step={1}
          style={{ width: "100%" }}
          value={l.quantity}
          disabled={readonly}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, quantity: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "期望到货日",
      width: 140,
      render: (_v: unknown, l: LineRow) => (
        <DatePicker
          style={{ width: "100%" }}
          value={l.expectedDate}
          disabled={readonly}
          onChange={(d) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, expectedDate: d ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "参考单价",
      width: 110,
      render: (_v: unknown, l: LineRow) => (
        <InputNumber
          min={0}
          step={0.01}
          style={{ width: "100%" }}
          value={l.unitPrice}
          disabled={readonly}
          onChange={(v) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, unitPrice: v ?? undefined } : x)))
          }
        />
      ),
    },
    {
      title: "行备注",
      width: 160,
      render: (_v: unknown, l: LineRow) => (
        <Input
          value={l.remark}
          disabled={readonly}
          onChange={(e) =>
            setLines((ls) => ls.map((x) => (x.key === l.key ? { ...x, remark: e.target.value } : x)))
          }
        />
      ),
    },
    ...(readonly
      ? []
      : [
          {
            title: "",
            width: 50,
            render: (_v: unknown, l: LineRow) => (
              <a onClick={() => setLines((ls) => (ls.length > 1 ? ls.filter((x) => x.key !== l.key) : ls))}>
                删除
              </a>
            ),
          },
        ]),
  ];

  return (
    <>
      <div className="doc-page-head">
        <Link className="doc-page-back" to="/purchase-requisitions">
          <ArrowLeftOutlined /> 返回列表
        </Link>
        <h1 className="doc-page-title">
          {editId != null ? "编辑请购单" : "新建请购单"}
        </h1>
        {editId != null && <span className="doc-page-meta">单号 {docNo || "..."}</span>}
      </div>

      <ProCard bodyStyle={{ padding: 0 }}>
        <div className="doc-form-body">
          <ProForm form={form} layout="vertical" grid submitter={false} disabled={readonly}>
            <ProFormDatePicker
              name="docDate"
              label="开单日期"
              colProps={{ span: 6 }}
              initialValue={dayjs()}
              rules={[{ required: true, message: "请选择开单日期" }]}
            />
            <ProFormSelect
              name="warehouseId"
              label="收货仓库"
              colProps={{ span: 6 }}
              placeholder="选择收货仓库"
              options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
              rules={[{ required: true, message: "请选择收货仓库" }]}
            />
            <ProFormSelect
              name="applicantId"
              label="申请人"
              colProps={{ span: 6 }}
              showSearch
              placeholder="选择申请人"
              options={users.map((u) => ({ label: u.name, value: u.id }))}
              fieldProps={{ optionFilterProp: "label" }}
              rules={[{ required: true, message: "请选择申请人" }]}
            />
            <ProFormSelect
              name="department"
              label="申请部门"
              colProps={{ span: 6 }}
              placeholder="选择申请部门(可空)"
              options={deptOptions}
              allowClear
            />
            <ProFormTextArea
              name="remark"
              label="备注"
              colProps={{ span: 24 }}
              fieldProps={{ rows: 2 }}
            />
          </ProForm>

          <div className="doc-form-section-title">请购明细(无金额合计,行价可空)</div>
          <Table
            rowKey="key"
            size="small"
            dataSource={lines}
            pagination={false}
            columns={lineColumns}
            scroll={{ x: 800, y: "calc(100vh - 520px)" }}
          />
          {!readonly && (
            <div className="doc-form-line-adder">
              <Button onClick={() => setLines((ls) => [...ls, { key: lineSeq++ }])}>添加行</Button>
            </div>
          )}
        </div>
        <div className="doc-form-footer">
          <div className="doc-form-footer-left">
            <div className="doc-form-footer-summary">
              共 {lines.length} 行明细
            </div>
          </div>
          <div className="doc-form-footer-main">
            {readonly ? (
              <Button onClick={() => navigate("/purchase-requisitions")}>返回</Button>
            ) : (
              <div style={{ display: "flex", gap: 12 }}>
                <Button loading={saving} onClick={() => doSave(false)}>
                  保存草稿
                </Button>
                <Button type="primary" loading={saving} onClick={() => doSave(true)}>
                  提交
                </Button>
              </div>
            )}
          </div>
        </div>
      </ProCard>
    </>
  );
}