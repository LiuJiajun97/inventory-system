// 报表中心类型(与后端 V15 ReportController 出参一致)
// 数量/金额均为字符串(后端契约),金额可为 null(无快照)

// 月报行展开:各仓期末明细
export interface StockMonthlyDetail {
  warehouseId: number;
  warehouseName: string | null;
  closingQty: string;
}

// 进销存月报行(item 维度,跨仓汇总)
export interface StockMonthlyRow {
  itemId: number;
  itemCode: string | null;
  itemName: string | null;
  unit: string | null;
  spec: string | null;
  openingQty: string;
  inQty: string;
  outQty: string;
  closingQty: string;
  inAmount: string | null;
  outAmount: string | null;
  details: StockMonthlyDetail[];
}

// 库龄/呆滞行(批次维度;无批次仓为 item+仓库 汇总行,batchId=0)
export interface StockAgeingRow {
  warehouseId: number;
  warehouseName: string | null;
  itemId: number;
  batchId: number;
  itemCode: string | null;
  itemName: string | null;
  unit: string | null;
  batchNo: string | null;
  productionDate: string | null;
  ageDays: number | null;
  // 库龄区间文字:0-30 / 31-90 / 91-180 / >180 / 未知
  ageBucket: string;
  quantity: string;
  stagnant: boolean;
}

// 对账行展开:期间内单据明细
export interface ReconDocDetail {
  docNo: string;
  docDate: string;
  docType: string;
  docTypeName: string;
  amount: string | null;
}

// 采购对账行(供应商维度)
export interface PurchaseReconRow {
  supplierId: number;
  supplierCode: string | null;
  supplierName: string | null;
  orderCount: number;
  orderQty: string;
  orderAmount: string | null;
  returnQty: string;
  returnAmount: string | null;
  netQty: string;
  netAmount: string | null;
  details: ReconDocDetail[];
}

// 销售对账行(客户维度)
export interface SalesReconRow {
  customerId: number;
  customerCode: string | null;
  customerName: string | null;
  orderCount: number;
  orderQty: string;
  orderAmount: string | null;
  returnQty: string;
  returnAmount: string | null;
  netQty: string;
  netAmount: string | null;
  details: ReconDocDetail[];
}

// 库存成本(移动均价)报表行(成本单元 = 仓库+物品+批次,batchId=0 为无批次单元)
export interface CostReportRow {
  warehouseId: number;
  warehouseName: string | null;
  itemId: number;
  itemCode: string | null;
  itemName: string | null;
  unit: string | null;
  batchId: number;
  batchNo: string | null;
  quantity: string;
  // 均价 4 位小数(2.625 这类均价 2 位会失真)
  avgPrice: string;
  amount: string;
}

// 库存成本报表出参:全量行 + 合计行
export interface CostReportResult {
  rows: CostReportRow[];
  totalQuantity: string;
  totalAmount: string;
}
