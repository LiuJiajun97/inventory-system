package com.company.inventory.common.constant;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志模块映射:Controller 类简名 → 模块中文名。
 *
 * <p>V16 操作日志落库时按 Controller 类名映射模块中文名;
 * 未映射的类名原样落库(如新增 Controller 忘记登记,日志不丢、模块名为英文类名)。</p>
 *
 * @author inventory
 */
public final class LogModule {

    /** Controller 类简名 → 模块中文名(顺序即展示顺序)。 */
    private static final Map<String, String> MODULES = new LinkedHashMap<>();

    static {
        // 基础数据组
        MODULES.put("ItemController", "物品");
        MODULES.put("WarehouseController", "仓库");
        MODULES.put("LocationController", "库位");
        MODULES.put("SupplierController", "供应商");
        MODULES.put("CustomerController", "客户");
        // 采购组
        MODULES.put("PurchaseOrderController", "采购订单");
        MODULES.put("InboundController", "入库单");
        MODULES.put("PurchaseReturnController", "采购退货单");
        // 销售组
        MODULES.put("SalesOrderController", "销售订单");
        MODULES.put("OutboundController", "出库单");
        MODULES.put("SalesReturnController", "销售退货单");
        // 库存组
        MODULES.put("StockController", "库存");
        MODULES.put("TransferController", "调拨单");
        MODULES.put("StocktakeController", "盘点单");
        MODULES.put("StockAdjustController", "调整单");
        MODULES.put("OpeningStockController", "期初库存");
        MODULES.put("AlertController", "预警");
        // 系统组
        MODULES.put("UserController", "用户");
        MODULES.put("RoleController", "角色");
        MODULES.put("MenuController", "菜单");
        MODULES.put("DictController", "字典");
        MODULES.put("DictTypeController", "字典类型");
        MODULES.put("MonitorController", "系统监控");
        MODULES.put("AuthController", "认证");
        MODULES.put("OperationLogController", "操作日志");
    }

    private LogModule() {
        // 常量类禁止实例化
    }

    /**
     * 按 Controller 类取模块中文名,未映射时返回类简名原样。
     *
     * @param controller Controller 类(如 ItemController.class)
     * @return 模块中文名(未映射为英文类简名)
     */
    public static String of(Class<?> controller) {
        if (controller == null) {
            return "未知";
        }
        return MODULES.getOrDefault(controller.getSimpleName(), controller.getSimpleName());
    }

    /**
     * 全部模块中文名(有序去重,供前端筛选下拉)。
     *
     * @return 模块中文名集合(按登记顺序)
     */
    public static Set<String> names() {
        return new LinkedHashSet<>(MODULES.values());
    }
}
