// 一期新增类型:主数据/采购/销售/调拨/盘点/调整/预警
// 与后端 VO(record) 字段一一对应

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
  expectedDeliveryDate?: string | null;
  unitPrice: string;
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
  customerDeliveryDate?: string | null;
  unitPrice: string;
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
