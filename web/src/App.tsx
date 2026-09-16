// 应用入口与路由
// 路由懒加载(TASK-v22c C1):登录页/主布局静态导入,其余页面组件 React.lazy 按需分包;
// 项目均为具名导出,故 lazy 需 .then 包装成 default 导出;
// 懒加载 chunk 下载期间展示整页骨架屏 <Suspense fallback={<PageSkeleton/>}>。
import { Suspense, lazy } from "react";
import type { ComponentType } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ConfigProvider, App as AntdApp, Result } from "antd";
import zhCN from "antd/locale/zh_CN";
import { themeConfig } from "./theme";
import { LoginPage } from "./pages/login/LoginPage";
import { MainLayout } from "./layout/MainLayout";
import { PageSkeleton } from "./components/PageSkeleton";
import { RequireAuth } from "./auth/RequireAuth";
import { MenuProvider } from "./auth/MenuContext";

// 懒加载页面包装:每个懒组件自带 Suspense,chunk 下载期间展示整页骨架屏。
// 注意:react-router v6 要求 <Route> 的直接子元素必须全是 <Route>,
// 不能把 <Suspense> 挂进路由树(Suspense 必须放 element 内部)。
// 透传全部 props:同一页面组件会以不同 key/mode 复用多次(如 LedgerPage mode=ap/ar)。
// props 用 any:懒加载包装需对任意 props 签名组件透传,any 是 React lazy 包装的标准写法。
function withSuspense(loader: () => Promise<{ default: any }>) {
  const Lazy = lazy(loader);
  return function LazyPage(props: any) {
    const P = Lazy as unknown as ComponentType<any>;
    return (
      <Suspense fallback={<PageSkeleton />}>
        <P {...props} />
      </Suspense>
    );
  };
}

