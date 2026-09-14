// 与后端一致的 TS 类型

export type Role = "admin" | "operator" | "viewer";

// 用户角色条目(多角色,与后端 UserVO.roles 一致)
export interface UserRoleItem {
  code: string;
  name: string;
}

export interface UserInfo {
  id: number;
  username: string;
  name: string;
  // 已废弃:首角色(兼容保留),多角色请看 roles
  role: Role;
  status?: number;
  createdAt?: string;
  // 多角色列表(编码 + 名称)
  roles?: UserRoleItem[];
  // 仓库授权 ID 列表(数据权限,admin 豁免)
  warehouseIds?: number[];
}

export interface LoginResponse {
  token: string;
  user: UserInfo;
}

// 动态菜单节点(与 GET /auth/menus、/menus/tree 返回结构一致)
// type: directory=目录(一级分组) / menu=菜单页(有 path) / button=按钮权限点(无 path,以 permissions 挂叶子菜单)
export interface MenuNode {
  menuCode: string;
  menuName: string;
  path: string | null;
  sort: number;
  type: "directory" | "menu" | "button";
  permissions: string[];
  children: MenuNode[];
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
  // V10 默认仓(全表至多一个 true,可空)
  defaultWarehouse?: boolean | null;
  creator?: string | null;
  createdAt?: string | null;
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
  // V9 通用字段(可空):条码/辅助单位/换算率/品牌
  barcode?: string | null;
  secondUnit?: string | null;
  convertFactor?: string | number | null;
  brand?: string | null;
  // V10 通用字段(可空):参考采购价/参考销售价/产地
  referencePurchasePrice?: string | number | null;
  referenceSalePrice?: string | number | null;
  origin?: string | null;
  creator?: string | null;
  createdAt?: string | null;
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
  // V10 行号/金额快照(服务端落,可空)
  lineNo?: number | null;
  amount?: string | number | null;
  taxAmount?: string | number | null;
  taxInclusiveTotal?: string | number | null;
  // V20 含税单价(服务端落库值,可空)
  taxPrice?: string | number | null;
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
  // V9 运输信息(可空)
  carrier?: string | null;
  vehicleNo?: string | null;
  freight?: string | null;
  // V10 单据总金额(服务端落)/单据类型/经办人(可空)
  totalAmount?: string | null;
  docType?: string | null;
  handler?: string | null;
}

export interface OutboundDocItem {
  id: number;
  itemId: number;
  quantity: string | number;
  batchId: number;
  locationId: number;
  serialNos?: string | null;
  unitPrice?: string | number | null;
  // V10 税率/行号/金额快照(可空)
  taxRate?: string | number | null;
  lineNo?: number | null;
  amount?: string | number | null;
  taxAmount?: string | number | null;
  taxInclusiveTotal?: string | number | null;
  // V20 含税单价(服务端落库值,可空)
  taxPrice?: string | number | null;
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
  // V9 运输信息(可空)
  carrier?: string | null;
  vehicleNo?: string | null;
  freight?: string | null;
  // V10 单据总金额(服务端落)/单据类型/经办人(可空)
  totalAmount?: string | null;
  docType?: string | null;
  handler?: string | null;
}

export interface PagedResponse<T> {
  rows: T[];
  total: number;
  page: number;
  pageSize: number;
}

// V13 期初库存(创建即过账,联动入库单 ref_type=opening)
export interface OpeningStockDocItem {
  id: number;
  docId: number;
  lineNo: number;
  itemId: number;
  itemCode?: string | null;
  itemName?: string | null;
  specSnapshot?: string | null;
  unit?: string | null;
  quantity: string | number;
  unitPrice?: string | number | null;
  batchNo?: string | null;
  productionDate?: string | null;
  expiryDate?: string | null;
  locationId?: number | null;
}

export interface OpeningStockDoc {
  id: number;
  docNo: string;
  docDate: string;
  warehouseId: number;
  totalQty: string | number;
  remark?: string | null;
  status: string;
  creator?: string | null;
  createdAt: string;
  warehouse?: Warehouse;
  items?: OpeningStockDocItem[];
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

// ===== 系统监控(仅 admin)=====
// 与后端 MonitorVO 契约一致:内存/磁盘为字节,CPU 为 0~1(获取失败为 null)

export interface MonitorMem {
  total: number;
  free: number;
  used: number;
}

export interface MonitorJvm {
  heapMax: number;
  heapUsed: number;
  heapFree: number;
  threadCount: number;
  startTime: number;
  uptimeSeconds: number;
  javaVersion: string;
}

export interface MonitorOs {
  name: string;
  arch: string;
  userName: string;
}

export interface MonitorDisk {
  mount: string;
  total: number;
  free: number;
  usable: number;
}

export interface MonitorOverview {
  systemCpuUsage: number | null;
  processCpuUsage: number | null;
  loadAverage: number;
  mem: MonitorMem;
  jvm: MonitorJvm;
  os: MonitorOs;
  gcCollectionCount: number;
  disks: MonitorDisk[];
}
