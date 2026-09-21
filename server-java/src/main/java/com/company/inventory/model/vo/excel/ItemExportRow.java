package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 物品导出 Excel 行模型(列头中文)。
 *
 * <p>数量/金额/日期统一 String 直出,避免单元格格式歧义;
 * 税率列即百分数(13 表示 13%)。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class ItemExportRow {

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

    /** 分类(字典值 raw/finished/hardware)。 */
    @ExcelProperty("分类")
    private String category;

    /** 条码。 */
    @ExcelProperty("条码")
    private String barcode;

    /** 辅助单位。 */
    @ExcelProperty("辅助单位")
    private String secondUnit;

    /** 换算率。 */
    @ExcelProperty("换算率")
    private String convertFactor;

    /** 品牌。 */
    @ExcelProperty("品牌")
    private String brand;

    /** 产地。 */
    @ExcelProperty("产地")
    private String origin;

    /** 最低库存。 */
    @ExcelProperty("最低库存")
    private String minStock;

    /** 默认税率(百分数)。 */
    @ExcelProperty("默认税率(%)")
    private String defaultTaxRate;

    /** 参考采购价。 */
    @ExcelProperty("参考采购价")
    private String referencePurchasePrice;

    /** 参考销售价。 */
    @ExcelProperty("参考销售价")
    private String referenceSalePrice;

    /** 创建时间(yyyy-MM-dd HH:mm:ss)。 */
    @ExcelProperty("创建时间")
    private String createdAt;
}
