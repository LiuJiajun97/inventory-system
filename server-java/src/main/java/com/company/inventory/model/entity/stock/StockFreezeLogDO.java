package com.company.inventory.model.entity.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存冻结审计流水实体(表 stock_freeze_log,纯追加)。
 *
 * <p>记录每次冻结/解冻(action=freeze/unfreeze),原因/操作人留痕,查询热路径不碰本表。</p>
 *
 * @author inventory
 */
@TableName("stock_freeze_log")
@Getter
@Setter
public class StockFreezeLogDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 仓库 ID。 */
    private Long warehouseId;
    /** 批次 ID。 */
    private Long batchId;
    /** 物品 ID(回填展示用)。 */
    private Long itemId;
    /** 操作:freeze 冻结 / unfreeze 解冻。 */
    private String action;
    /** 原因(冻结必填,解冻可空)。 */
    private String reason;
    /** 操作人。 */
    private String operator;
    /** 创建时间。 */
    private LocalDateTime createdAt;

}
