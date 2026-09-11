package com.company.inventory.entity.customer;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户主数据实体(表 Customer)。
 *
 * @author inventory
 */
@TableName("\"Customer\"")
public class CustomerDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 客户编码。 */
    @TableField("\"customerCode\"")
    private String customerCode;
    /** 客户名称。 */
    @TableField("\"customerName\"")
    private String customerName;
    /** 税号。 */
    @TableField("\"taxNo\"")
    private String taxNo;
    /** 默认税率。 */
    @TableField("\"defaultTaxRate\"")
    private BigDecimal defaultTaxRate;
    /** 联系人。 */
    @TableField("\"contact\"")
    private String contact;
    /** 联系电话。 */
    @TableField("\"phone\"")
    private String phone;
    /** 地址。 */
    @TableField("\"address\"")
    private String address;
    /** 结算方式。 */
    @TableField("\"settleMethod\"")
    private String settleMethod;
    /** 客户账期天数。 */
    @TableField("\"payTermDays\"")
    private Integer payTermDays;
    /** 状态,1 启用 0 停用。 */
    @TableField("\"status\"")
    private Integer status;
    /** 备注。 */
    @TableField("\"remark\"")
    private String remark;
    /** 创建人。 */
    @TableField(value = "\"creator\"", fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间。 */
    @TableField(value = "\"createdAt\"", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(value = "\"updater\"", fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(value = "\"updatedAt\"", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键。
     *
     * @param id 主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取客户编码。
     *
     * @return 客户编码
     */
    public String getCustomerCode() {
        return customerCode;
    }

    /**
     * 设置客户编码。
     *
     * @param customerCode 客户编码
     */
    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    /**
     * 获取客户名称。
     *
     * @return 客户名称
     */
    public String getCustomerName() {
        return customerName;
    }

    /**
     * 设置客户名称。
     *
     * @param customerName 客户名称
     */
    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    /**
     * 获取税号。
     *
     * @return 税号
     */
    public String getTaxNo() {
        return taxNo;
    }

    /**
     * 设置税号。
     *
     * @param taxNo 税号
     */
    public void setTaxNo(String taxNo) {
        this.taxNo = taxNo;
    }

    /**
     * 获取默认税率。
     *
     * @return 默认税率
     */
    public BigDecimal getDefaultTaxRate() {
        return defaultTaxRate;
    }

    /**
     * 设置默认税率。
     *
     * @param defaultTaxRate 默认税率
     */
    public void setDefaultTaxRate(BigDecimal defaultTaxRate) {
        this.defaultTaxRate = defaultTaxRate;
    }

    /**
     * 获取联系人。
     *
     * @return 联系人
     */
    public String getContact() {
        return contact;
    }

    /**
     * 设置联系人。
     *
     * @param contact 联系人
     */
    public void setContact(String contact) {
        this.contact = contact;
    }

    /**
     * 获取联系电话。
     *
     * @return 联系电话
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置联系电话。
     *
     * @param phone 联系电话
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 获取地址。
     *
     * @return 地址
     */
    public String getAddress() {
        return address;
    }

    /**
     * 设置地址。
     *
     * @param address 地址
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * 获取结算方式。
     *
     * @return 结算方式
     */
    public String getSettleMethod() {
        return settleMethod;
    }

    /**
     * 设置结算方式。
     *
     * @param settleMethod 结算方式
     */
    public void setSettleMethod(String settleMethod) {
        this.settleMethod = settleMethod;
    }

    /**
     * 获取客户账期天数。
     *
     * @return 客户账期天数
     */
    public Integer getPayTermDays() {
        return payTermDays;
    }

    /**
     * 设置客户账期天数。
     *
     * @param payTermDays 客户账期天数
     */
    public void setPayTermDays(Integer payTermDays) {
        this.payTermDays = payTermDays;
    }

    /**
     * 获取状态,1 启用 0 停用。
     *
     * @return 状态,1 启用 0 停用
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置状态,1 启用 0 停用。
     *
     * @param status 状态,1 启用 0 停用
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取备注。
     *
     * @return 备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置备注。
     *
     * @param remark 备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 获取创建人。
     *
     * @return 创建人
     */
    public String getCreator() {
        return creator;
    }

    /**
     * 设置创建人。
     *
     * @param creator 创建人
     */
    public void setCreator(String creator) {
        this.creator = creator;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取修改人。
     *
     * @return 修改人
     */
    public String getUpdater() {
        return updater;
    }

    /**
     * 设置修改人。
     *
     * @param updater 修改人
     */
    public void setUpdater(String updater) {
        this.updater = updater;
    }

    /**
     * 获取修改时间。
     *
     * @return 修改时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置修改时间。
     *
     * @param updatedAt 修改时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
