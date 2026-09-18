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
  OpeningStockDoc,
  PagedResponse,
  DashboardSummary,
  MonitorOverview,
  UserInfo,
  MenuNode,
} from "../types";
import type {
  Supplier,
  Customer,
  PurchaseOrder,
  SalesOrder,
  PurchaseReturn,
  SalesReturn,
  OperationLog,
  TransferDoc,
  StocktakeDoc,
  StockAdjustDoc,
  ExpiryAlertRow,
  LowStockRow,
  DictItem,
  DictTypeItem,
  DocLine,
  Invoice,
  InvoiceableLine,
  PaymentDoc,
  UnsettledInvoice,
  LedgerRow,
  SettlementDashboard,
} from "../types/phase1";
import type {
  StockMonthlyRow,
  StockAgeingRow,
  PurchaseReconRow,
  SalesReconRow,
  CostReportResult,
} from "../types/report";

export const authApi = {
  login: (username: string, password: string) =>
    http.post<unknown, LoginResponse>("/auth/login", { username, password }),
  changePassword: (oldPassword: string, newPassword: string) =>
    http.post("/auth/password", { oldPassword, newPassword }),
  // 登出:后端吊销当前 token 的 jti(失败也清本地,降级为自然过期)
  logout: () => http.post<unknown, { ok: boolean }>("/auth/logout"),
  me: () => http.get<unknown, UserInfo>("/auth/me"),
  // 当前用户(多角色并集)菜单树:目录/菜单节点 + 各节点 permissions 权限码
  menus: () => http.get<unknown, MenuNode[]>("/auth/menus"),
  // 权限码探针:后端 @RequirePermission 拦截,无权限返回 403
  permCheck: (code: string) =>
    http.get<unknown, { ok: boolean }>("/auth/perm-check", { params: { code } }),
};

// RBAC 角色管理(仅 admin,批 2 角色权限页用;批 1b 顺手建好)
export interface RoleRow {
  id: number;
  roleCode: string;
  roleName: string;
  remark?: string | null;
  isBuiltin: boolean;
  status: number;
  createdAt?: string;
}

export const roleApi = {
  // 角色列表(不分页,管理页用)
  list: () => http.get<unknown, RoleRow[]>("/roles"),
  get: (id: number) => http.get<unknown, RoleRow>(`/roles/${id}`),
  create: (data: { roleCode: string; roleName: string; remark?: string }) =>
    http.post<unknown, RoleRow>("/roles", data),
  update: (
    id: number,
    data: { roleCode?: string; roleName?: string; remark?: string | null; status?: number },
  ) => http.put<unknown, RoleRow>(`/roles/${id}`, data),
  delete: (id: number) => http.delete(`/roles/${id}`),
  // 角色-菜单全量分配(空数组 = 清空)
  assignMenus: (id: number, menuIds: number[]) =>
    http.put(`/roles/${id}/menus`, { menuIds }),
  // 角色已绑定菜单 ID(分配页回显)
  menuIds: (id: number) => http.get<unknown, number[]>(`/roles/${id}/menus`),
};

// RBAC 菜单管理(仅 admin,批 2 菜单管理页用;批 1b 顺手建好)
// 注意:/menus/tree 节点结构含 id/parentId/status,无 permissions(与 /auth/menus 不同)
export interface ManageMenuNode {
  id: number;
  parentId: number;
  menuCode: string;
  menuName: string;
  type: "directory" | "menu" | "button";
  path: string | null;
  sort: number;
  status: number;
  children: ManageMenuNode[];
}

