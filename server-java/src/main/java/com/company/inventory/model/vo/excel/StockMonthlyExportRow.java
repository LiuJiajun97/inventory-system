package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * 进销存月报导出 Excel 行模型(列头中文)。
 *
 * <p>数量/金额统一 String 直出(金额无快照为空串)。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class StockMonthlyExportRow {

    /** 物品编码。 */
    @ExcelProperty("物品编码")
    private String itemCode;

    /** 物品名称。 */
    @ExcelProperty("物品名称")
    private String itemName;

    /** 单位。 */
    @ExcelProperty("单位")
    private String unit;

    /** 规格。 */
    @ExcelProperty("规格")
    private String spec;

    /** 期初量。 */
    @ExcelProperty("期初量")
    private String openingQty;

    /** 本期入量。 */
    @ExcelProperty("本期入量")
    private String inQty;

    /** 本期出量。 */
    @ExcelProperty("本期出量")
    private String outQty;

    /** 期末量。 */
    @ExcelProperty("期末量")
    private String closingQty;

    /** 入库金额(含税,无快照为空)。 */
    @ExcelProperty("入库金额(含税)")
    private String inAmount;

    /** 出库金额(含税,无快照为空)。 */
    @ExcelProperty("出库金额(含税)")
    private String outAmount;
}
