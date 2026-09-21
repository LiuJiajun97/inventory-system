package com.company.inventory.model.vo.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户导出 Excel 行模型(列头中文)。
 *
 * @author inventory
 */
@Getter
@Setter
public class CustomerExportRow {

    /** 客户编码。 */
    @ExcelProperty("客户编码")
    private String customerCode;

    /** 客户名称。 */
    @ExcelProperty("客户名称")
    private String customerName;

    /** 联系人。 */
    @ExcelProperty("联系人")
    private String contact;

    /** 联系电话。 */
    @ExcelProperty("电话")
    private String phone;

    /** 地址。 */
    @ExcelProperty("地址")
    private String address;

    /** 结算方式(中文:月结/预付/货到付款/账期)。 */
    @ExcelProperty("结算方式")
    private String settleMethod;

    /** 默认税率(百分数)。 */
    @ExcelProperty("默认税率(%)")
    private String defaultTaxRate;

    /** 税号。 */
    @ExcelProperty("税号")
    private String taxNo;

    /** 邮箱。 */
    @ExcelProperty("邮箱")
    private String email;

    /** 开户行。 */
    @ExcelProperty("开户行")
    private String bankName;

    /** 银行账号。 */
    @ExcelProperty("银行账号")
    private String bankAccount;

    /** 信用额度。 */
    @ExcelProperty("信用额度")
    private String creditLimit;

    /** 付款条件(账期天数)。 */
    @ExcelProperty("付款条件(天)")
    private String payTermDays;

    /** 交货地址。 */
    @ExcelProperty("交货地址")
    private String deliveryAddress;

    /** 创建时间(yyyy-MM-dd HH:mm:ss)。 */
    @ExcelProperty("创建时间")
    private String createdAt;
}
