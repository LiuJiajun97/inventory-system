package com.company.inventory.model.entity.opening;

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
 * 期初单头表实体(表 opening_stock_doc,create 即过账,status 恒 finished)。
 *
 * @author inventory
 */
@TableName("opening_stock_doc")
@Getter
@Setter
public class OpeningStockDocDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 单据号(QC-YYYYMMDD-NNNN,唯一)。 */
    private String docNo;
    /** 单据日期。 */
    private LocalDate docDate;
    /** 仓库 ID。 */
    private Long warehouseId;
    /** 总数量(行数量合计,服务端落)。 */
    private BigDecimal totalQty;
    /** 备注。 */
    private String remark;
    /** 单据状态:finished。 */
    private String status;
    /** 创建人。 */
    @TableField(fill = FieldFill.INSERT)
    private String creator;
    /** 创建时间(UTC)。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /** 修改人。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;
    /** 修改时间(UTC)。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
