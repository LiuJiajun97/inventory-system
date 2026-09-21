// 仓库范围快捷切换上下文(TASK-v22c C2/C3)
// 顶栏提供「仓库」Select:选中某仓后,所有支持 warehouseId 筛选的列表页
// 在构建请求参数时自动并入该范围(页面筛选表单值优先,表单为空时本范围生效);
// 清空 Select = 恢复全部仓库。
// 仓库列表在 Provider 首次挂载时拉一次 GET /warehouses(后端数据权限已过滤,
// 非 admin 返回的就是其授权仓),顶栏「授权仓 n 个」徽标与 Select 选项共用
// 同一份 state,不重复请求。

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { warehouseApi } from "../api";
import type { Warehouse } from "../types";

interface WarehouseScopeValue {
  /** 仓库列表(已按数据权限过滤:admin 见全部,非 admin 仅见授权仓) */
  warehouses: Warehouse[];
  /** 顶栏当前选中的仓库范围(null = 全部仓库) */
  scopeWarehouseId: number | null;
  /** 设置顶栏仓库范围(null 表示清空,恢复全部) */
  setScopeWarehouseId: (id: number | null) => void;
}

const WarehouseScopeContext = createContext<WarehouseScopeValue>({
  warehouses: [],
  scopeWarehouseId: null,
  setScopeWarehouseId: () => undefined,
});

/** 仓库范围 Provider:挂在 MainLayout 内,登录态才有效。 */
export function WarehouseScopeProvider({ children }: { children: ReactNode }) {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [scopeWarehouseId, setScopeWarehouseId] = useState<number | null>(null);

  // 首次挂载拉一次;失败静默,回退"全部仓库"(列表页各自筛选不受影响)
  useEffect(() => {
    warehouseApi
      .list({ page: 1, pageSize: 200 })
      .then((r) => setWarehouses(r.rows))
      .catch(() => undefined);
  }, []);

  return (
    <WarehouseScopeContext.Provider
      value={{ warehouses, scopeWarehouseId, setScopeWarehouseId }}
    >
      {children}
    </WarehouseScopeContext.Provider>
  );
}

/** 取顶栏当前仓库范围(列表页请求合并用;null 表示不限仓库)。 */
export const useScopeWarehouseId = (): number | null =>
  useContext(WarehouseScopeContext).scopeWarehouseId;

/** 取完整上下文(顶栏 Select 选项 / 授权仓数量徽标用)。 */
export const useWarehouseScope = (): WarehouseScopeValue =>
  useContext(WarehouseScopeContext);
