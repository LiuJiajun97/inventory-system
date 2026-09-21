// 一期新增类型:主数据/采购/销售/调拨/盘点/调整/预警
// 与后端 VO(record) 字段一一对应

import type { Warehouse } from "./index";

// ===== V25 销售报价单 =====

export interface SalesQuotationLine {
  id: number;
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  unit: string;
  quantity: string;
  unitPrice?: string | number | null;
  taxPrice?: string | number | null;
  taxRate: string;
  amount: string;
  taxAmount: string;
  totalAmount: string;
  remark?: string | null;
}

export interface SalesQuotation {
  id: number;
  docNo: string;
  docDate: string;
  customerId: number;
  customer?: Customer | null;
  salespersonId?: number | null;
  warehouseId: number;
  totalAmount: string;
  totalTaxAmount: string;
  totalTaxInclusive: string;
  quoteValidUntil?: string | null;
  status: string; // draft/sent/converted/voided
  expired?: boolean | null; // V25 过期展示标记(状态不变,纯展示)
  creator?: string | null;
  createdAt: string;
  updater?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
  items?: SalesQuotationLine[];
}

// ===== V25 请购单 =====

export interface PurchaseRequisitionLine {
  id: number;
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  unit: string;
  quantity: string;
  expectedDate?: string | null;
  unitPrice?: string | number | null;
  remark?: string | null;
}

export interface PurchaseRequisition {
  id: number;
  docNo: string;
  docDate: string;
  warehouseId: number;
  applicantId: number;
  department?: string | null; // 字典 dept 的 dict_key
  status: string; // draft/submitted/converted/cancelled
  creator?: string | null;
  createdAt: string;
  updater?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
  items?: PurchaseRequisitionLine[];
}

// ===== V25 站内消息 =====

export interface SysMessage {
  id: number;
  receiverId: number;
  type: string; // approval/conversion/alert_daily
  title: string;
  content: string;
  refDocType?: string | null;
  refDocId?: number | null;
  read: boolean;
  createdAt: string;
}

export interface Supplier {
  id: number;
  supplierCode: string;
  supplierName: string;
  taxNo?: string | null;
  defaultTaxRate?: string | number | null;
  contact?: string | null;
  phone?: string | null;
  address?: string | null;
  settleMethod?: string | null;
  payTermDays?: number | null;
  // V9 通用字段(可空)
  bankName?: string | null;
  bankAccount?: string | null;
  creditLimit?: string | number | null;
  deliveryAddress?: string | null;
  // V10 邮箱(可空)
  email?: string | null;
  status: number;
  remark?: string | null;
  creator?: string | null;
  createdAt?: string | null;
}

export interface Customer {
  id: number;
  customerCode: string;
  customerName: string;
  taxNo?: string | null;
  defaultTaxRate?: string | number | null;
  contact?: string | null;
  phone?: string | null;
  address?: string | null;
  settleMethod?: string | null;
  payTermDays?: number | null;
  // V9 通用字段(可空)
  bankName?: string | null;
  bankAccount?: string | null;
  creditLimit?: string | number | null;
  deliveryAddress?: string | null;
  // V10 邮箱(可空)
  email?: string | null;
  status: number;
  remark?: string | null;
  creator?: string | null;
  createdAt?: string | null;
}

/** 单据状态(方案 §4 状态机) */
export type DocStatus =
  | "draft"
  | "pending"
  | "approved"
  | "rejected"
  | "completed"
  | "voided"
  | "closed";

export interface PurchaseOrderLine {
  id: number;
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  specSnapshot?: string | null;
  unit: string;
  orderedQty: string;
  arrivedQty: string;
  returnedQty: string;
  expectedDeliveryDate?: string | null;
  unitPrice: string;
  taxPrice: string;
  taxRate: string;
  amount: string;
  taxAmount: string;
  taxInclusiveTotal: string;
  closed: boolean;
  lineRemark?: string | null;
}

