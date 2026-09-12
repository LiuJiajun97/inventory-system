package com.company.inventory.model.entity.returns;

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
 * 销售退货单头实体(表 sales_return)。
 *
 * @author inventory
 */
@TableName("sales_return")
@Getter
@Setter
public class SalesReturnDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据号(XT-YYYYMMDD-NNNN,唯一)。 */
    private String docNo;
    /** 单据日期。 */
    private LocalDate docDate;
    /** 原销售订单 ID。 */
    private Long salesOrderId;
    /** 退货仓库 ID。 */
    private Long warehouseId;
    /** 单据总金额(行金额合计,服务端落)。 */
    private java.math.BigDecimal totalAmount;
    /** 备注。 */
    private String remark;
    /** 单据状态:finished(create 即过账)。 */
    private String status;
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