export const menuApi = {
  // 全量菜单树(管理页用,含按钮型节点)
  tree: () => http.get<unknown, ManageMenuNode[]>("/menus/tree"),
  create: (data: {
    parentId: number;
    menuCode: string;
    menuName: string;
    type: "directory" | "menu" | "button";
    path?: string | null;
    sort?: number;
  }) => http.post<unknown, ManageMenuNode>("/menus", data),
  update: (
    id: number,
    data: { parentId?: number; menuName?: string; path?: string | null; sort?: number; status?: number },
  ) => http.put<unknown, ManageMenuNode>(`/menus/${id}`, data),
  delete: (id: number) => http.delete(`/menus/${id}`),
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
  // V9 通用字段(可空)
  contractNo?: string;
  freight?: number;
  shippingAddress?: string;
  // V10 通用字段(可空)
  discountAmount?: number;
  currencyCode?: string;
  exchangeRate?: number;
  remark?: string;
  items: Array<{
    itemId: number;
    orderedQty: number;
    expectedDeliveryDate?: string;
    unitPrice?: number;
    taxPrice?: number;
    taxRate?: number;
    lineRemark?: string;
  }>;
}

export interface SalesCreatePayload {
  docDate: string;
  customerId: number;
  salespersonId: number;
  warehouseId: number;
  // V9 通用字段(可空)
  contractNo?: string;
  freight?: number;
  shippingAddress?: string;
  // V10 通用字段(可空)
  discountAmount?: number;
  currencyCode?: string;
  exchangeRate?: number;
  remark?: string;
  items: Array<{
    itemId: number;
    orderedQty: number;
    customerDeliveryDate?: string;
    unitPrice?: number;
    taxPrice?: number;
    taxRate?: number;
    lineRemark?: string;
  }>;
}

export interface TransferCreatePayload {
  docDate: string;
  fromWarehouseId: number;
  toWarehouseId: number;
  remark?: string;
  // V10 承运商(可空)
  carrier?: string;
  items: Array<{
    itemId: number;
    qty: number;
    unitPrice?: number;
    fromLocationId?: number;
    toLocationId?: number;
    lineRemark?: string;
    // V9 车牌(可空)
    vehicleNo?: string;
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
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    supplierId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/purchase-orders/lines", { params }),
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
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    customerId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/sales-orders/lines", { params }),
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
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    fromWarehouseId?: number;
    toWarehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/transfers/lines", { params }),
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
    lines: Array<{
      lineId: number;
      actualQty: number | null;
      // V9 盘点人/盘点日期(可空)
      checkerName?: string;
      checkDate?: string;
    }>,
  ) =>
    http.put<unknown, StocktakeDoc>(`/stocktakes/${id}/actual`, { lines }),
  refreshBook: (id: number) => http.post(`/stocktakes/${id}/refresh-book`),
  generateAdjust: (id: number) => http.post(`/stocktakes/${id}/adjust`),
  submit: (id: number) => http.post(`/stocktakes/${id}/submit`),
  approve: (id: number) => http.post(`/stocktakes/${id}/approve`),
  reject: (id: number, rejectReason: string) =>
    http.post(`/stocktakes/${id}/reject`, { rejectReason }),
  voidDoc: (id: number) => http.post(`/stocktakes/${id}/void`),
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/stocktakes/lines", { params }),
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
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    warehouseId?: number;
    adjustType?: string;
    docNo?: string;
    status?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/stock-adjusts/lines", { params }),
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
    // V9 运输信息(可空)
    carrier?: string;
    vehicleNo?: string;
    freight?: number;
    // V10 单据类型/经办人(可空;总金额由服务端落)
    docType?: string;
    handler?: string;
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
      // V10 不含税单价/税率(可空;采购到货由服务端按订单行覆盖)
      unitPrice?: number;
      taxRate?: number;
      // V20 含税单价(可空)
      taxPrice?: number;
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
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/inbound/lines", { params }),
};

// V13 期初库存(创建即过账,单事务,每物品×仓库限一次)
export const openingApi = {
  create: (data: {
    warehouseId: number;
    docDate: string;
    remark?: string;
    items: Array<{
      itemId: number;
      quantity: number;
      unitPrice?: number;
      batchNo?: string;
      productionDate?: string;
      expiryDate?: string;
      locationId?: number;
    }>;
  }) => http.post("/opening", data),
  list: (params: {
    page?: number;
    pageSize?: number;
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
  }) => http.get<unknown, PagedResponse<OpeningStockDoc>>("/opening", { params }),
  get: (id: number) => http.get<unknown, OpeningStockDoc>(`/opening/${id}`),
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/opening/lines", { params }),
};

