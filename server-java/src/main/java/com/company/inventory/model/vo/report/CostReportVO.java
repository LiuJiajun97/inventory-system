package com.company.inventory.model.vo.report;

import java.util.List;

/**
 * 库存成本(移动均价)报表出参:全量行 + 合计行(合计仅对当前可见行)。
 *
 * <p>不分页:成本单元数量对单机自用规模远小于常规分页页大小,一次取完。</p>
 *
 * @param rows          报表行(含数量为 0 的历史单元)
 * @param totalQuantity 合计数量(字符串,合同格式)
 * @param totalAmount   合计成本金额(字符串,2 位小数)
 * @author inventory
 */
public record CostReportVO(List<CostReportRow> rows, String totalQuantity, String totalAmount) {
}
