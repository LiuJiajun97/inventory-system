package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存成本(移动均价)导出 Excel 行模型(列头中文)。
 *
 * <p>均价 4 位小数(与页面一致,2 位会失真),金额 2 位小数;无批次行批次号为空。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class CostReportExportRow {

    /** 仓库名称。 */
    @ExcelProperty("仓库")
    private String warehouseName;

    /** 物品编码。 */
    @ExcelProperty("物品编码")
    private String itemCode;

    /** 物品名称。 */
    @ExcelProperty("物品名称")
    private String itemName;

    /** 单位。 */
    @ExcelProperty("单位")
    private String unit;

    /** 批次号(无批次行为空)。 */
    @ExcelProperty("批次号")
    private String batchNo;

    /** 数量。 */
    @ExcelProperty("数量")
    private String quantity;

    /** 移动均价(4 位小数)。 */
    @ExcelProperty("移动均价")
    private String avgPrice;

    /** 成本金额(2 位小数)。 */
    @ExcelProperty("成本金额")
    private String amount;
}