export const outboundApi = {
  create: (data: {
    warehouseId: number;
    remark?: string;
    refType?: string;
    refDocId?: number;
    docDate?: string;
    // V9 运输信息(可空)
    carrier?: string;
    vehicleNo?: string;
    freight?: number;
    // V10 单据类型/经办人(可空;总金额由服务端落)
    docType?: string;
    handler?: string;
    items: Array<{
      itemId: number;
      qty: number;
      batchNo?: string;
      locationId?: number;
      serialNos?: string[];
      refLineId?: number;
      // V10 不含税单价/税率(可空)
      unitPrice?: number;
      taxRate?: number;
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
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/outbound/lines", { params }),
};

export const userApi = {
  list: (params?: { keyword?: string; page?: number; pageSize?: number }) =>
    http.get<unknown, PagedResponse<UserInfo>>("/users", { params }),
  create: (data: {
    username: string;
    password: string;
    name: string;
    // 多角色:角色 ID 列表(首角色同步写已废弃的 role 列)
    roleIds: number[];
    status?: number;
  }) => http.post("/users", data),
  update: (
    id: number,
    data: {
      name?: string;
      // null/缺省=不动;空数组=400(角色不能为空)
      roleIds?: number[];
      // null/缺省=不动;空数组=清空授权
      warehouseIds?: number[];
      status?: number;
      password?: string;
    },
  ) => http.put(`/users/${id}`, data),
};

// ===== V11 退货模块 =====

export interface PurchaseReturnCreatePayload {
  purchaseOrderId: number;
  warehouseId: number;
  docDate: string;
  remark?: string;
  items: Array<{
    purchaseOrderItemId: number;
    quantity: number;
    locationId?: number;
    serialNos?: string[];
  }>;
}

export interface SalesReturnCreatePayload {
  salesOrderId: number;
  warehouseId: number;
  docDate: string;
  remark?: string;
  items: Array<{
    salesOrderItemId: number;
    quantity: number;
    batchNo?: string;
    productionDate?: string;
    expiryDate?: string;
    locationId?: number;
    serialNos?: string[];
  }>;
}

export const purchaseReturnApi = {
  list: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<PurchaseReturn>>("/purchase-returns", { params }),
  get: (id: number) => http.get<unknown, PurchaseReturn>(`/purchase-returns/${id}`),
  create: (data: PurchaseReturnCreatePayload) =>
    http.post<unknown, { id: number; docNo: string; outDocNo: string }>("/purchase-returns", data),
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/purchase-returns/lines", { params }),
};

export const salesReturnApi = {
  list: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<SalesReturn>>("/sales-returns", { params }),
  get: (id: number) => http.get<unknown, SalesReturn>(`/sales-returns/${id}`),
  create: (data: SalesReturnCreatePayload) =>
    http.post<unknown, { id: number; docNo: string; inDocNo: string }>("/sales-returns", data),
  // V17 明细行视图(单据行拍平,支持物品关键字/批次号筛选)
  lines: (params?: {
    warehouseId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    itemKeyword?: string;
    batchNo?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<DocLine>>("/sales-returns/lines", { params }),
};

// 报表中心(V15):4 个报表 + 各自 xlsx 导出(导出走 ExportButton,不另设权限码)
export const reportApi = {
  // 进销存月报(item 维度,跨仓汇总)
  monthly: (params: {
    warehouseId?: number;
    itemId?: number;
    itemKeyword?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<StockMonthlyRow>>("/reports/stock-monthly", { params }),
  // 库龄/呆滞(批次维度)
  ageing: (params: {
    warehouseId?: number;
    itemId?: number;
    itemKeyword?: string;
    ageFrom?: number;
    ageTo?: number;
    stagnantDays?: number;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<StockAgeingRow>>("/reports/stock-ageing", { params }),
  // 采购对账(供应商维度)
  purchaseRecon: (params: {
    supplierId?: number;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<PurchaseReconRow>>("/reports/purchase-recon", { params }),
  // 销售对账(客户维度)
  salesRecon: (params: {
    customerId?: number;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<SalesReconRow>>("/reports/sales-recon", { params }),
  // 库存成本(移动均价,不分页:全量行 + 合计)
  cost: (params?: {
    warehouseId?: number;
    itemId?: number;
    date?: string;
  }) => http.get<unknown, CostReportResult>("/reports/cost", { params }),
};

// 操作日志(V16,仅 admin)
export const operationLogApi = {
  list: (params: {
    username?: string;
    module?: string;
    success?: number;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<OperationLog>>("/operation-logs", { params }),
  // 模块筛选项(后端 LogModule 集中映射)
  modules: () => http.get<unknown, string[]>("/operation-logs/modules"),
};

// 发票(V18 结算域:手工登记正票 + 退货生成红字凭单)
export const invoiceApi = {
  list: (params: {
    invoiceType?: string;
    partyId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<Invoice>>("/invoices", { params }),
  get: (id: number) => http.get<unknown, Invoice>(`/invoices/${id}`),
  create: (data: {
    invoiceType: string;
    partyId: number;
    invoiceDate: string;
    remark?: string;
    items: { srcDocType: string; srcDocId: number; srcDocItemId: number; invoicedAmount: number }[];
  }) => http.post<unknown, Invoice>("/invoices", data),
  update: (
    id: number,
    data: {
      partyId?: number;
      invoiceDate?: string;
      remark?: string;
      items: { srcDocType: string; srcDocId: number; srcDocItemId: number; invoicedAmount: number }[];
    },
  ) => http.put<unknown, Invoice>(`/invoices/${id}`, data),
  confirm: (id: number) => http.post<unknown, Invoice>(`/invoices/${id}/confirm`),
  void: (id: number) => http.post<unknown, Invoice>(`/invoices/${id}/void`),
  // 可挂票源单据行(未开票/部分开票)
  invoiceableLines: (invoiceType: string, partyId: number) =>
    http.get<unknown, InvoiceableLine[]>(`/invoices/invoiceable-lines`, {
      params: { invoiceType, partyId },
    }),
};

// 付款/收款单(V18 结算域:一表一 type,核销行挂 confirmed 正票)
export const paymentApi = {
  list: (params: {
    payType?: string;
    partyId?: number;
    docNo?: string;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
    pageSize?: number;
  }) => http.get<unknown, PagedResponse<PaymentDoc>>("/payments", { params }),
  get: (id: number) => http.get<unknown, PaymentDoc>(`/payments/${id}`),
  create: (data: {
    payType: string;
    partyId: number;
    payDate: string;
    remark?: string;
    lines: { invoiceId: number; amount: number }[];
  }) => http.post<unknown, PaymentDoc>("/payments", data),
  void: (id: number) => http.post<unknown, PaymentDoc>(`/payments/${id}/void`),
  // 可核销票(confirmed 正票剩余可核额)
  unsettledInvoices: (payType: string, partyId: number) =>
    http.get<unknown, UnsettledInvoice[]>("/payments/unsettled-invoices", {
      params: { payType, partyId },
    }),
};

// 结算台账(V18:应付/应收台账 + 仪表盘余额,只读)
export const settlementApi = {
  ap: () => http.get<unknown, LedgerRow[]>("/settlement/ap"),
  ar: () => http.get<unknown, LedgerRow[]>("/settlement/ar"),
  dashboard: () => http.get<unknown, SettlementDashboard>("/settlement/dashboard"),
};

// 表格偏好(列宽拖拽/列显隐/列序,按用户持久化;config 结构见 utils/tablePrefs.ts)
export const tablePrefApi = {
  // 当前用户全部页面列配置(pageKey→config)
  get: () =>
    http.get<
      unknown,
      Record<string, { widths?: Record<string, number>; hidden?: string[]; order?: string[] }>
    >("/table-prefs"),
  // 保存指定页面列配置(upsert)
  save: (pageKey: string, config: { widths?: Record<string, number>; hidden?: string[]; order?: string[] }) =>
    http.put<unknown, { ok: boolean }>(`/table-prefs/${pageKey}`, config),
};
