package com.company.inventory.model.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * 批次表实体(表 Batch)。
 *
 * <p>唯一键 (itemId, batchNo);生产日期/保质期为 DATE 类型。</p>
 *
 * @author inventory
 */
@TableName("batch")
@Getter
@Setter
public class BatchDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 物品 ID。 */
    private Long itemId;
    /** 批次号。 */
    private String batchNo;
    /** 生产日期。 */
    private LocalDate productionDate;
    /** 保质期到期日。 */
    private LocalDate expiryDate;
    /** 供应商。 */
    private String supplier;
    /** 批次状态:active。 */
    private String status;

}
