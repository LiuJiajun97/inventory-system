// 与后端一致的 TS 类型

export type Role = "admin" | "operator" | "viewer";

export interface UserInfo {
  id: number;
  username: string;
  name: string;
  role: Role;
  status?: number;
}

export interface LoginResponse {
  token: string;
  user: UserInfo;
}

export interface Warehouse {
  id: number;
  warehouseCode: string;
  warehouseName: string;
  warehouseType: string;
  enableBatch: boolean;
  enableExpiry: boolean;
  enableSerial: boolean;
  enableLocation: boolean;
  status: number;
}

export interface Location {
  id: number;
  warehouseId: number;
  locationCode: string;
  locationName?: string | null;
}

export interface Item {
  id: number;
  itemCode: string;
  itemName: string;
  unit: string;
  spec?: string | null;
  attributes?: string | null;
  category?: string | null;
  minStock?: string | number | null;
  defaultTaxRate?: string | number | null;
}

export interface StockRow {
  id: number;
  warehouseId: number;
  itemId: number;
  batchId: number;
  locationId: number;
  quantity: string | number;
  preAllocatedQty?: string | number;
  availableQty?: string | number;
  warehouse?: Warehouse;
  item?: Item;
  batch?: { batchNo: string; expiryDate?: string | null } | null;
  location?: Location | null;
}

export interface StockTransaction {
  id: number;
  warehouseId: number;
  itemId: number;
  batchId: number;
  locationId: number;
  changeQty: string | number;
  afterQty: string | number;
  bizCode: string;
  docNo?: string | null;
  operator?: string | null;
  createdAt: string;
  warehouse?: Warehouse;
  item?: Item;
  batch?: { batchNo: string } | null;
}

export interface InboundDocItem {
  id: number;
  itemId: number;
  quantity: string | number;
  batchId: number;
  locationId: number;
  serialNos?: string | null;
  unitPrice?: string | number | null;
  taxRate?: string | number | null;
  batchNo?: string | null;
  productionDate?: string | null;
  expiryDate?: string | null;
}

export interface InboundDoc {
  id: number;
  docNo: string;
  warehouseId: number;
  status: string;
  remark?: string | null;
  creator?: string | null;
  createdAt: string;
  warehouse?: Warehouse;
  items?: InboundDocItem[];
  refType?: string | null;
  refDocId?: number | null;
  refDocNo?: string | null;
  supplierName?: string | null;
  docDate?: string | null;
}

export interface OutboundDocItem {
  id: number;
  itemId: number;
  quantity: string | number;
  batchId: number;
  locationId: number;
  serialNos?: string | null;
  unitPrice?: string | number | null;
}

export interface OutboundDoc {
  id: number;
  docNo: string;
  warehouseId: number;
  status: string;
  remark?: string | null;
  creator?: string | null;
  createdAt: string;
  warehouse?: Warehouse;
  items?: OutboundDocItem[];
  refType?: string | null;
  refDocId?: number | null;
  refDocNo?: string | null;
  customerName?: string | null;
  docDate?: string | null;
}

export interface PagedResponse<T> {
  rows: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface DashboardSummary {
  warehouseCount: number;
  itemCount: number;
  stockLineCount: number;
  todayInboundCount: number;
  todayInboundQty: number;
  todayOutboundCount: number;
  todayOutboundQty: number;
}
