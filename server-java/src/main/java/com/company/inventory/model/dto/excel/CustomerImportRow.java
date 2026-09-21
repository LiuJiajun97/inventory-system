package com.company.inventory.model.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户导入 Excel 行模型(列头中文,与模板下载一致)。
 *
 * <p>字段全为 String,由导入服务负责必填/数值/字典映射校验;
 * 带 * 的列头为必填列。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class CustomerImportRow {

    /** 客户编码(必填,唯一)。 */
    @ExcelProperty("客户编码*")
    private String customerCode;

    /** 客户名称(必填)。 */
    @ExcelProperty("客户名称*")
    private String customerName;

    /** 联系人(可选)。 */
    @ExcelProperty("联系人")
    private String contact;

    /** 联系电话(可选)。 */
    @ExcelProperty("电话")
    private String phone;

    /** 地址(可选)。 */
    @ExcelProperty("地址")
    private String address;

    /** 结算方式(可选:月结→month,预付→prepay,货到付款→cod,账期→credit)。 */
    @ExcelProperty("结算方式")
    private String settleMethod;

    /** 默认税率(可选,百分数,如 13 表示 13%,留空默认 13)。 */
    @ExcelProperty("默认税率")
    private String defaultTaxRate;

    /** 税号(可选)。 */
    @ExcelProperty("税号")
    private String taxNo;

    /** 邮箱(可选)。 */
    @ExcelProperty("邮箱")
    private String email;

    /** 开户行(可选)。 */
    @ExcelProperty("开户行")
    private String bankName;

    /** 银行账号(可选)。 */
    @ExcelProperty("银行账号")
    private String bankAccount;

    /** 信用额度(可选)。 */
    @ExcelProperty("信用额度")
    private String creditLimit;

    /** 付款条件(可选,账期天数)。 */
    @ExcelProperty("付款条件(天)")
    private String payTermDays;

    /** 交货地址(可选)。 */
    @ExcelProperty("交货地址")
    private String deliveryAddress;
}
