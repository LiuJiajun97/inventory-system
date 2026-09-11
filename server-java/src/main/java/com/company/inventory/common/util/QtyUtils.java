package com.company.inventory.common.util;






import java.math.BigDecimal;

/**
 * 数量序列化工具。
 *
 * <p>契约约定:库存/流水/单据行 JSON 中 quantity/changeQty/afterQty 必须是字符串
 * (Prisma Decimal 序列化为字符串),格式与 Prisma Decimal 一致:不带尾随零
 * (100.0000 → "100",950.5000 → "950.5",-50.0000 → "-50")。</p>
 *
 * @author inventory
 */
public final class QtyUtils {

    /**
     * 私有构造,工具类禁止实例化。
     */
    private QtyUtils() {
    }

    /**
     * BigDecimal 转契约字符串(去掉尾随零,零值返回 "0")。
     *
     * @param value 数量
     * @return 契约字符串
     */
    public static String toContractString(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * BigDecimal 转契约数值(用于总览页等 JSON number 字段,去掉尾随零)。
     *
     * @param value 数量
     * @return 规整后的 BigDecimal
     */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.stripTrailingZeros();
    }
}