// 以下页面组件全部懒加载(具名导出,需 then 包装)
const DashboardPage = withSuspense(() =>
  import("./pages/dashboard/DashboardPage").then((m) => ({ default: m.DashboardPage })),
);
const InboundListPage = withSuspense(() =>
  import("./pages/inbound/InboundListPage").then((m) => ({ default: m.InboundListPage })),
);
const InboundFormPage = withSuspense(() =>
  import("./pages/inbound/InboundFormPage").then((m) => ({ default: m.InboundFormPage })),
);
const OutboundListPage = withSuspense(() =>
  import("./pages/outbound/OutboundListPage").then((m) => ({ default: m.OutboundListPage })),
);
const OutboundFormPage = withSuspense(() =>
  import("./pages/outbound/OutboundFormPage").then((m) => ({ default: m.OutboundFormPage })),
);
const StockQueryPage = withSuspense(() =>
  import("./pages/stock/StockQueryPage").then((m) => ({ default: m.StockQueryPage })),
);
const TransactionQueryPage = withSuspense(() =>
  import("./pages/transaction/TransactionQueryPage").then((m) => ({
    default: m.TransactionQueryPage,
  })),
);
const OpeningStockListPage = withSuspense(() =>
  import("./pages/opening/OpeningStockListPage").then((m) => ({
    default: m.OpeningStockListPage,
  })),
);
const OpeningStockNewPage = withSuspense(() =>
  import("./pages/opening/OpeningStockNewPage").then((m) => ({
    default: m.OpeningStockNewPage,
  })),
);
const ItemListPage = withSuspense(() =>
  import("./pages/item/ItemListPage").then((m) => ({ default: m.ItemListPage })),
);
const ItemFormPage = withSuspense(() =>
  import("./pages/item/ItemFormPage").then((m) => ({ default: m.ItemFormPage })),
);
const WarehouseListPage = withSuspense(() =>
  import("./pages/warehouse/WarehouseListPage").then((m) => ({
    default: m.WarehouseListPage,
  })),
);
const LocationListPage = withSuspense(() =>
  import("./pages/location/LocationListPage").then((m) => ({
    default: m.LocationListPage,
  })),
);
const UserListPage = withSuspense(() =>
  import("./pages/user/UserListPage").then((m) => ({ default: m.UserListPage })),
);
const RoleManagePage = withSuspense(() =>
  import("./pages/role/RoleManagePage").then((m) => ({ default: m.RoleManagePage })),
);
const MenuManagePage = withSuspense(() =>
  import("./pages/menu/MenuManagePage").then((m) => ({ default: m.MenuManagePage })),
);
const SupplierPage = withSuspense(() =>
  import("./pages/supplier/SupplierPage").then((m) => ({ default: m.SupplierPage })),
);
const CustomerPage = withSuspense(() =>
  import("./pages/customer/CustomerPage").then((m) => ({ default: m.CustomerPage })),
);
const PurchaseOrderListPage = withSuspense(() =>
  import("./pages/purchase/PurchaseOrderListPage").then((m) => ({
    default: m.PurchaseOrderListPage,
  })),
);
const PurchaseOrderNewPage = withSuspense(() =>
  import("./pages/purchase/PurchaseOrderNewPage").then((m) => ({
    default: m.PurchaseOrderNewPage,
  })),
);
const PurchaseReturnListPage = withSuspense(() =>
  import("./pages/purchaseReturn/PurchaseReturnListPage").then((m) => ({
    default: m.PurchaseReturnListPage,
  })),
);
const PurchaseReturnNewPage = withSuspense(() =>
  import("./pages/purchaseReturn/PurchaseReturnNewPage").then((m) => ({
    default: m.PurchaseReturnNewPage,
  })),
);
const SalesOrderListPage = withSuspense(() =>
  import("./pages/sales/SalesOrderListPage").then((m) => ({
    default: m.SalesOrderListPage,
  })),
);
const SalesOrderNewPage = withSuspense(() =>
  import("./pages/sales/SalesOrderNewPage").then((m) => ({
    default: m.SalesOrderNewPage,
  })),
);
const SalesReturnListPage = withSuspense(() =>
  import("./pages/salesReturn/SalesReturnListPage").then((m) => ({
    default: m.SalesReturnListPage,
  })),
);
const SalesReturnNewPage = withSuspense(() =>
  import("./pages/salesReturn/SalesReturnNewPage").then((m) => ({
    default: m.SalesReturnNewPage,
  })),
);
const TransferPage = withSuspense(() =>
  import("./pages/transfer/TransferPage").then((m) => ({ default: m.TransferPage })),
);
const StocktakePage = withSuspense(() =>
  import("./pages/stocktake/StocktakePage").then((m) => ({ default: m.StocktakePage })),
);
const AdjustPage = withSuspense(() =>
  import("./pages/adjust/AdjustPage").then((m) => ({ default: m.AdjustPage })),
);
const AlertPage = withSuspense(() =>
  import("./pages/alert/AlertPage").then((m) => ({ default: m.AlertPage })),
);
const ReportPage = withSuspense(() =>
  import("./pages/report/ReportPage").then((m) => ({ default: m.ReportPage })),
);
const InvoiceListPage = withSuspense(() =>
  import("./pages/settlement/InvoiceListPage").then((m) => ({
    default: m.InvoiceListPage,
  })),
);
const InvoiceNewPage = withSuspense(() =>
  import("./pages/settlement/InvoiceNewPage").then((m) => ({
    default: m.InvoiceNewPage,
  })),
);
const LedgerPage = withSuspense(() =>
  import("./pages/settlement/LedgerPage").then((m) => ({ default: m.LedgerPage })),
);
const PaymentListPage = withSuspense(() =>
  import("./pages/settlement/PaymentListPage").then((m) => ({
    default: m.PaymentListPage,
  })),
);
const PaymentNewPage = withSuspense(() =>
  import("./pages/settlement/PaymentNewPage").then((m) => ({
    default: m.PaymentNewPage,
  })),
);
const DictPage = withSuspense(() =>
  import("./pages/dict/DictPage").then((m) => ({ default: m.DictPage })),
);
const MonitorPage = withSuspense(() =>
  import("./pages/monitor/MonitorPage").then((m) => ({ default: m.MonitorPage })),
);
const OperationLogPage = withSuspense(() =>
  import("./pages/operationLog/OperationLogPage").then((m) => ({
    default: m.OperationLogPage,
  })),
);

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <AntdApp>
        {/* 全局菜单/权限上下文:登录拉 /auth/menus,守卫与按钮显隐共用 */}
        <MenuProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/"
              element={
                <RequireAuth>
                  <MainLayout />
                </RequireAuth>
              }
            >
              {/* 登录态内路由统一包 Suspense:懒加载 chunk 下载期间展示整页骨架屏 */}
              <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="dashboard" element={<DashboardPage />} />
                <Route path="inbound" element={<InboundListPage />} />
                <Route
                  path="inbound/new/:id?"
                  element={
                    <RequireAuth>
                      <InboundFormPage />
                    </RequireAuth>
                  }
                />
                <Route path="outbound" element={<OutboundListPage />} />
                <Route
                  path="outbound/new/:id?"
                  element={
                    <RequireAuth>
                      <OutboundFormPage />
                    </RequireAuth>
                  }
                />
                <Route path="stock" element={<StockQueryPage />} />
                <Route path="transactions" element={<TransactionQueryPage />} />
                <Route path="opening" element={<OpeningStockListPage />} />
                <Route
                  path="opening/new/:id?"
                  element={
                    <RequireAuth>
                      <OpeningStockNewPage />
                    </RequireAuth>
                  }
                />
                <Route path="suppliers" element={<SupplierPage />} />
                <Route path="customers" element={<CustomerPage />} />
                <Route path="purchase-orders" element={<PurchaseOrderListPage />} />
                <Route
                  path="purchase-orders/new/:id?"
                  element={
                    <RequireAuth>
                      <PurchaseOrderNewPage />
                    </RequireAuth>
                  }
                />
                <Route path="purchase-returns" element={<PurchaseReturnListPage />} />
                <Route
                  path="purchase-returns/new/:id?"
                  element={
                    <RequireAuth>
                      <PurchaseReturnNewPage />
                    </RequireAuth>
                  }
                />
                <Route path="sales-orders" element={<SalesOrderListPage />} />
                <Route
                  path="sales-orders/new/:id?"
                  element={
                    <RequireAuth>
                      <SalesOrderNewPage />
                    </RequireAuth>
                  }
                />
                <Route path="sales-returns" element={<SalesReturnListPage />} />
                <Route
                  path="sales-returns/new/:id?"
                  element={
                    <RequireAuth>
                      <SalesReturnNewPage />
                    </RequireAuth>
                  }
                />
                <Route path="transfers" element={<TransferPage />} />
                <Route path="stocktakes" element={<StocktakePage />} />
                <Route path="stock-adjusts" element={<AdjustPage />} />
                <Route path="alerts" element={<AlertPage />} />
                <Route path="reports" element={<ReportPage />} />
                <Route path="invoices" element={<InvoiceListPage />} />
                <Route
                  path="invoices/new/:id?"
                  element={
                    <RequireAuth>
                      <InvoiceNewPage />
                    </RequireAuth>
                  }
                />
                <Route path="settlement/ap" element={<LedgerPage key="ap" mode="ap" />} />
                <Route path="settlement/ar" element={<LedgerPage key="ar" mode="ar" />} />
                <Route path="payments" element={<PaymentListPage key="payment" mode="payment" />} />
                <Route
                  path="payments/new/:id?"
                  element={
                    <RequireAuth>
                      <PaymentNewPage key="payment-new" mode="payment" />
                    </RequireAuth>
                  }
                />
                <Route path="receipts" element={<PaymentListPage key="receipt" mode="receipt" />} />
                <Route
                  path="receipts/new/:id?"
                  element={
                    <RequireAuth>
                      <PaymentNewPage key="receipt-new" mode="receipt" />
                    </RequireAuth>
                  }
                />
                <Route path="items" element={<ItemListPage />} />
                <Route
                  path="items/new"
                  element={
                    <RequireAuth>
                      <ItemFormPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="warehouses"
                  element={
                    <RequireAuth>
                      <WarehouseListPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="locations"
                  element={
                    <RequireAuth>
                      <LocationListPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="users"
                  element={
                    <RequireAuth>
                      <UserListPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="roles"
                  element={
                    <RequireAuth>
                      <RoleManagePage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="menus"
                  element={
                    <RequireAuth>
                      <MenuManagePage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="dicts"
                  element={
                    <RequireAuth>
                      <DictPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="monitor"
                  element={
                    <RequireAuth>
                      <MonitorPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="operation-logs"
                  element={
                    <RequireAuth>
                      <OperationLogPage />
                    </RequireAuth>
                  }
                />
                <Route
                  path="403"
                  element={
                    <Result
                      status="403"
                      title="403"
                      subTitle="无权限访问此页面"
                    />
                  }
                />
            </Route>
            <Route
              path="*"
              element={<Result status="404" title="404" subTitle="页面不存在" />}
            />
          </Routes>
        </BrowserRouter>
        </MenuProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
