package com.company.inventory.model.entity.inbound;
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
 * 入库单表实体(表 InboundDoc)。
 *
 * @author inventory
 */
@TableName("inbound_doc")
@Getter
@Setter
public class InboundDocDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据号(RK-YYYYMMDD-NNNN,唯一)。 */
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

    /** 关联单据类型(purchase=采购到货,空=手工入库)。 */
    private String refType;
    /** 关联采购订单 ID(可空)。 */
    private Long refDocId;
    /** 单据日期(可空)。 */
    private LocalDate docDate;

}
