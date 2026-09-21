package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * 库龄/呆滞导出 Excel 行模型(列头中文)。
 *
 * <p>库龄区间用文字(0-30/31-90/91-180/>180/未知),呆滞用文字"呆滞",
 * 与页面朴素风格一致(不带颜色)。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class StockAgeingExportRow {

    /** 仓库名称。 */
    @ExcelProperty("仓库")
    private String warehouseName;

    /** 物品编码。 */
    @ExcelProperty("物品编码")
    private String itemCode;

    /** 物品名称。 */
    @ExcelProperty("物品名称")
    private String itemName;

    /** 批次号(无批次行为空)。 */
    @ExcelProperty("批次号")
    private String batchNo;

    /** 生产日期(yyyy-MM-dd,无生产时间为空)。 */
    @ExcelProperty("生产日期")
    private String productionDate;

    /** 库龄天数(无生产时间为空)。 */
    @ExcelProperty("库龄天数")
    private String ageDays;

    /** 库龄区间(文字,不带颜色)。 */
    @ExcelProperty("库龄区间")
    private String ageBucket;

    /** 当前量。 */
    @ExcelProperty("当前量")
    private String quantity;

    /** 呆滞标记(文字"呆滞",非呆滞为空)。 */
    @ExcelProperty("呆滞")
    private String stagnant;
}
