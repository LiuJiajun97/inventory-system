// 应用入口与路由
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ConfigProvider, App as AntdApp, Result } from "antd";
import zhCN from "antd/locale/zh_CN";
import { themeConfig } from "./theme";
import { LoginPage } from "./pages/login/LoginPage";
import { MainLayout } from "./layout/MainLayout";
import { DashboardPage } from "./pages/dashboard/DashboardPage";
import { InboundListPage } from "./pages/inbound/InboundListPage";
import { InboundFormPage } from "./pages/inbound/InboundFormPage";
import { OutboundListPage } from "./pages/outbound/OutboundListPage";
import { OutboundFormPage } from "./pages/outbound/OutboundFormPage";
import { StockQueryPage } from "./pages/stock/StockQueryPage";
import { TransactionQueryPage } from "./pages/transaction/TransactionQueryPage";
import { OpeningStockListPage } from "./pages/opening/OpeningStockListPage";
import { OpeningStockNewPage } from "./pages/opening/OpeningStockNewPage";
import { ItemListPage } from "./pages/item/ItemListPage";
import { ItemFormPage } from "./pages/item/ItemFormPage";
import { WarehouseListPage } from "./pages/warehouse/WarehouseListPage";
import { LocationListPage } from "./pages/location/LocationListPage";
import { UserListPage } from "./pages/user/UserListPage";
import { RoleManagePage } from "./pages/role/RoleManagePage";
import { MenuManagePage } from "./pages/menu/MenuManagePage";
import { SupplierPage } from "./pages/supplier/SupplierPage";
import { CustomerPage } from "./pages/customer/CustomerPage";
import { PurchaseOrderListPage } from "./pages/purchase/PurchaseOrderListPage";
import { PurchaseOrderNewPage } from "./pages/purchase/PurchaseOrderNewPage";
import { PurchaseReturnListPage } from "./pages/purchaseReturn/PurchaseReturnListPage";
import { PurchaseReturnNewPage } from "./pages/purchaseReturn/PurchaseReturnNewPage";
import { SalesOrderListPage } from "./pages/sales/SalesOrderListPage";
import { SalesOrderNewPage } from "./pages/sales/SalesOrderNewPage";
import { SalesReturnListPage } from "./pages/salesReturn/SalesReturnListPage";
import { SalesReturnNewPage } from "./pages/salesReturn/SalesReturnNewPage";
import { TransferPage } from "./pages/transfer/TransferPage";
import { StocktakePage } from "./pages/stocktake/StocktakePage";
import { AdjustPage } from "./pages/adjust/AdjustPage";
import { AlertPage } from "./pages/alert/AlertPage";
import { ReportPage } from "./pages/report/ReportPage";
import { DictPage } from "./pages/dict/DictPage";
import { MonitorPage } from "./pages/monitor/MonitorPage";
import { RequireAuth } from "./auth/RequireAuth";
import { MenuProvider } from "./auth/MenuContext";

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
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route path="dashboard" element={<DashboardPage />} />
              <Route path="inbound" element={<InboundListPage />} />
              <Route
                path="inbound/new"
                element={
                  <RequireAuth>
                    <InboundFormPage />
                  </RequireAuth>
                }
              />
              <Route path="outbound" element={<OutboundListPage />} />
              <Route
                path="outbound/new"
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
                path="opening/new"
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
                path="purchase-returns/new"
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
                path="sales-returns/new"
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