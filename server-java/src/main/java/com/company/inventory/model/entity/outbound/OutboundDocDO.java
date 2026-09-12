package com.company.inventory.model.entity.outbound;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/**
 * 出库单表实体(表 OutboundDoc)。
 *
 * @author inventory
 */
@TableName("outbound_doc")
@Getter
@Setter
public class OutboundDocDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据号(CK-YYYYMMDD-NNNN,唯一)。 */
    private String docNo;
    /** 仓库 ID。 */
    private Long warehouseId;
    /** 单据状态:finished。 */
    private String status;
    /** 备注。 */
    private String remark;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间(UTC)。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 关联单据类型(sales=销售发货,空=手工出库)。 */
    private String refType;
    /** 关联销售订单 ID(可空)。 */
    private Long refDocId;
    /** 单据日期(可空)。 */
    private LocalDate docDate;
    /** 承运商(可空)。 */
    private String carrier;
    /** 车牌(可空)。 */
    private String vehicleNo;
    /** 运费(可空)。 */
    private java.math.BigDecimal freight;

}
