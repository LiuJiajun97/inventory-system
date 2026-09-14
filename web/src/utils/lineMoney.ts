// V20 价税六列口径:与后端 MoneyUtils 完全一致(4 位小数 HALF_UP)
// 金额 = 数量×不含税单价;税额 = 金额×税率;含税金额 = 金额+税额
// 含税输入时:含税金额 = 数量×含税单价;金额 = 含税金额/(1+税率);税额 = 含税金额-金额
// 前端仅做实时预览;提交两单价都传,服务端按"不含税优先"裁决重算,以服务端为准。

/** 4 位小数四舍五入 */
export function r4(n: number): number {
  return Math.round((n + Number.EPSILON) * 10000) / 10000;
}

export interface LineMoneyPreview {
  amount: number;
  tax: number;
  inclusive: number;
}

/**
 * 按行数据预览价税三列(与后端同口径):不含税单价优先,否则含税单价,都没有返回空。
 *
 * @param qty       数量
 * @param unitPrice 不含税单价(可空)
 * @param taxPrice  含税单价(可空)
 * @param taxRate   税率百分数(可空按 0)
 */
export function previewLineMoney(
  qty: number,
  unitPrice: number | null | undefined,
  taxPrice: number | null | undefined,
  taxRate: number | null | undefined,
): LineMoneyPreview | null {
  const rate = taxRate ?? 0;
  if (qty == null) return null;
  if (unitPrice != null) {
    const amount = r4(qty * unitPrice);
    const tax = r4((amount * rate) / 100);
    return { amount, tax, inclusive: r4(amount + tax) };
  }
  if (taxPrice != null) {
    const inclusive = r4(qty * taxPrice);
    const amount = r4(inclusive / (1 + rate / 100));
    const tax = r4(inclusive - amount);
    return { amount, tax, inclusive };
  }
  return null;
}

/** 不含税单价反算(含税输入时展示用):含税金额÷(1+税率)÷数量 */
export function exclusiveUnitOf(
  qty: number,
  taxPrice: number,
  taxRate: number | null | undefined,
): number {
  if (!qty) return 0;
  const inclusive = qty * taxPrice;
  return r4(r4(inclusive / (1 + (taxRate ?? 0) / 100)) / qty);
}

/**
 * 双单价双向联动:按"用户刚改的字段"(primary)和当前税率实时重算另一个单价。
 * 税率未知不重算;主字段值为空(用户清空)返回空对象,避免清空时又填回来。
 *
 * @param unitPrice 当前不含税单价
 * @param taxPrice  当前含税单价
 * @param taxRate   税率百分数(可空)
 * @param primary   用户本次改的字段:unit=不含税 tax=含税 rate=税率(税率改时优先按不含税正算)
 */
export function recalcPricePair(
  unitPrice: number | null | undefined,
  taxPrice: number | null | undefined,
  taxRate: number | null | undefined,
  primary: "unit" | "tax" | "rate",
): { unitPrice?: number; taxPrice?: number } {
  if (taxRate == null) return {};
  if (primary === "unit" && unitPrice != null) {
    return { taxPrice: r4(unitPrice * (1 + taxRate / 100)) };
  }
  if (primary === "tax" && taxPrice != null) {
    return { unitPrice: r4(taxPrice / (1 + taxRate / 100)) };
  }
  if (primary === "rate") {
    if (unitPrice != null) return { taxPrice: r4(unitPrice * (1 + taxRate / 100)) };
    if (taxPrice != null) return { unitPrice: r4(taxPrice / (1 + taxRate / 100)) };
  }
  return {};
}

/**
 * 双单价一致性提示(纯展示,不参与校验):两单价都填时,
 * 按税率推算的含税单价 = 不含税×(1+税率/100),与所填含税单价不等(容差 0.0001)就提示。
 * 税率 0 时自然退化为"两价不等即提示"。
 *
 * @param unitPrice 不含税单价(可空)
 * @param taxPrice  含税单价(可空)
 * @param taxRate   税率百分数(可空按 0)
 */
export function priceMismatchHint(
  unitPrice: number | null | undefined,
  taxPrice: number | null | undefined,
  taxRate: number | null | undefined,
): string | null {
  if (unitPrice == null || taxPrice == null) return null;
  const rate = taxRate ?? 0;
  const expected = r4(Number(unitPrice) * (1 + rate / 100));
  if (Math.abs(expected - Number(taxPrice)) <= 0.0001) return null;
  return `税率 ${rate}%,不含税 ${unitPrice}×(1+${rate}%)≈${expected},与所填含税单价 ${taxPrice} 不一致,请核对`;
}

/** 含税单价反算(不含税输入时展示用):(金额+税额)÷数量 */
export function taxUnitOf(inclusive: number, qty: number): number {
  if (!qty) return 0;
  return r4(inclusive / qty);
}
