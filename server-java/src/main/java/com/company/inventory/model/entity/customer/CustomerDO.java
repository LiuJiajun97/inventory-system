package com.company.inventory.model.entity.customer;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 客户主数据实体(表 Customer)。
 *
 * @author inventory
 */
@TableName("customer")
@Getter
@Setter
public class CustomerDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 客户编码。 */
    private String customerCode;
    /** 客户名称。 */
    private String customerName;
    /** 税号。 */
    private String taxNo;
    /** 默认税率。 */
    private BigDecimal defaultTaxRate;
    /** 联系人。 */
    private String contact;
    /** 联系电话。 */
    private String phone;
    /** 地址。 */
    private String address;
    /** 结算方式。 */
    private String settleMethod;
    /** 客户账期天数。 */
    private Integer payTermDays;
    /** 开户行(可空)。 */
    private String bankName;
    /** 银行账号(可空)。 */
    private String bankAccount;
    /** 信用额度(可空)。 */
    private BigDecimal creditLimit;
    /** 交货地址(可空)。 */
    private String deliveryAddress;
    /** 状态,1 启用 0 停用。 */
    private Integer status;
    /** 备注。 */
    private String remark;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