export interface PurchaseOrder {
  id: number;
  docNo: string;
  docDate: string;
  supplierId: number;
  buyerId: number;
  allowOverReceiptRate: string;
  totalAmount: string;
  totalTaxAmount: string;
  totalTaxInclusive: string;
  // V9 通用字段(可空)
  contractNo?: string | null;
  freight?: string | null;
  shippingAddress?: string | null;
  // V10 折扣额/币种/汇率(可空)
  discountAmount?: string | null;
  currencyCode?: string | null;
  exchangeRate?: string | null;
  // V25 关联源单(请购单)
  refDocType?: string | null;
  refDocNo?: string | null;
  refDocId?: number | null;
  status: DocStatus;
  creator?: string | null;
  createdAt: string;
  approver?: string | null;
  approvedAt?: string | null;
  rejectReason?: string | null;
  remark?: string | null;
  items?: PurchaseOrderLine[];
}

export interface SalesOrderLine {
  id: number;
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  specSnapshot?: string | null;
  unit: string;
  orderedQty: string;
  shippedQty: string;
  returnedQty: string;
  customerDeliveryDate?: string | null;
  unitPrice: string;
  taxPrice: string;
  taxRate: string;
  amount: string;
  taxAmount: string;
  taxInclusiveTotal: string;
  closed: boolean;
  lineRemark?: string | null;
}

export interface SalesOrder {
  id: number;
  docNo: string;
  docDate: string;
  customerId: number;
  salespersonId: number;
  warehouseId: number;
  totalAmount: string;
  totalTaxAmount: string;
  totalTaxInclusive: string;
  // V9 通用字段(可空)
  contractNo?: string | null;
  freight?: string | null;
  shippingAddress?: string | null;
  // V10 折扣额/币种/汇率(可空)
  discountAmount?: string | null;
  currencyCode?: string | null;
  exchangeRate?: string | null;
  // V25 关联源单(销售报价单)
  refDocType?: string | null;
  refDocNo?: string | null;
  refDocId?: number | null;
  status: DocStatus;
  creator?: string | null;
  createdAt: string;
  approver?: string | null;
  approvedAt?: string | null;
  rejectReason?: string | null;
  remark?: string | null;
  items?: SalesOrderLine[];
}

export interface TransferLine {
  id: number;
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  specSnapshot?: string | null;
  unit: string;
  qty: string;
  unitPrice: string;
  fromLocationId?: number | null;
  toLocationId?: number | null;
  lineRemark?: string | null;
  // V9 车牌(可空)
  vehicleNo?: string | null;
}

export interface TransferDoc {
  id: number;
  docNo: string;
  docDate: string;
  fromWarehouseId: number;
  toWarehouseId: number;
  totalAmount: string;
  status: DocStatus;
  creator?: string | null;
  createdAt: string;
  approver?: string | null;
  approvedAt?: string | null;
  rejectReason?: string | null;
  remark?: string | null;
  // V10 承运商(可空)
  carrier?: string | null;
  items?: TransferLine[];
}

export interface StocktakeLine {
  id: number;
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  specSnapshot?: string | null;
  unit: string;
  batchId: number;
  locationId: number;
  bookQty: string;
  actualQty?: string | null;
  diffQty?: string | null;
  // V9 盘点人/盘点日期(可空)
  checkerName?: string | null;
  checkDate?: string | null;
}

export interface StocktakeDoc {
  id: number;
  docNo: string;
  docDate: string;
  warehouseId: number;
  scopeType: string;
  status: DocStatus;
  adjustGenerated?: boolean;
  creator?: string | null;
  createdAt: string;
  approver?: string | null;
  approvedAt?: string | null;
  rejectReason?: string | null;
  remark?: string | null;
  items?: StocktakeLine[];
}

export interface StockAdjustLine {
  id: number;
  lineNo: number;
  itemId: number;
  itemCode: string;
  itemName: string;
  specSnapshot?: string | null;
  unit: string;
  batchId: number;
  locationId: number;
  qty: string;
  unitPrice?: string | null;
  reason?: string | null;
}

