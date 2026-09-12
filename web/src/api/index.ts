// 各模块 API
import { http } from "./http";
import type {
  LoginResponse,
  Warehouse,
  Location,
  Item,
  StockRow,
  StockTransaction,
  InboundDoc,
  OutboundDoc,
  PagedResponse,
  DashboardSummary,
  MonitorOverview,
  UserInfo,
  Role,
} from "../types";
import type {
  Supplier,
  Customer,
  PurchaseOrder,
  SalesOrder,
  TransferDoc,
  StocktakeDoc,
  StockAdjustDoc,
  ExpiryAlertRow,
  LowStockRow,
  DictItem,
  DictTypeItem,
} from "../types/phase1";

export const authApi = {
  login: (username: string, password: string) =>
    http.post<unknown, LoginResponse>("/auth/login", { username, password }),
  changePassword: (oldPassword: string, newPassword: string) =>
    http.post("/auth/password", { oldPassword, newPassword }),
  me: () => http.get<unknown, UserInfo>("/auth/me"),
};

export const dashboardApi = {
  summary: () => http.get<unknown, DashboardSummary>("/dashboard/summary"),
};

export const monitorApi = {
  // 轮询高频调用,失败静默(由页面按 ref 只提示一次)
  overview: () => http.get<unknown, MonitorOverview>("/monitor/overview", { silent: true }),
};

