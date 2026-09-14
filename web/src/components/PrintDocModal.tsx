// 通用单据打印组件(V14)
// 各单据详情弹窗「打印」按钮 → 打开本 Modal,.print-doc-sheet 内渲染 A4 黑白朴素版式,
// 点「打印」调浏览器原生 window.print();打印 @media print 规则见 styles/global.css。
// 纯前端零依赖:明细用原生 <table>(不用 antd Table),状态/签字区打文字,无颜色标签。

import { Fragment, useEffect, useState } from "react";
import { Button, Modal, Space } from "antd";
import dayjs from "dayjs";
import { itemApi, warehouseApi } from "../api";
import { fmtMoney } from "../utils/format";

// 打印抬头公司名(TASK-v22c C6):单机自用固定值,如需更换只改这里
const COMPANY_NAME = "航民达美";

/** 打印明细列定义(标题 + 对齐方式) */
export interface PrintDocColumn {
  title: string;
  align?: "left" | "right" | "center";
}

/** 打印版数据(各页用自己的 buildPrintData 把 detail VO 转成此结构) */
export interface PrintDocData {
  /** 单据类型中文名(标题) */
  title: string;
  /** 单号 */
  docNo: string;
  /** 头部键值对(已用 printHeader 过滤 null/空) */
  header: Array<[string, string]>;
  /** 明细列头 */
  columns: PrintDocColumn[];
  /** 明细行(与 columns 对齐) */
  rows: string[][];
  /** 合计行(与 columns 同长,留空处传 "";仅有金额类单据需要) */
  totals?: string[];
  /** 状态(文字,不用颜色标签) */
  status: string;
  /** 备注(可空) */
  remark?: string | null;
}

/**
 * 构建头部键值对:过滤掉 null/undefined/空串/"-" 的项,只保留有业务含义的字段。
 */
export function printHeader(
  pairs: Array<[string, string | number | null | undefined]>,
): Array<[string, string]> {
  return pairs
    .filter(([, v]) => v != null && String(v).trim() !== "" && v !== "-")
    .map(([k, v]) => [k, String(v)]);
}

/**
 * 金额格式化:千分位 + 固定 2 位小数;null/空返回 null(便于调用方判空)。
 */
export const printMoney = (v: string | number | null | undefined): string | null =>
  v == null || v === "" ? null : fmtMoney(v);

/**
 * 打印用名称映射:ID → 可读文本(物品编码+名称 / 库位码)。
 * 各打印页明细行只有 itemId/locationId 时,用此 hook 的映射函数替换为可读文本;
 * 首次打开打印时懒加载一次(全局组件级,失败静默——映射缺失时回退显示 ID)。
 */
export function usePrintNameMaps() {
  const [itemName, setItemName] = useState<Record<number, string>>({});
  const [locCode, setLocCode] = useState<Record<number, string>>({});
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    if (loaded) return;
    setLoaded(true);
    itemApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => {
        const m: Record<number, string> = {};
        for (const it of r.rows) m[it.id] = it.itemCode ? `${it.itemCode} ${it.itemName}`.trim() : it.itemName;
        setItemName(m);
      })
      .catch(() => undefined);
    // /locations 要求 warehouseId,先取全部仓库再逐仓拉库位
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then(async (r) => {
        const m: Record<number, string> = {};
        for (const w of r.rows) {
          const lr = await warehouseApi.listLocations({ warehouseId: w.id, page: 1, pageSize: 200 });
          for (const l of lr.rows) m[l.id] = l.locationCode;
        }
        setLocCode(m);
      })
      .catch(() => undefined);
  }, [loaded]);
  /** 物品 ID → "编码 名称"(未加载完成回退 ID 文本) */
  const itemText = (id: number | null | undefined): string =>
    id == null ? "" : itemName[id] ?? String(id);
  /** 库位 ID → 库位码(未加载完成回退 ID 文本) */
  const locText = (id: number | null | undefined): string =>
    id == null ? "" : locCode[id] ?? String(id);
  return { itemText, locText };
}

export function PrintDocModal({
  open,
  onClose,
  data,
}: {
  open: boolean;
  onClose: () => void;
  data: PrintDocData | null;
}) {
  // 头部键值对两两分组成行(奇数个补一对空单元格,保持四列网格)
  const headerRows: Array<Array<[string, string]>> = [];
  for (let i = 0; i < (data?.header.length ?? 0); i += 2) {
    headerRows.push((data?.header ?? []).slice(i, i + 2));
  }
  return (
    <Modal
      title={`打印 - ${data?.title ?? ""} ${data?.docNo ?? ""}`}
      open={open && data != null}
      onCancel={onClose}
      width={880}
      className="print-doc-modal"
      // 屏显时限制高度可滚动;打印时由 @media print 覆盖为不限高
      styles={{ body: { maxHeight: "70vh", overflow: "auto" } }}
      footer={
        <Space>
          <Button onClick={onClose}>关闭</Button>
          <Button type="primary" onClick={() => window.print()}>
            打印
          </Button>
        </Space>
      }
    >
      {data && (
        <div className="print-doc-sheet">
          {/* TASK-v22c C6 抬头:左公司名,右单据类型(24px 加粗)+单号(16px) */}
          <div className="print-doc-letterhead">
            <div className="print-doc-company">{COMPANY_NAME}</div>
            <div className="print-doc-docinfo">
              <div className="print-doc-title">{data.title}</div>
              <div className="print-doc-no">{data.docNo}</div>
            </div>
          </div>
          {data.header.length > 0 && (
            <table className="print-doc-header">
              <tbody>
                {headerRows.map((row, i) => (
                  <tr key={i}>
                    {row.map(([k, v]) => (
                      <Fragment key={k}>
                        <td className="print-doc-hlabel">{k}</td>
                        <td>{v}</td>
                      </Fragment>
                    ))}
                    {row.length === 1 && (
                      <>
                        <td className="print-doc-hlabel" />
                        <td />
                      </>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <table className="print-doc-lines">
            <thead>
              <tr>
                {data.columns.map((c) => (
                  <th key={c.title} style={{ textAlign: c.align ?? "left" }}>
                    {c.title}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.rows.map((r, i) => (
                <tr key={i}>
                  {r.map((cell, j) => (
                    <td key={j} style={{ textAlign: data.columns[j]?.align ?? "left" }}>
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
            {data.totals && (
              <tfoot>
                <tr>
                  {data.totals.map((cell, j) => (
                    <td
                      key={j}
                      style={{
                        textAlign: data.columns[j]?.align ?? "left",
                        fontWeight: 600,
                      }}
                    >
                      {cell}
                    </td>
                  ))}
                </tr>
              </tfoot>
            )}
          </table>
          <div className="print-doc-foot">
            <div>状态:{data.status}</div>
            {data.remark ? <div>备注:{data.remark}</div> : null}
            <div className="print-doc-sign">
              <span>制单人:&nbsp;____________</span>
              <span>审批人:&nbsp;____________</span>
            </div>
            {/* TASK-v22c C6 页脚:左页码(简化固定“共 1 页”,单据明细不拆页不真实算页数),
                右打印时间(浏览器本地时区,用户环境东八区) */}
            <div className="print-doc-pagemeta">
              <span>第 1 页 共 1 页</span>
              <span>打印时间 {dayjs().format("YYYY-MM-DD HH:mm:ss")}</span>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
