package com.company.inventory.model.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 物品导入 Excel 行模型(列头中文,与模板下载一致)。
 *
 * <p>字段全为 String,由导入服务负责必填/数值/字典映射校验;
 * 带 * 的列头为必填列。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class ItemImportRow {

    /** 物品编码(必填,唯一)。 */
    @ExcelProperty("物品编码*")
    private String itemCode;

    /** 物品名称(必填)。 */
    @ExcelProperty("物品名称*")
    private String itemName;

    /** 单位(必填)。 */
    @ExcelProperty("单位*")
    private String unit;

    /** 规格(可选)。 */
    @ExcelProperty("规格")
    private String spec;

    /** 分类(可选:原材料/原料→raw,成品→finished,五金→hardware)。 */
    @ExcelProperty("分类")
    private String category;

    /** 条码(可选,非空须唯一)。 */
    @ExcelProperty("条码")
    private String barcode;

    /** 辅助单位(可选)。 */
    @ExcelProperty("辅助单位")
    private String secondUnit;

    /** 换算率(可选,1 辅助单位对应的基本单位数)。 */
    @ExcelProperty("换算率")
    private String convertFactor;

    /** 品牌(可选)。 */
    @ExcelProperty("品牌")
    private String brand;

    /** 产地(可选)。 */
    @ExcelProperty("产地")
    private String origin;

    /** 最低库存(可选,预警阈值)。 */
    @ExcelProperty("最低库存")
    private String minStock;

    /** 默认税率(可选,百分数,如 13 表示 13%,留空默认 13)。 */
    @ExcelProperty("默认税率")
    private String defaultTaxRate;
}
