package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 入库单导出 Excel 行模型(列头中文,一行一单不展开行明细)。
 *
 * @author inventory
 */
@Getter
@Setter
public class InboundExportRow {

    /** 单据号。 */
    @ExcelProperty("单据号")
    private String docNo;

    /** 单据日期(yyyy-MM-dd)。 */
    @ExcelProperty("单据日期")
    private String docDate;

    /** 单据类型。 */
    @ExcelProperty("单据类型")
    private String docType;

    /** 单据状态(中文)。 */
    @ExcelProperty("状态")
    private String status;

    /** 关联单据号(采购订单号等)。 */
    @ExcelProperty("关联单号")
    private String refDocNo;

    /** 关联供应商名称。 */
    @ExcelProperty("供应商")
    private String supplierName;

    /** 仓库名称。 */
    @ExcelProperty("仓库")
    private String warehouseName;

    /** 承运商。 */
    @ExcelProperty("承运商")
    private String carrier;

    /** 运费。 */
    @ExcelProperty("运费")
    private String freight;

    /** 单据总金额。 */
    @ExcelProperty("总金额")
    private String totalAmount;

    /** 备注。 */
    @ExcelProperty("备注")
    private String remark;
}
