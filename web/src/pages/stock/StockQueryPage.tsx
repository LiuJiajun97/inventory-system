// 库存查询(SPEC-WEB V2 2.7)
// 基于 ProTable:筛选字段由 columns 配置驱动(仓库/物品关键字/批次号)
// 数量 0 行灰色弱化(row-zero);可用量为负红色
// V26:Segmented「库存/冻结记录」+ 冻结红色 Tag + 行内冻结/解冻(权限码 stock:freeze)

import { useEffect, useMemo, useRef, useState } from "react";
import { ProTable, type ActionType } from "@ant-design/pro-components";
import { useResizableColumns } from "../../utils/tablePrefs";
import type { ProColumns } from "@ant-design/pro-components";
import { App, Modal, Segmented, Space, Tag } from "antd";
import { stockApi, warehouseApi } from "../../api";
import { ExportButton } from "../../components/ExportButton";
import { usePermission } from "../../auth/usePermission";
import { useScopeWarehouseId } from "../../auth/WarehouseScopeContext";
import type { FreezeLogRow, StockRow, Warehouse } from "../../types";
import { fmtDate, fmtDateTime, fmtQty } from "../../utils/format";
import { EmptyHint } from "../../components/EmptyHint";
import { proTableRequest } from "../../utils/proTable";

