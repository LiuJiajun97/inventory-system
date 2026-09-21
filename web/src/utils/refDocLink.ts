// 关联单据跳转映射:出入库列表"关联单号"链接与详情弹窗共用
// 优先用 refDocId 直跳对应单据的只读详情页(/xxx/new/:id,页面按状态只读);
// 无 id 时退回列表页 ?docNo= 筛选跳转;未知 refType 返回 undefined,调用方按"不跳转"处理
const VIEW_ROUTE_BY_TYPE: Record<string, string> = {
  purchase: "/purchase-orders/new/",
  sales: "/sales-orders/new/",
  purchase_return: "/purchase-returns/new/",
  sales_return: "/sales-returns/new/",
  opening: "/opening/new/",
  // V25 报价单/请购单 详情页
  quotation: "/sales-quotations/new/",
  requisition: "/purchase-requisitions/new/",
};

export function refDocHref(
  refType: string | null | undefined,
  refDocId: number | null | undefined,
  refDocNo: string | null | undefined,
): string | undefined {
  const base = refType ? VIEW_ROUTE_BY_TYPE[refType] : undefined;
  if (!base) return undefined;
  if (refDocId != null) return base + refDocId;
  if (refDocNo) return base.replace("/new/", "") + `?docNo=${encodeURIComponent(refDocNo)}`;
  return undefined;
}
