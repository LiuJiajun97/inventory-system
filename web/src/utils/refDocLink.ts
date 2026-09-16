 // 关联单据跳转映射:出入库列表"关联单号"链接与详情弹窗共用
// 五种 refType 各跳对应单据类型列表页(带 ?docNo= 参数,目标页自动预填筛选);
// 未知 refType 返回 undefined,调用方按"不跳转"处理
export function refDocHref(
  refType: string | null | undefined,
  refDocNo: string | null | undefined,
): string | undefined {
  if (!refDocNo) return undefined;
  switch (refType) {
    case "purchase":
      return `/purchase-orders?docNo=${encodeURIComponent(refDocNo)}`;
    case "sales":
      return `/sales-orders?docNo=${encodeURIComponent(refDocNo)}`;
    case "purchase_return":
      return `/purchase-returns?docNo=${encodeURIComponent(refDocNo)}`;
    case "sales_return":
      return `/sales-returns?docNo=${encodeURIComponent(refDocNo)}`;
    case "opening":
      return `/opening?docNo=${encodeURIComponent(refDocNo)}`;
    default:
      return undefined;
  }
}
