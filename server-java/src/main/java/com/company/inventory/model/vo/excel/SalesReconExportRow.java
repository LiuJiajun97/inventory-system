package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * 销售对账导出 Excel 行模型(列头中文,客户维度)。
 *
 * <p>数量/金额统一 String 直出。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class SalesReconExportRow {

    /** 客户编码。 */
    @ExcelProperty("客户编码")
    private String customerCode;

    /** 客户名称。 */
    @ExcelProperty("客户名称")
    private String customerName;

    /** 销售单数。 */
    @ExcelProperty("销售单数")
    private String orderCount;

    /** 销售量。 */
    @ExcelProperty("销售量")
    private String orderQty;

    /** 销售金额(价税合计)。 */
    @ExcelProperty("销售金额(价税合计)")
    private String orderAmount;

    /** 退货量。 */
    @ExcelProperty("退货量")
    private String returnQty;

    /** 退货金额(价税合计)。 */
    @ExcelProperty("退货金额(价税合计)")
    private String returnAmount;

    /** 净销售量(销售-退货)。 */
    @ExcelProperty("净销售量")
    private String netQty;

    /** 净销售金额(销售-退货)。 */
    @ExcelProperty("净销售金额(价税合计)")
    private String netAmount;
}