export function StockQueryPage() {
  const { hasPerm } = usePermission();
  const { message } = App.useApp();
  const canExport = hasPerm("stock:export");
  const canFreeze = hasPerm("stock:freeze");
  // C3 顶栏仓库快捷切换:表单未选仓库时并入请求参数(表单值优先)
  const scopeWarehouseId = useScopeWarehouseId();
  const actionRef = useRef<ActionType>();
  // 顶栏仓库范围切换后自动刷新表格(scope 并入请求参数;首次挂载由 ProTable 自触发,不重复)
  const scopeMounted = useRef(false);
  useEffect(() => {
    if (scopeMounted.current) actionRef.current?.reload();
    else scopeMounted.current = true;
  }, [scopeWarehouseId]);

  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);

  // V26:库存/冻结记录 视图切换(与 V17 主表/明细同款)
  const [viewMode, setViewMode] = useState<"stock" | "freeze">("stock");

  // V26:冻结弹层(原因必填;解冻走 confirm 不用弹层)
  const [freezeTarget, setFreezeTarget] = useState<StockRow | null>(null);
  const [freezeReason, setFreezeReason] = useState("");
  const [freezeSubmitting, setFreezeSubmitting] = useState(false);

  // 仓库下拉数据源(异步加载,仅用于筛选项)
  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  // 参数适配:ProTable 发 current/pageSize,后端吃 page/pageSize
  // 当前筛选条件(导出 URL 用,随筛选变化重建)
  const [filterParams, setFilterParams] = useState<Record<string, string | number | undefined>>({});

  // 分页适配走公共封装:current/pageSize -> page/pageSize、{rows,total} -> {data,success,total}
  const request = proTableRequest(
    (p: { warehouseId?: number; itemKeyword?: string; batchNo?: string }) => {
      setFilterParams({
        warehouseId: p.warehouseId,
        itemKeyword: p.itemKeyword,
        batchNo: p.batchNo,
      });
      return {
        warehouseId: p.warehouseId ?? scopeWarehouseId ?? undefined,
        itemKeyword: p.itemKeyword,
        batchNo: p.batchNo,
      };
    },
    stockApi.query,
  );
  // V26:冻结记录分页请求(仅支持仓库/批次号筛选)
  const freezeRequest = proTableRequest(
    (p: { warehouseId?: number; itemKeyword?: string; batchNo?: string }) => ({
      warehouseId: p.warehouseId ?? scopeWarehouseId ?? undefined,
      batchNo: p.batchNo,
    }),
    stockApi.freezeLogs,
  );
  // 导出 URL:带当前筛选条件(导出不分页)
  const exportUrl = useMemo(() => {
    const p = new URLSearchParams();
    if (filterParams.warehouseId !== undefined && filterParams.warehouseId !== "") p.set("warehouseId", String(filterParams.warehouseId));
    if (filterParams.itemKeyword !== undefined && filterParams.itemKeyword !== "") p.set("itemKeyword", String(filterParams.itemKeyword));
    if (filterParams.batchNo !== undefined && filterParams.batchNo !== "") p.set("batchNo", String(filterParams.batchNo));
    const qs = p.toString();
    return "/stock/export" + (qs ? `?${qs}` : "");
  }, [filterParams]);


  const columns: ProColumns<StockRow>[] = [
    {
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      hideInTable: true,
      formItemProps: { name: "warehouseId" },
      fieldProps: {
        allowClear: true,
        placeholder: "全部仓库",
        options: warehouses.map((w) => ({
          label: w.warehouseName,
          value: w.id,
        })),
      },
    },
    {
      title: "物品",
      dataIndex: "itemKeyword",
      hideInTable: true,
      fieldProps: { placeholder: "编码或名称", allowClear: true },
    },
    {
      title: "批次号",
      dataIndex: "batchNo",
      hideInTable: true,
      fieldProps: { placeholder: "批次号", allowClear: true },
    },
    {
      title: "仓库",
      dataIndex: ["warehouse", "warehouseName"],
      width: 140,
      ellipsis: true,
      search: false,
    },
    {
      title: "物品",
      width: 240,
      ellipsis: true,
      search: false,
      render: (_v, r) => `${r.item?.itemName ?? "-"} ${r.item?.itemCode ?? "-"}`,
    },
    {
      title: "批次",
      dataIndex: ["batch", "batchNo"],
      width: 140,
      ellipsis: true,
      search: false,
    },
    {
      // V26:冻结位(仓+批次粒度),有则红色 Tag
      title: "冻结",
      dataIndex: "frozen",
      width: 70,
      search: false,
      render: (_v, r) => (r.frozen ? <Tag color="red">冻结</Tag> : null),
    },
    {
      title: "库位",
      dataIndex: ["location", "locationCode"],
      width: 120,
      ellipsis: true,
      search: false,
    },
    {
      title: "数量",
      dataIndex: "quantity",
      width: 140,
      align: "right",
      search: false,
      render: (_v, r) => (
        <span className="num-cell">
          {fmtQty(r.quantity)}
          <span style={{ color: "#9ca3af", marginLeft: 4, fontSize: 12 }}>
            {r.item?.unit ?? ""}
          </span>
        </span>
      ),
    },
    {
      title: "已预占",
      dataIndex: "preAllocatedQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) => (
        <span className="num-cell">
          {fmtQty(r.preAllocatedQty)}
        </span>
      ),
    },
    {
      title: "可用量",
      dataIndex: "availableQty",
      width: 100,
      align: "right",
      search: false,
      render: (_v, r) =>
        r.availableQty == null ? (
          "-"
        ) : (
          <span
            className="num-cell"
            style={{ color: Number(r.availableQty) < 0 ? "#cf1322" : undefined }}
          >
            {fmtQty(r.availableQty)}
          </span>
        ),
    },
    {
      title: "保质期",
      dataIndex: ["batch", "expiryDate"],
      width: 120,
      search: false,
      render: (_v, r) => fmtDate(r.batch?.expiryDate),
    },
    {
      // V26:行内冻结/解冻(按 frozen 切换;无批次行不支持冻结粒度,不渲染)
      title: "操作",
      width: 70,
      search: false,
      render: (_v, r) =>
        canFreeze && r.batch?.batchNo ? (
          r.frozen ? (
            <a onClick={() => onUnfreeze(r)}>解冻</a>
          ) : (
            <a onClick={() => setFreezeTarget(r)}>冻结</a>
          )
        ) : null,
    },
  ];

  // V26:冻结记录视图列(时间/物品/批次/操作/原因/操作人)
  const freezeColumns: ProColumns<FreezeLogRow>[] = [
    {
      title: "仓库",
      dataIndex: "warehouseId",
      valueType: "select",
      hideInTable: true,
      formItemProps: { name: "warehouseId" },
      fieldProps: {
        allowClear: true,
        placeholder: "全部仓库",
        options: warehouses.map((w) => ({
          label: w.warehouseName,
          value: w.id,
        })),
      },
    },
    {
      title: "物品",
      dataIndex: "itemKeyword",
      hideInTable: true,
      fieldProps: { placeholder: "编码或名称", allowClear: true },
    },
    {
      title: "批次号",
      dataIndex: "batchNo",
      hideInTable: true,
      fieldProps: { placeholder: "批次号", allowClear: true },
    },
    {
      title: "时间",
      dataIndex: "createdAt",
      width: 170,
      search: false,
      render: (_v, r) => fmtDateTime(r.createdAt),
    },
    {
      title: "物品",
      width: 240,
      ellipsis: true,
      search: false,
      render: (_v, r) => `${r.itemName ?? "-"} ${r.itemCode ?? "-"}`,
    },
    {
      title: "批次",
      dataIndex: "batchNo",
      width: 140,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.batchNo ?? "-",
    },
    {
      title: "操作",
      dataIndex: "action",
      width: 80,
      search: false,
      render: (_v, r) =>
        r.action === "freeze" ? <Tag color="red">冻结</Tag> : <Tag color="green">解冻</Tag>,
    },
    {
      title: "原因",
      dataIndex: "reason",
      width: 200,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.reason || "-",
    },
    {
      title: "操作人",
      dataIndex: "operator",
      width: 120,
      ellipsis: true,
      search: false,
      render: (_v, r) => r.operator || "-",
    },
  ];

  // 冻结确认(原因必填)
  const onFreezeConfirm = async () => {
    if (!freezeTarget) return;
    if (!freezeReason.trim()) {
      message.warning("请填写冻结原因");
      return;
    }
    setFreezeSubmitting(true);
    try {
      await stockApi.freeze({
        warehouseId: freezeTarget.warehouseId,
        batchNo: freezeTarget.batch!.batchNo,
        reason: freezeReason.trim(),
      });
      message.success("批次已冻结(仅拦截出库/预占,不影响入库)");
      setFreezeTarget(null);
      setFreezeReason("");
      actionRef.current?.reload();
    } catch {
      // 拦截器已提示
    } finally {
      setFreezeSubmitting(false);
    }
  };

  // 解冻(动库存行为,保留确认弹层)
  const onUnfreeze = (r: StockRow) => {
    Modal.confirm({
      title: "确认解冻?",
      content: `解冻后 ${r.batch?.batchNo} 该仓批次恢复出库/预占。`,
      okText: "解冻",
      onOk: async () => {
        try {
          await stockApi.unfreeze({
            warehouseId: r.warehouseId,
            batchNo: r.batch!.batchNo,
          });
          message.success("批次已解冻");
          actionRef.current?.reload();
        } catch {
          // 拦截器已提示
        }
      },
    });
  };

  // 表格偏好:列宽拖拽/列显隐/列序(服务端持久化,库存/冻结记录各自 pageKey)
  const {
    columns: rcColumns,
    scroll: rcScroll,
    columnsState,
    onColumnsChange,
    components: rcComponents,
    optionSetting: rcOptionSetting,
  } = useResizableColumns(
    viewMode === "freeze" ? (freezeColumns as unknown as ProColumns<StockRow>[]) : columns,
    viewMode === "freeze" ? "stock-freeze-logs" : "stock-query",
  );
  return (
    <>
      <ProTable<StockRow>
        actionRef={actionRef}
        rowKey="id"
        locale={{
          emptyText: (
            <EmptyHint
              text={viewMode === "freeze" ? "当前筛选条件下暂无冻结记录" : "当前筛选条件下暂无库存"}
            />
          ),
        }}
        columns={rcColumns}
        request={
          viewMode === "freeze"
            ? (freezeRequest as unknown as typeof request)
            : request
        }
        headerTitle={
          <Segmented
            options={[
              { label: "库存", value: "stock" },
              { label: "冻结记录", value: "freeze" },
            ]}
            value={viewMode}
            onChange={(v) => {
              setViewMode(v as "stock" | "freeze");
              actionRef.current?.reload();
            }}
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
          optionRender: (_searchConfig, _props, dom) => [
            ...dom,
            canExport && viewMode === "stock" && (
              <ExportButton key="export" url={exportUrl} filename="库存.xlsx" />
            ),
          ],
        }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
        scroll={rcScroll}
        rowClassName={(r) =>
          viewMode === "stock" && Number(r.quantity) === 0 ? "row-zero" : ""
        }
      />
      {/* V26:冻结弹层(原因必填;解冻走 Modal.confirm 不用本弹层) */}
      <Modal
        title="冻结批次"
        open={freezeTarget != null}
        okText="确认冻结"
        okButtonProps={{ loading: freezeSubmitting }}
        onOk={onFreezeConfirm}
        onCancel={() => {
          setFreezeTarget(null);
          setFreezeReason("");
        }}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: "100%" }} size="middle">
          <span>
            仓库 {freezeTarget?.warehouse?.warehouseName ?? "-"}
            {" / "}批次 <b>{freezeTarget?.batch?.batchNo ?? "-"}</b>
          </span>
          <span style={{ color: "#9ca3af", fontSize: 12 }}>
            冻结后该仓该批次禁止出库/预占,不影响入库;可随时解冻。
          </span>
          <textarea
            rows={3}
            placeholder="冻结原因(必填,如:质检扣留 / 客诉 / 破损)"
            value={freezeReason}
            onChange={(e) => setFreezeReason(e.target.value)}
            style={{ width: "100%", padding: 8, borderRadius: 6 }}
          />
        </Space>
      </Modal>
    </>
  );
}
