package com.company.inventory.common.constant;






/**
 * 业务错误码与 HTTP 状态常量(阿里规范:禁止魔法值)。
 *
 * <p>错误码与 Fastify 版保持同名,便于契约对齐。</p>
 *
 * @author inventory
 */
public final class ErrorCode {

    /** 通用业务错误。 */
    public static final String BIZ_ERROR = "BIZ_ERROR";

    /** 未登录。 */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    /** 无权限。 */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** 资源不存在。 */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** 参数校验失败。 */
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    /** 服务器内部错误。 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /** 原密码错误。 */
    public static final String OLD_PASSWORD_WRONG = "OLD_PASSWORD_WRONG";

    /** 角色非法。 */
    public static final String BAD_ROLE = "BAD_ROLE";

    /** 用户名已存在。 */
    public static final String USERNAME_DUP = "USERNAME_DUP";

    /** 物品编码已存在。 */
    public static final String ITEM_CODE_DUP = "ITEM_CODE_DUP";

    /** 物品条码已存在。 */
    public static final String ITEM_BARCODE_DUP = "ITEM_BARCODE_DUP";

    /** 仓库编码已存在。 */
    public static final String WAREHOUSE_CODE_DUP = "WAREHOUSE_CODE_DUP";

    /** 库位编码在该仓库内已存在。 */
    public static final String LOCATION_CODE_DUP = "LOCATION_CODE_DUP";

    /** 供应商编码已存在。 */
    public static final String SUPPLIER_CODE_DUP = "SUPPLIER_CODE_DUP";

    /** 客户编码已存在。 */
    public static final String CUSTOMER_CODE_DUP = "CUSTOMER_CODE_DUP";

    /** 角色编码已存在。 */
    public static final String ROLE_CODE_DUP = "ROLE_CODE_DUP";

    /** 菜单编码已存在。 */
    public static final String MENU_CODE_DUP = "MENU_CODE_DUP";

    /** 内置角色受保护(禁删/禁改编码)。 */
    public static final String ROLE_BUILTIN_PROTECTED = "ROLE_BUILTIN_PROTECTED";

    /** 角色存在绑定用户,禁删。 */
    public static final String ROLE_HAS_USERS = "ROLE_HAS_USERS";

    /** 菜单存在子节点,禁删。 */
    public static final String MENU_HAS_CHILDREN = "MENU_HAS_CHILDREN";

    // ===== 业务类型(bizCode) =====

    /** 入库流水。 */
    public static final String BIZ_CODE_INBOUND = "inbound";

    /** 出库流水。 */
    public static final String BIZ_CODE_OUTBOUND = "outbound";

    /** 调拨出库流水(源仓)。 */
    public static final String BIZ_CODE_TRANSFER_OUT = "transfer_out";

    /** 调拨入库流水(目的仓)。 */
    public static final String BIZ_CODE_TRANSFER_IN = "transfer_in";

    /** 调整入库流水(盘盈/报损撤销等增加)。 */
    public static final String BIZ_CODE_ADJUST_IN = "adjust_in";

    /** 调整出库流水(盘亏/报损减少)。 */
    public static final String BIZ_CODE_ADJUST_OUT = "adjust_out";

    /** 流水业务类型:销售预占(销售订单审批时冻结可用量)。 */
    public static final String BIZ_CODE_PRE_ALLOC = "pre_alloc";

    /** 流水业务类型:释放预占(销售订单关闭/作废/发货后解冻)。 */
    public static final String BIZ_CODE_RELEASE_PRE_ALLOC = "release_pre_alloc";

    // ===== 序列号状态 =====

    /** 序列号:在库。 */
    public static final String SERIAL_STATUS_IN_STOCK = "in_stock";

    /** 序列号:已出库。 */
    public static final String SERIAL_STATUS_OUT = "out";

    // ===== 批次状态 =====

    /** 批次:启用。 */
    public static final String BATCH_STATUS_ACTIVE = "active";

    // ===== 单据状态 =====

    /** 单据:已完成。 */
    public static final String DOC_STATUS_FINISHED = "finished";

    // ===== 方向 =====

    /** 入库方向。 */
    public static final String DIRECTION_INBOUND = "inbound";

    /** 出库方向。 */
    public static final String DIRECTION_OUTBOUND = "outbound";

    // ===== 单据关联类型(refType) =====

    /** 关联类型:采购到货(入库单 → 采购订单)。 */
    public static final String REF_TYPE_PURCHASE = "purchase";

    /** 关联类型:销售发货(出库单 → 销售订单)。 */
    public static final String REF_TYPE_SALES = "sales";

    /** 关联类型:采购退货(出库单 → 采购退货单)。 */
    public static final String REF_TYPE_PURCHASE_RETURN = "purchase_return";

    /** 关联类型:销售退货(入库单 → 销售退货单)。 */
    public static final String REF_TYPE_SALES_RETURN = "sales_return";

    /** 关联类型:期初入库(入库单 → 期初单)。 */
    public static final String REF_TYPE_OPENING = "opening";

    // ===== 调整类型(adjustType) =====

    /** 调整:盘盈。 */
    public static final String ADJUST_TYPE_GAIN = "gain";

    /** 调整:盘亏。 */
    public static final String ADJUST_TYPE_LOSS = "loss";

    /** 调整:报损。 */
    public static final String ADJUST_TYPE_SCRAP = "scrap";

    // ===== 盘点范围(scopeType) =====

    /** 盘点范围:全仓。 */
    public static final String SCOPE_ALL = "all";

    /** 盘点范围:指定物品。 */
    public static final String SCOPE_ITEM = "item";

    // ===== HTTP 状态 =====

    /** HTTP 400。 */
    public static final int HTTP_BAD_REQUEST = 400;

    /** HTTP 401。 */
    public static final int HTTP_UNAUTHORIZED = 401;

    /** HTTP 403。 */
    public static final int HTTP_FORBIDDEN = 403;

    /** HTTP 404。 */
    public static final int HTTP_NOT_FOUND = 404;

    /** HTTP 405。 */
    public static final int HTTP_METHOD_NOT_ALLOWED = 405;

    /** HTTP 429(限流/重复请求)。 */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** HTTP 500。 */
    public static final int HTTP_INTERNAL_ERROR = 500;

    // ===== 分页 =====

    /** 分页 pageSize 上限(与 MyBatis-Plus 分页插件 maxLimit 一致)。 */
    public static final long PAGE_SIZE_MAX = 200L;

    private ErrorCode() {
    }
}
