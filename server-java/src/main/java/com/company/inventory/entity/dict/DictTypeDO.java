package com.company.inventory.entity.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 字典类型表实体(表 DictType):管理字典分类(仓库类型/物品分类等)。
 *
 * @author inventory
 */
@TableName("\"DictType\"")
public class DictTypeDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 类型编码(唯一,不可改)。 */
    @TableField("\"typeCode\"")
    private String typeCode;
    /** 类型名称。 */
    @TableField("\"typeName\"")
    private String typeName;
    /** 备注。 */
    @TableField("\"remark\"")
    private String remark;
    /** 状态:1 启用 / 0 停用。 */
    @TableField("\"status\"")
    private Integer status;
    /** 创建时间。 */
    @TableField("\"createdAt\"")
    private LocalDateTime createdAt;
    /** 更新时间。 */
    @TableField("\"updatedAt\"")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