export const warehouseApi = {
  // 列表已改为分页接口;下拉框等需全量时传 pageSize=200
  list: (params?: { keyword?: string; warehouseType?: string; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<Warehouse>>("/warehouses", { params }),
  get: (id: number) => http.get<unknown, Warehouse>(`/warehouses/${id}`),
  create: (data: Omit<Warehouse, "id" | "status"> & { status?: number }) =>
    http.post("/warehouses", data),
  update: (
    id: number,
    data: Partial<Omit<Warehouse, "id" | "warehouseCode">>,
  ) => http.put(`/warehouses/${id}`, data),
  listLocations: (params?: { warehouseId?: number; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<Location>>("/locations", { params }),
  createLocation: (data: { warehouseId: number; locationCode: string; locationName?: string }) =>
    http.post("/locations", data),
  updateLocation: (
    id: number,
    data: Partial<{ locationName: string | null }>,
  ) => http.put(`/locations/${id}`, data),
};

export const itemApi = {
  list: (params?: { keyword?: string; itemCategory?: string; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<Item>>("/items", { params }),
  get: (id: number) => http.get<unknown, Item>(`/items/${id}`),
  create: (data: Omit<Item, "id">) => http.post("/items", data),
  update: (
    id: number,
    data: Partial<Omit<Item, "id" | "itemCode">> & { status?: number },
  ) => http.put(`/items/${id}`, data),
};

export const supplierApi = {
  list: (params?: { keyword?: string; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<Supplier>>("/suppliers", { params }),
  get: (id: number) => http.get<unknown, Supplier>(`/suppliers/${id}`),
  create: (data: Omit<Supplier, "id" | "createdBy" | "createdAt" | "status"> & { status?: number }) =>
    http.post("/suppliers", data),
  update: (id: number, data: Partial<Omit<Supplier, "id" | "supplierCode">>) =>
    http.put(`/suppliers/${id}`, data),
};

export const customerApi = {
  list: (params?: { keyword?: string; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<Customer>>("/customers", { params }),
  get: (id: number) => http.get<unknown, Customer>(`/customers/${id}`),
  create: (data: Omit<Customer, "id" | "createdBy" | "createdAt" | "status"> & { status?: number }) =>
    http.post("/customers", data),
  update: (id: number, data: Partial<Omit<Customer, "id" | "customerCode">>) =>
    http.put(`/customers/${id}`, data),
};

export interface PurchaseCreatePayload {
  docDate: string;
  supplierId: number;
  buyerId: number;
  allowOverReceiptRate?: number;
  remark?: string;
  items: Array<{
    itemId: number;
    orderedQty: number;
    expectedDeliveryDate?: string;
    unitPrice: number;
    taxRate?: number;
    lineRemark?: string;
  }>;
}

export interface SalesCreatePayload {
  docDate: string;
  customerId: number;
  salespersonId: number;
  warehouseId: number;
  remark?: string;
  items: Array<{
    itemId: number;
    orderedQty: number;
    customerDeliveryDate?: string;
    unitPrice: number;
    taxRate?: number;
    lineRemark?: string;
  }>;
}

export interface TransferCreatePayload {
  docDate: string;
  fromWarehouseId: number;
  toWarehouseId: number;
  remark?: string;
  items: Array<{
    itemId: number;
    qty: number;
    unitPrice?: number;
    fromLocationId?: number;
    toLocationId?: number;
    lineRemark?: string;
  }>;
}

export interface StocktakeCreatePayload {
  warehouseId: number;
  docDate: string;
  scopeType: "all" | "item";
  itemIds?: number[];
  remark?: string;
}

export interface AdjustCreatePayload {
  warehouseId: number;
  docDate: string;
  adjustType: "gain" | "loss" | "scrap";
  refDocNo?: string;
  remark?: string;
  items: Array<{
    itemId: number;
    qty: number;
    unitPrice?: number;
    batchId?: number;
    locationId?: number;
    reason?: string;
  }>;
}

export const purchaseApi = {
  list: (params?: {
    supplierId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<PurchaseOrder>>("/purchase-orders", { params }),
  get: (id: number) => http.get<unknown, PurchaseOrder>(`/purchase-orders/${id}`),
  create: (data: PurchaseCreatePayload) =>
    http.post<unknown, PurchaseOrder>("/purchase-orders", data),
  update: (id: number, data: PurchaseCreatePayload) =>
    http.put(`/purchase-orders/${id}`, data),
  submit: (id: number) => http.post(`/purchase-orders/${id}/submit`),
  approve: (id: number) => http.post(`/purchase-orders/${id}/approve`),
  reject: (id: number, rejectReason: string) =>
    http.post(`/purchase-orders/${id}/reject`, { rejectReason }),
  voidDoc: (id: number) => http.post(`/purchase-orders/${id}/void`),
  close: (id: number) => http.post(`/purchase-orders/${id}/close`),
};

export const salesApi = {
  list: (params?: {
    customerId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<SalesOrder>>("/sales-orders", { params }),
  get: (id: number) => http.get<unknown, SalesOrder>(`/sales-orders/${id}`),
  create: (data: SalesCreatePayload) =>
    http.post<unknown, SalesOrder>("/sales-orders", data),
  update: (id: number, data: SalesCreatePayload) =>
    http.put(`/sales-orders/${id}`, data),
  submit: (id: number) => http.post(`/sales-orders/${id}/submit`),
  approve: (id: number) => http.post(`/sales-orders/${id}/approve`),
  reject: (id: number, rejectReason: string) =>
    http.post(`/sales-orders/${id}/reject`, { rejectReason }),
  voidDoc: (id: number) => http.post(`/sales-orders/${id}/void`),
  close: (id: number) => http.post(`/sales-orders/${id}/close`),
};

export const transferApi = {
  list: (params?: {
    fromWarehouseId?: number;
    toWarehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<TransferDoc>>("/transfers", { params }),
  get: (id: number) => http.get<unknown, TransferDoc>(`/transfers/${id}`),
  create: (data: TransferCreatePayload) =>
    http.post<unknown, TransferDoc>("/transfers", data),
  update: (id: number, data: TransferCreatePayload) =>
    http.put(`/transfers/${id}`, data),
  submit: (id: number) => http.post(`/transfers/${id}/submit`),
  approve: (id: number) => http.post(`/transfers/${id}/approve`),
  reject: (id: number, rejectReason: string) =>
    http.post(`/transfers/${id}/reject`, { rejectReason }),
  voidDoc: (id: number) => http.post(`/transfers/${id}/void`),
};

export const stocktakeApi = {
  list: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<StocktakeDoc>>("/stocktakes", { params }),
  get: (id: number) => http.get<unknown, StocktakeDoc>(`/stocktakes/${id}`),
  create: (data: StocktakeCreatePayload) =>
    http.post<unknown, StocktakeDoc>("/stocktakes", data),
  enterActual: (
    id: number,
    lines: Array<{ lineId: number; actualQty: number | null }>,
  ) =>
    http.put<unknown, StocktakeDoc>(`/stocktakes/${id}/actual`, { lines }),
  refreshBook: (id: number) => http.post(`/stocktakes/${id}/refresh-book`),
  generateAdjust: (id: number) => http.post(`/stocktakes/${id}/adjust`),
  submit: (id: number) => http.post(`/stocktakes/${id}/submit`),
  approve: (id: number) => http.post(`/stocktakes/${id}/approve`),
  reject: (id: number, rejectReason: string) =>
    http.post(`/stocktakes/${id}/reject`, { rejectReason }),
  voidDoc: (id: number) => http.post(`/stocktakes/${id}/void`),
};

export const adjustApi = {
  list: (params?: {
    warehouseId?: number;
    adjustType?: string;
    docNo?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<StockAdjustDoc>>("/stock-adjusts", { params }),
  get: (id: number) => http.get<unknown, StockAdjustDoc>(`/stock-adjusts/${id}`),
  create: (data: AdjustCreatePayload) =>
    http.post<unknown, StockAdjustDoc>("/stock-adjusts", data),
  update: (id: number, data: AdjustCreatePayload) =>
    http.put<unknown, StockAdjustDoc>(`/stock-adjusts/${id}`, data),
  submit: (id: number) => http.post(`/stock-adjusts/${id}/submit`),
  approve: (id: number) => http.post(`/stock-adjusts/${id}/approve`),
  reject: (id: number, rejectReason: string) =>
    http.post(`/stock-adjusts/${id}/reject`, { rejectReason }),
  voidDoc: (id: number) => http.post(`/stock-adjusts/${id}/void`),
};

export const alertApi = {
  expiry: (params?: { warehouseId?: number; itemId?: number; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<ExpiryAlertRow>>("/alerts/expiry", { params }),
  lowStock: (params?: { itemId?: number; itemKeyword?: string; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<LowStockRow>>("/alerts/low-stock", { params }),
};

export const dictApi = {
  getTypes: () =>
    http.get<unknown, DictTypeItem[]>("/dicts/types"),
  createType: (data: { typeCode: string; typeName: string; remark?: string }) =>
    http.post<unknown, DictTypeItem>("/dicts/types", data),
  updateType: (typeCode: string, data: { typeName?: string; remark?: string; status?: number }) =>
    http.put<unknown, DictTypeItem>(`/dicts/types/${typeCode}`, data),
  getType: (type: string) =>
    http.get<unknown, Array<{ code: string; label: string }>>("/dicts", { params: { type } }),
  getAll: (type: string) =>
    http.get<unknown, DictItem[]>("/dicts/all", { params: { type } }),
  create: (data: { dictType: string; dictKey: string; dictLabel: string; sortOrder?: number; status?: number }) =>
    http.post<unknown, DictItem>("/dicts", data),
  update: (id: number, data: { dictLabel?: string; sortOrder?: number }) =>
    http.put<unknown, DictItem>(`/dicts/${id}`, data),
  setStatus: (id: number, status: number) =>
    http.post(`/dicts/${id}/status`, { status }),
};

export const stockApi = {
  query: (params: {
    warehouseId?: number;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<StockRow>>("/stock", { params }),
};

export const transactionApi = {
  query: (params: {
    page?: number;
    pageSize?: number;
    warehouseId?: number;
    itemId?: number;
    from?: string;
    to?: string;
    bizCode?: string;
  }) => http.get<unknown, PagedResponse<StockTransaction>>("/transactions", { params }),
};

export const inboundApi = {
  create: (data: {
    warehouseId: number;
    remark?: string;
    refType?: string;
    refDocId?: number;
    docDate?: string;
    items: Array<{
      itemId: number;
      qty: number;
      batchNo?: string;
      expiryDate?: string;
      productionDate?: string;
      supplier?: string;
      locationId?: number;
      serialNos?: string[];
      refLineId?: number;
    }>;
  }) => http.post("/inbound", data),
  list: (params: {
    page?: number;
    pageSize?: number;
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
  }) => http.get<unknown, PagedResponse<InboundDoc>>("/inbound", { params }),
  get: (id: number) => http.get<unknown, InboundDoc>(`/inbound/${id}`),
};

export const outboundApi = {
  create: (data: {
    warehouseId: number;
    remark?: string;
    refType?: string;
    refDocId?: number;
    docDate?: string;
    items: Array<{
      itemId: number;
      qty: number;
      batchNo?: string;
      locationId?: number;
      serialNos?: string[];
      refLineId?: number;
    }>;
  }) => http.post("/outbound", data),
  list: (params: {
    page?: number;
    pageSize?: number;
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
  }) => http.get<unknown, PagedResponse<OutboundDoc>>("/outbound", { params }),
  get: (id: number) => http.get<unknown, OutboundDoc>(`/outbound/${id}`),
};

export const userApi = {
  list: (params?: { keyword?: string; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<UserInfo>>("/users", { params }),
  create: (data: {
    username: string;
    password: string;
    name: string;
    role: Role;
    status?: number;
  }) => http.post("/users", data),
  update: (
    id: number,
    data: { name?: string; role?: Role; status?: number; password?: string },
  ) => http.put(`/users/${id}`, data),
};