export interface StockAdjustDoc {
  id: number;
  docNo: string;
  docDate: string;
  warehouseId: number;
  adjustType: string;
  refDocNo?: string | null;
  status: DocStatus;
  creator?: string | null;
  createdAt: string;
  approver?: string | null;
  approvedAt?: string | null;
  rejectReason?: string | null;
  remark?: string | null;
  items?: StockAdjustLine[];
}

export interface ExpiryAlertRow {
  itemId: number;
  itemCode: string;
  itemName: string;
  unit: string;
  batchNo: string;
  warehouseId: number;
  warehouseName: string;
  productionDate?: string | null;
  expiryDate: string;
  daysLeft: number;
  quantity: string;
  availableQty: string;
}

export interface LowStockRow {
  itemId: number;
  itemCode: string;
  itemName: string;
  unit: string;
  minStock: string | null;
  totalQty: string;
  availableQty: string;
}

/** 字典项(管理界面用) */
export interface DictItem {
  id: number;
  dictType: string;
  dictKey: string;
  dictLabel: string;
  sortOrder: number;
  status: number;
}

/** 字典类型(动态管理) */
export interface DictTypeItem {
  id: number;
  typeCode: string;
  typeName: string;
  remark: string;
  status: number;
  enabledCount: number;
}

// ===== V11 退货模块 =====

export interface PurchaseReturnLine {
  id: number;
  docId: number;
  lineNo: number;
  purchaseOrderItemId: number;
  itemId: number;
  specSnapshot?: string | null;
  unit: string;
  quantity: string;
  unitPrice: string;
  taxPrice: string;
  taxRate: string;
  amount: string;
  taxAmount: string;
  taxInclusiveTotal: string;
}

export interface PurchaseReturn {
  id: number;
  docNo: string;
  docDate: string;
  purchaseOrderId: number;
  purchaseOrderNo?: string | null;
  warehouseId: number;
  warehouse?: Warehouse | null;
  totalAmount?: string | null;
  remark?: string | null;
  status: string;
  creator?: string | null;
  createdAt: string;
  items?: PurchaseReturnLine[];
}

export interface SalesReturnLine {
  id: number;
  docId: number;
  lineNo: number;
  salesOrderItemId: number;
  itemId: number;
  specSnapshot?: string | null;
  unit: string;
  quantity: string;
  unitPrice: string;
  taxPrice: string;
  taxRate: string;
  amount: string;
  taxAmount: string;
  taxInclusiveTotal: string;
}

export interface SalesReturn {
  id: number;
  docNo: string;
  docDate: string;
  salesOrderId: number;
  salesOrderNo?: string | null;
  warehouseId: number;
  warehouse?: Warehouse | null;
  totalAmount?: string | null;
  remark?: string | null;
  status: string;
  creator?: string | null;
  createdAt: string;
  items?: SalesReturnLine[];
}

// ===== V12 导入导出 =====

// 导入失败行
export interface ImportFailedRow {
  row: number; // Excel 行号(第 1 行为表头)
  code: string;
  reason: string;
}

// 导入结果:{imported, failed}
export interface ImportResult {
  imported: number;
  failed: ImportFailedRow[];
}

// ===== V16 操作日志 =====

// 操作日志行(写操作执行流水)
export interface OperationLog {
  id: number;
  username: string;
  ip?: string | null;
  module: string;
  action: string; // POST/PUT/DELETE
  path: string;
  targetType?: string | null;
  targetId?: number | null;
  success: number; // 1 成功 0 失败
  errorMsg?: string | null;
  costMs?: number | null;
  createdAt: string;
}

// V17 单据明细行(10 类单据主表/明细切换的拍平视图,各类缺少的字段可为空)
export interface DocLine {
  id: number;
  docId: number;
  docNo: string;
  docDate?: string | null;
  status: string;
  supplierName?: string | null;
  customerName?: string | null;
  warehouseName?: string | null;
  fromWarehouseName?: string | null;
  toWarehouseName?: string | null;
  lineNo?: number | null;
  itemId: number;
  itemCode: string;
  itemName: string;
  spec?: string | null;
  unit?: string | null;
  quantity?: string | null;
  bookQty?: string | null;
  actualQty?: string | null;
  diffQty?: string | null;
  unitPrice?: string | null;
  taxPrice?: string | null; // V23 含税单价(后端 DocLine VO 已返回,可空)
  taxRate?: string | null;
  amount?: string | null;
  taxAmount?: string | null;
  taxInclusiveTotal?: string | null;
  batchNo?: string | null;
}

