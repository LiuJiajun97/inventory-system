package com.company.inventory.model.entity.transfer;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 调拨单表头实体(表 TransferDoc)。
 *
 * @author inventory
 */
@TableName("transfer_doc")
@Getter
@Setter
public class TransferDocDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据编号。 */
    private String docNo;
    /** 单据日期。 */
    private LocalDate docDate;
    /** 源仓库 ID。 */
    private Long fromWarehouseId;
    /** 目的仓库 ID。 */
    private Long toWarehouseId;
    /** 成本参考合计。 */
    private BigDecimal totalAmount;
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
    /** 审批人。 */
    private String approver;
    /** 审批时间。 */
    private LocalDateTime approvedAt;
    /** 驳回原因。 */
    private String rejectReason;
    /** 备注。 */
    private String remark;
    /** 承运商(可空)。 */
    private String carrier;

}
