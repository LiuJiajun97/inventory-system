// 盘点单(一期新增)
// ProTable 版:筛选字段由 columns 配置驱动,新建按钮经 search.optionRender 放筛选行右侧
// 新建(整仓/指定物品,保存即按当前余额生成 bookQty 快照)+ 录入实盘 + 刷新快照
// + 差异生成调整单(盘盈/盘亏各一张)+ 状态机操作

import { useEffect, useRef, useState } from "react";
import { Button, Col, DatePicker, Descriptions, Drawer, Form, Input, Modal, Popconfirm, Row, Select, Space, Table, theme } from "antd";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import dayjs, { type Dayjs } from "dayjs";
import { fmtDate } from "../../utils/format";
import { itemApi, stocktakeApi, warehouseApi } from "../../api";
import type { Item, Warehouse } from "../../types";
import type { StocktakeDoc, StocktakeLine } from "../../types/phase1";
import { usePermission } from "../../auth/usePermission";
import { DocStatusTag } from "../../components/DocStatusTag";

// 状态机枚举:筛选下拉用 valueEnum,表格单元格仍用 DocStatusTag 自定义渲染(样式不变)
const STATUS_ENUM = {
  draft: { text: "草稿" },
  pending: { text: "待审批" },
  approved: { text: "已审批" },
  rejected: { text: "已驳回" },
  voided: { text: "已作废" },
};

