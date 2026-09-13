package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 采购订单导出 Excel 行模型(列头中文,一行一单不展开行明细)。
 *
 * @author inventory
 */
@Getter
@Setter
public class PurchaseOrderExportRow {

    /** 单号。 */
    @ExcelProperty("单号")
    private String docNo;

    /** 单据日期(yyyy-MM-dd)。 */
    @ExcelProperty("单据日期")
    private String docDate;

    /** 状态(中文)。 */
    @ExcelProperty("状态")
    private String status;

    /** 供应商名称。 */
    @ExcelProperty("供应商")
    private String supplierName;

    /** 合同号。 */
    @ExcelProperty("合同号")
    private String contractNo;

    /** 币种。 */
    @ExcelProperty("币种")
    private String currencyCode;

    /** 运费。 */
    @ExcelProperty("运费")
    private String freight;

    /** 交货地址。 */
    @ExcelProperty("交货地址")
    private String shippingAddress;

    /** 折扣额。 */
    @ExcelProperty("折扣额")
    private String discountAmount;

    /** 不含税总额。 */
    @ExcelProperty("不含税总额")
    private String totalAmount;

    /** 税额合计。 */
    @ExcelProperty("税额")
    private String totalTaxAmount;

    /** 价税合计。 */
    @ExcelProperty("价税合计")
    private String totalTaxInclusive;

    /** 备注。 */
    @ExcelProperty("备注")
    private String remark;
}
