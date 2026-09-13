package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存导出 Excel 行模型(列头中文)。
 *
 * @author inventory
 */
@Getter
@Setter
public class StockExportRow {

    /** 物品编码。 */
    @ExcelProperty("物品编码")
    private String itemCode;

    /** 物品名称。 */
    @ExcelProperty("物品名称")
    private String itemName;

    /** 规格。 */
    @ExcelProperty("规格")
    private String spec;

    /** 单位。 */
    @ExcelProperty("单位")
    private String unit;

    /** 仓库名称。 */
    @ExcelProperty("仓库")
    private String warehouseName;

    /** 批次号。 */
    @ExcelProperty("批次")
    private String batchNo;

    /** 库位编码。 */
    @ExcelProperty("库位")
    private String locationCode;

    /** 库存数量。 */
    @ExcelProperty("数量")
    private String quantity;

    /** 预占数量。 */
    @ExcelProperty("预占")
    private String preAllocatedQty;

    /** 可用数量(数量-预占)。 */
    @ExcelProperty("可用")
    private String availableQty;
}
