package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * 采购对账导出 Excel 行模型(列头中文,供应商维度)。
 *
 * <p>数量/金额统一 String 直出。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class PurchaseReconExportRow {

    /** 供应商编码。 */
    @ExcelProperty("供应商编码")
    private String supplierCode;

    /** 供应商名称。 */
    @ExcelProperty("供应商名称")
    private String supplierName;

    /** 采购单数。 */
    @ExcelProperty("采购单数")
    private String orderCount;

    /** 采购量。 */
    @ExcelProperty("采购量")
    private String orderQty;

    /** 采购金额(价税合计)。 */
    @ExcelProperty("采购金额(价税合计)")
    private String orderAmount;

    /** 退货量。 */
    @ExcelProperty("退货量")
    private String returnQty;

    /** 退货金额(价税合计)。 */
    @ExcelProperty("退货金额(价税合计)")
    private String returnAmount;

    /** 净采购量(采购-退货)。 */
    @ExcelProperty("净采购量")
    private String netQty;

    /** 净采购金额(采购-退货)。 */
    @ExcelProperty("净采购金额(价税合计)")
    private String netAmount;
}
