package com.company.inventory.model.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存余额表实体(表 Stock)。
 *
 * <p>唯一键 (warehouseId, itemId, batchId, locationId),无批次/库位时以 0 占位;
 * quantity 为 DECIMAL(18,4)。</p>
 *
 * @author inventory
 */
@TableName("stock")
@Getter
@Setter
public class StockDO {

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
    /** 库存数量。 */
    private BigDecimal quantity;
    /** 预占量(销售订单审批预占,方案 §5.2;可用=quantity-preAllocatedQty)。 */
    private BigDecimal preAllocatedQty;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

}
