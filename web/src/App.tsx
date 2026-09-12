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
import { ItemListPage } from "./pages/item/ItemListPage";
import { ItemFormPage } from "./pages/item/ItemFormPage";
import { WarehouseListPage } from "./pages/warehouse/WarehouseListPage";
import { LocationListPage } from "./pages/location/LocationListPage";
import { UserListPage } from "./pages/user/UserListPage";
import { SupplierPage } from "./pages/supplier/SupplierPage";
import { CustomerPage } from "./pages/customer/CustomerPage";
import { PurchaseOrderListPage } from "./pages/purchase/PurchaseOrderListPage";
import { PurchaseOrderNewPage } from "./pages/purchase/PurchaseOrderNewPage";
import { SalesOrderListPage } from "./pages/sales/SalesOrderListPage";
import { SalesOrderNewPage } from "./pages/sales/SalesOrderNewPage";
import { TransferPage } from "./pages/transfer/TransferPage";
import { StocktakePage } from "./pages/stocktake/StocktakePage";
import { AdjustPage } from "./pages/adjust/AdjustPage";
import { AlertPage } from "./pages/alert/AlertPage";
import { DictPage } from "./pages/dict/DictPage";
import { MonitorPage } from "./pages/monitor/MonitorPage";
import { RequireAuth } from "./auth/RequireAuth";

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <AntdApp>
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
                  <RequireAuth roles={["admin", "operator"]}>
                    <InboundFormPage />
                  </RequireAuth>
                }
              />
              <Route path="outbound" element={<OutboundListPage />} />
              <Route
                path="outbound/new"
                element={
                  <RequireAuth roles={["admin", "operator"]}>
                    <OutboundFormPage />
                  </RequireAuth>
                }
              />
              <Route path="stock" element={<StockQueryPage />} />
              <Route path="transactions" element={<TransactionQueryPage />} />
              <Route path="suppliers" element={<SupplierPage />} />
              <Route path="customers" element={<CustomerPage />} />
              <Route path="purchase-orders" element={<PurchaseOrderListPage />} />
              <Route
                path="purchase-orders/new/:id?"
                element={
                  <RequireAuth roles={["admin", "operator"]}>
                    <PurchaseOrderNewPage />
                  </RequireAuth>
                }
              />
              <Route path="sales-orders" element={<SalesOrderListPage />} />
              <Route
                path="sales-orders/new/:id?"
                element={
                  <RequireAuth roles={["admin", "operator"]}>
                    <SalesOrderNewPage />
                  </RequireAuth>
                }
              />
              <Route path="transfers" element={<TransferPage />} />
              <Route path="stocktakes" element={<StocktakePage />} />
              <Route path="stock-adjusts" element={<AdjustPage />} />
              <Route path="alerts" element={<AlertPage />} />
              <Route path="items" element={<ItemListPage />} />
              <Route
                path="items/new"
                element={
                  <RequireAuth roles={["admin"]}>
                    <ItemFormPage />
                  </RequireAuth>
                }
              />
              <Route
                path="warehouses"
                element={
                  <RequireAuth roles={["admin"]}>
                    <WarehouseListPage />
                  </RequireAuth>
                }
              />
              <Route
                path="locations"
                element={
                  <RequireAuth roles={["admin"]}>
                    <LocationListPage />
                  </RequireAuth>
                }
              />
              <Route
                path="users"
                element={
                  <RequireAuth roles={["admin"]}>
                    <UserListPage />
                  </RequireAuth>
                }
              />
              <Route
                path="dicts"
                element={
                  <RequireAuth roles={["admin"]}>
                    <DictPage />
                  </RequireAuth>
                }
              />
              <Route
                path="monitor"
                element={
                  <RequireAuth roles={["admin"]}>
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
      </AntdApp>
    </ConfigProvider>
  );
}