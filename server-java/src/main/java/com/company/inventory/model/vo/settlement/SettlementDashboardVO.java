package com.company.inventory.model.vo.settlement;

import java.math.BigDecimal;

/**
 * 结算仪表盘 VO(应付余额/应收余额两卡)。
 *
 * @param apBalance 应付余额=确认采购票净额 - 确认付款额
 * @param arBalance 应收余额=确认销售票净额 - 确认收款额
 * @author inventory
 */
public record SettlementDashboardVO(BigDecimal apBalance, BigDecimal arBalance) {
}
