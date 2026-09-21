package com.company.inventory.model.entity.adjust;

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
 * 库存调整单表头实体(表 StockAdjustDoc,adjustType gain/loss/scrap)。
 *
 * @author inventory
 */
@TableName("stock_adjust_doc")
@Getter
@Setter
public class StockAdjustDocDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据编号。 */
    private String docNo;
    /** 单据日期。 */
    private LocalDate docDate;
    /** 仓库 ID。 */
    private Long warehouseId;
    /** 调整类型。 */
    private String adjustType;
    /** 来源盘点单号。 */
    private String refDocNo;
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

}
