package com.company.inventory.vo.dashboard;
import java.math.BigDecimal;

/**
 * 总览统计出参(契约:全部为 JSON number)。
 *
 * @param warehouseCount     仓库数
 * @param itemCount          物品数
 * @param stockLineCount     库存行数
 * @param todayInboundCount  今日入库单数
 * @param todayInboundQty    今日入库数量
 * @param todayOutboundCount 今日出库单数
 * @param todayOutboundQty   今日出库数量
 * @author inventory
 */
public record DashboardVO(long warehouseCount, long itemCount, long stockLineCount,
        long todayInboundCount, BigDecimal todayInboundQty,
        long todayOutboundCount, BigDecimal todayOutboundQty) {
}
