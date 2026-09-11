package com.company.inventory.model.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 出入库流水表实体(表 StockTransaction)。
 *
 * <p>流水只插不改,必带 afterQty(事务内扣减/增加后回读)。</p>
 *
 * @author inventory
 */
@TableName("stock_transaction")
@Getter
@Setter
public class StockTransactionDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 仓库 ID。 */
    private Long warehouseId;
    /** 物品 ID。 */
    private Long itemId;
    /** 批次 ID,无批次为 0。 */
    private Long batchId;
    /** 库位 ID,无库位为 0。 */
    private Long locationId;
    /** 变动数量(入库为正,出库为负)。 */
    private BigDecimal changeQty;
    /** 变动后余额。 */
    private BigDecimal afterQty;
    /** 业务类型:inbound / outbound。 */
    private String bizCode;
    /** 关联单据号。 */
    private String docNo;
    /** 操作人。 */
    private String operator;
    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

}
