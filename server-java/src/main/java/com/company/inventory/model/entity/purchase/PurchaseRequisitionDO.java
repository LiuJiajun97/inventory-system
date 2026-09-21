package com.company.inventory.model.entity.purchase;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 请购单表头实体(V25,表 purchase_requisition):状态 draft/submitted/converted/cancelled,无审批。
 *
 * @author inventory
 */
@TableName("purchase_requisition")
@Getter
@Setter
public class PurchaseRequisitionDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据编号(QG-YYYYMMDD-NNNN)。 */
    private String docNo;
    /** 单据日期。 */
    private LocalDate docDate;
    /** 收货仓库 ID。 */
    private Long warehouseId;
    /** 申请人用户 ID。 */
    private Long applicantId;
    /** 申请部门(字典 dept 类型的 dict_key,可空)。 */
    private String department;
    /** 单据状态。 */
    private String status;
    /** 制单人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 制单时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    /** 备注。 */
    private String remark;

}