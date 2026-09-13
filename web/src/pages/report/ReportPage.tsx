// 报表中心(V15):单页 4 Tab——进销存月报/库龄呆滞/采购对账/销售对账
// 全部只读;菜单权限控制页面可见性,导出与列表读一致(不加新权限码)
// Tabs 用 antd(与预警中心一致;pro-components 2.8 无 ProTabs 导出)

import { Tabs } from "antd";
import { MonthlyReportTab } from "./MonthlyReportTab";
import { AgeingReportTab } from "./AgeingReportTab";
import { PurchaseReconTab } from "./PurchaseReconTab";
import { SalesReconTab } from "./SalesReconTab";

export function ReportPage() {
  return (
    <Tabs
      type="card"
      items={[
        { key: "monthly", label: "进销存月报", children: <MonthlyReportTab /> },
        { key: "ageing", label: "库龄/呆滞", children: <AgeingReportTab /> },
        { key: "purchase", label: "采购对账", children: <PurchaseReconTab /> },
        { key: "sales", label: "销售对账", children: <SalesReconTab /> },
      ]}
    />
  );
}