export function StocktakePage() {
  const { hasPerm } = usePermission();
  // 按钮级权限码(前端仅控制显隐,403 兜底由后端 @RequirePermission 拦截)
  // 录入实盘/刷新快照属编辑动作 → :edit;生成调整单为审批后动作 → :approve
  const canEdit = hasPerm("stocktake:edit");
  const canSubmit = hasPerm("stocktake:submit");
  const canApprove = hasPerm("stocktake:approve");
  const canReject = hasPerm("stocktake:reject");
  const canVoid = hasPerm("stocktake:void");
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [detail, setDetail] = useState<StocktakeDoc | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [actualDoc, setActualDoc] = useState<StocktakeDoc | null>(null);
  const [actuals, setActuals] = useState<Record<number, number | null>>({});
  const [rejectTarget, setRejectTarget] = useState<StocktakeDoc | null>(null);
  const [saving, setSaving] = useState(false);
  const [createForm] = Form.useForm();
  const actionRef = useRef<ActionType>();
  // antd 主题 token:抽屉 footer 上边线颜色(不硬编码色值)
  const { token } = theme.useToken();

  const whName = (id: number) => warehouses.find((w) => w.id === id)?.warehouseName ?? `#${id}`;

  // 仓库/物品下拉数据源(异步加载,仅用于筛选项与名称展示)
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

  // 参数适配:ProTable current/pageSize -> 后端 page/pageSize
  const request = async (params: {
    current?: number;
    pageSize?: number;
    docNo?: string;
    warehouseId?: number;
    status?: string;
  }) => {
    const res = await stocktakeApi.list({
      docNo: params.docNo,
      warehouseId: params.warehouseId,
      status: params.status,
      page: params.current ?? 1,
      pageSize: params.pageSize ?? 20,
    });
    return { data: res.rows, success: true, total: res.total };
  };

  const doAction = async (fn: () => Promise<unknown>, msg: string) => {
    try {
      await fn();
      Modal.success({ content: msg });
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    }
  };

  const onCreate = async () => {
    const v = await createForm.validateFields();
    if (v.scopeType === "item" && (!v.itemIds || v.itemIds.length === 0)) {
      Modal.error({ content: "指定物品盘点需至少选择一个物品" });
      return;
    }
    setSaving(true);
    try {
      const doc = await stocktakeApi.create({
        warehouseId: v.warehouseId,
        docDate: (v.docDate as Dayjs).format("YYYY-MM-DD"),
        scopeType: v.scopeType,
        itemIds: v.scopeType === "item" ? v.itemIds : undefined,
        remark: v.remark,
      });
      Modal.success({ content: `盘点单已创建,已按当前余额生成快照(${doc.items?.length ?? 0} 行)` });
      setCreateOpen(false);
      createForm.resetFields();
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const openActual = async (row: StocktakeDoc) => {
    const full = await stocktakeApi.get(row.id);
    setActualDoc(full);
    setActuals(Object.fromEntries((full.items ?? []).map((l) => [l.id, l.actualQty ? Number(l.actualQty) : null])));
  };

  const saveActual = async () => {
    if (!actualDoc) return;
    setSaving(true);
    try {
      const lines = (actualDoc.items ?? [])
        .filter((l) => actuals[l.id] !== undefined)
        .map((l) => ({ lineId: l.id, actualQty: actuals[l.id] }));
      const full = await stocktakeApi.enterActual(actualDoc.id, lines);
      setActualDoc(full);
      setActuals(Object.fromEntries((full.items ?? []).map((l) => [l.id, l.actualQty ? Number(l.actualQty) : null])));
      Modal.success({ content: "实盘数量已保存,差异已重算" });
    } catch {
      // 拦截器已提示
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumns<StocktakeDoc>[] = [
    {
      title: "单号",
      dataIndex: "docNo",
      width: 160,
      fieldProps: { placeholder: "单号", allowClear: true },
      render: (_v, row) => (
        <a style={{ fontFamily: "monospace", fontSize: 13 }} onClick={() => setDetail(row)}>
          {row.docNo}
        </a>
      ),
    },
    {
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      hideInTable: true,
      fieldProps: {
        allowClear: true,
        placeholder: "全部",
        options: warehouses.map((w) => ({ label: w.warehouseName, value: w.id })),
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 90,
      valueEnum: STATUS_ENUM,
      render: (_v, r) => <DocStatusTag status={r.status} />,
    },
    { title: "盘点日期", dataIndex: "docDate", width: 110, search: false, render: (_v, r) => fmtDate(r.docDate) },
    { title: "仓库", width: 140, ellipsis: true, search: false, render: (_v, r) => whName(r.warehouseId) },
    {
      title: "范围",
      dataIndex: "scopeType",
      width: 100,
      search: false,
      render: (_v, r) => (r.scopeType === "all" ? "整仓" : "指定物品"),
    },
    { title: "行数", width: 70, search: false, render: (_v, r) => r.items?.length ?? "-" },
    { title: "创建人", dataIndex: "creator", width: 90, ellipsis: true, search: false },
    {
      title: "操作",
      width: 300,
      fixed: "right" as const,
      search: false,
      render: (_v, row) => {
        const s = row.status;
        const btns: React.ReactNode[] = [];
        if (canEdit && (s === "draft" || s === "pending")) {
          btns.push(<a key="actual" className="action-submit" onClick={() => openActual(row)}>录入实盘</a>);
          btns.push(
            <a key="refresh" className="action-submit" onClick={() => doAction(() => stocktakeApi.refreshBook(row.id), "快照已刷新")}>
              刷新快照
            </a>,
          );
        }
        if (canApprove && s === "approved") {
          btns.push(
            <Popconfirm
              key="adjust"
              title="将按差异生成盘盈/盘亏调整单(草稿),确认?"
              onConfirm={() => doAction(() => stocktakeApi.generateAdjust(row.id), "调整单已生成,请到库存调整页审批执行")}
            >
              <a className="action-approve">生成调整单</a>
            </Popconfirm>,
          );
        }
        if (s === "draft" || s === "rejected") {
          if (canSubmit) {
            btns.push(
              <a key="submit" className="action-submit" onClick={() => doAction(() => stocktakeApi.submit(row.id), "已提交审批")}>
                提交
              </a>,
            );
          }
          if (canVoid) {
            btns.push(
              <Popconfirm key="void" title="确认作废该盘点单?" onConfirm={() => doAction(() => stocktakeApi.voidDoc(row.id), "已作废")}>
                <a className="action-void">作废</a>
              </Popconfirm>,
            );
          }
        }
        if (s === "pending") {
          if (canApprove) {
            btns.push(
              <a key="approve" className="action-approve" onClick={() => doAction(() => stocktakeApi.approve(row.id), "已审批通过")}>
                审批
              </a>,
            );
          }
          if (canReject) {
            btns.push(
              <a key="reject" className="action-reject" onClick={() => setRejectTarget(row)}>
                驳回
              </a>,
            );
          }
        }
        return <Space size={10}>{btns.length ? btns : <span style={{ color: "#999" }}>-</span>}</Space>;
      },
    },
  ];

  const lineTable = (lines: StocktakeLine[], editable: boolean) => (
    <Table
      size="small"
      rowKey="id"
      dataSource={lines}
      pagination={false}
      scroll={{ x: 800 }}
      columns={[
        { title: "行号", dataIndex: "lineNo", width: 50 },
        { title: "物品", dataIndex: "itemName", render: (v: string, r) => `${r.itemCode} ${v}` },
        { title: "账面量", dataIndex: "bookQty", width: 100, align: "right", className: "num-cell" },
        {
          title: "实盘量",
          dataIndex: "actualQty",
          width: 130,
          render: (v: string | null, r) =>
            editable ? (
              <Input
                key={`${r.id}-${v ?? ""}`}
                defaultValue={v ?? ""}
                placeholder="未盘"
                onBlur={(e) => {
                  const raw = e.target.value.trim();
                  setActuals((m) => ({ ...m, [r.id]: raw === "" ? null : Number(raw) }));
                }}
              />
            ) : (
              v ?? "-"
            ),
        },
        {
          title: "差异(实-账)",
          dataIndex: "diffQty",
          width: 110,
          align: "right",
          className: "num-cell",
          render: (v: string | null) => {
            if (v == null) return "-";
            const n = Number(v);
            return (
              <span style={{ color: n > 0 ? "#389e0d" : n < 0 ? "#cf1322" : undefined }}>
                {n > 0 ? `+${n}` : n}
              </span>
            );
          },
        },
      ]}
    />
  );

  return (
    <>
      <ProTable<StocktakeDoc>
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        request={request}
        headerTitle={false}
        options={false}
        scroll={{ x: 1100 }}
        search={{
          labelWidth: "auto",
          defaultCollapsed: false,
          span: 6,
          // 新建按钮放筛选行右侧(替代默认工具栏行)
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            // 盘点无独立 :create 码,按约定复用 :edit(能编辑即可新建)
            canEdit && (
              <Button key="new" type="primary" onClick={() => setCreateOpen(true)}>
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
      />

      <Drawer
        title="新建盘点单(保存即生成账面快照)"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        width={480}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮"创建"(文案保持现状,loading 态保留)
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setCreateOpen(false)}>取消</Button>
              <Button type="primary" loading={saving} onClick={onCreate}>
                创建
              </Button>
            </Space>
          </div>
        }
      >
        <Form form={createForm} layout="vertical" requiredMark={false} initialValues={{ docDate: dayjs(), scopeType: "all" }}>
          {/* 表头字段两列对齐(统一规格:两列上限) */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="盘点日期" name="docDate" rules={[{ required: true }]}>
                <DatePicker style={{ width: "100%" }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="仓库" name="warehouseId" rules={[{ required: true, message: "请选择仓库" }]}>
                <Select
                  placeholder="选择仓库"
                  options={warehouses.map((w) => ({ label: w.warehouseName, value: w.id }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="盘点范围" name="scopeType">
                <Select
                  options={[
                    { label: "整仓盘点", value: "all" },
                    { label: "指定物品", value: "item" },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          {/* 条件字段(指定物品时出现,多选)独占一行 */}
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item noStyle shouldUpdate={(a, b) => a.scopeType !== b.scopeType}>
                {({ getFieldValue }) =>
                  getFieldValue("scopeType") === "item" ? (
                    <Form.Item label="物品" name="itemIds" rules={[{ required: true, message: "请选择物品" }]}>
                      <Select
                        mode="multiple"
                        showSearch
                        optionFilterProp="label"
                        placeholder="选择参与盘点的物品"
                        options={items.map((it) => ({ label: `${it.itemCode} ${it.itemName}`, value: it.id }))}
                      />
                    </Form.Item>
                  ) : null
                }
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item label="备注" name="remark">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>

      <Drawer
        title={`录入实盘 - ${actualDoc?.docNo ?? ""}(差异 = 实盘 - 账面)`}
        open={!!actualDoc}
        onClose={() => setActualDoc(null)}
        width={860}
        // 去掉 Drawer footer 默认内边距/边框,由 .drawer-footer 统一控制
        styles={{ footer: { padding: "0 16px", borderTop: "none" } }}
        // 统一底部操作条:次按钮"取消" + 主按钮"保存实盘"(文案保持现状,loading 态保留)
        footer={
          <div
            className="drawer-footer"
            style={{ borderTop: `1px solid ${token.colorBorderSecondary}` }}
          >
            <Space>
              <Button onClick={() => setActualDoc(null)}>取消</Button>
              <Button type="primary" loading={saving} onClick={saveActual}>
                保存实盘
              </Button>
            </Space>
          </div>
        }
      >
        <div style={{ color: "#999", marginBottom: 8 }}>
          留空表示该行为"未盘"(不计差异);保存后差异自动重算。
        </div>
        {actualDoc && lineTable(actualDoc.items ?? [], true)}
      </Drawer>

      <Modal
        title={`盘点单详情 - ${detail?.docNo ?? ""}`}
        open={!!detail}
        onCancel={() => setDetail(null)}
        footer={null}
        width={960}
        className="doc-detail-modal"
      >
        {detail && (
          <>
            <Descriptions column={3} bordered size="small">
              <Descriptions.Item label="盘点日期">{fmtDate(detail.docDate)}</Descriptions.Item>
              <Descriptions.Item label="仓库">{whName(detail.warehouseId)}</Descriptions.Item>
              <Descriptions.Item label="范围">{detail.scopeType === "all" ? "整仓" : "指定物品"}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <DocStatusTag status={detail.status} />
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detail.creator ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="审批人">{detail.approver ?? "-"}</Descriptions.Item>
              <Descriptions.Item label="驳回原因" span={3}>{detail.rejectReason ?? "-"}</Descriptions.Item>
            </Descriptions>
            {lineTable(detail.items ?? [], false)}
          </>
        )}
      </Modal>

      <RejectModal
        target={rejectTarget}
        onClose={() => setRejectTarget(null)}
        onConfirm={(reason) => {
          if (rejectTarget) doAction(() => stocktakeApi.reject(rejectTarget.id, reason), "已驳回");
          setRejectTarget(null);
        }}
      />
    </>
  );
}

// 驳回弹窗(独立组件:表单实例与列表筛选解耦,避免原"共用 form"的坑)
function RejectModal({
  target,
  onClose,
  onConfirm,
}: {
  target: StocktakeDoc | null;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");
  return (
    <Modal
      title={`驳回盘点单 - ${target?.docNo ?? ""}`}
      open={!!target}
      onCancel={onClose}
      onOk={() => {
        if (!reason.trim()) return;
        onConfirm(reason.trim());
        setReason("");
      }}
      okButtonProps={{ disabled: !reason.trim() }}
    >
      <Input.TextArea
        rows={3}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="驳回原因(必填)"
      />
    </Modal>
  );
}