// V18 结算域:发票 / 付款收款 / 台账

export interface InvoiceItemRow {
  id: number;
  invoiceId: number;
  lineNo?: number | null;
  srcDocType: string;
  srcDocNo?: string | null;
  srcDocId: number;
  srcDocItemId: number;
  itemId: number;
  itemCode?: string | null;
  itemName?: string | null;
  specSnapshot?: string | null;
  unit?: string | null;
  quantity?: number | null;
  invoicedAmount: number;
  srcAmount?: number | null;
  variance: number;
  sign: string;
  batchNo?: string | null;
}

export interface Invoice {
  id: number;
  docNo: string;
  invoiceType: "purchase" | "sales";
  partyId: number;
  partyName?: string | null;
  invoiceDate: string;
  totalAmount: number;
  status: "draft" | "mismatch" | "confirmed" | "voided";
  sign: "positive" | "negative";
  sourceType: "manual" | "return_gen";
  refReturnId?: number | null;
  refReturnNo?: string | null;
  remark?: string | null;
  creator?: string | null;
  createdAt?: string | null;
  updater?: string | null;
  updatedAt?: string | null;
  items?: InvoiceItemRow[];
}

export interface InvoiceableLine {
  srcDocType: string;
  srcDocId: number;
  srcDocItemId: number;
  srcDocNo: string;
  docDate?: string | null;
  itemId: number;
  itemCode: string;
  itemName: string;
  spec?: string | null;
  unit?: string | null;
  quantity?: number | null;
  srcAmount: number;
  invoicedSoFar: number;
  remaining: number;
}

export interface PaymentLineRow {
  id: number;
  paymentId: number;
  invoiceId: number;
  invoiceNo?: string | null;
  amount: number;
}

export interface PaymentDoc {
  id: number;
  docNo: string;
  payType: "payment" | "receipt";
  partyId: number;
  partyName?: string | null;
  payDate?: string | null;
  totalAmount: number;
  status: "confirmed" | "voided";
  remark?: string | null;
  creator?: string | null;
  createdAt?: string | null;
  lines?: PaymentLineRow[];
}

export interface UnsettledInvoice {
  invoiceId: number;
  docNo: string;
  invoiceDate: string;
  totalAmount: number;
  settledAmount: number;
  remainingAmount: number;
}

export interface LedgerInvoiceRow {
  invoiceId: number;
  docNo: string;
  invoiceDate: string;
  totalAmount: number;
  sign: string;
  sourceType: string;
  settledAmount: number;
}

export interface OrderProgressRow {
  orderId: number;
  orderNo: string;
  docDate?: string | null;
  orderAmount: number;
  receivedAmount: number;
  invoicedAmount: number;
  settledAmount: number;
}

export interface LedgerRow {
  partyId: number;
  partyName: string;
  receivedAmount: number;
  invoicedAmount: number;
  estimatedAmount: number;
  settledAmount: number;
  balance: number;
  invoices: LedgerInvoiceRow[];
  orders: OrderProgressRow[];
}

export interface SettlementDashboard {
  apBalance: number;
  arBalance: number;
}

/**
 * 统一 API 错误体(与后端 GlobalExceptionHandler 对齐)。
 * 业务错(401/403/404 等)带 code;参数校验/兜底错无 code;
 * traceId 由 TraceIdFilter 注入,用于前后端联调排障。
 */
export interface ApiErrorBody {
  statusCode: number;
  code?: string;
  error: string;
  message: string;
  /** 请求链路追踪 ID(与响应头 X-Trace-Id 一致,可空) */
  traceId?: string;
}
