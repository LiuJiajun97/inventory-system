package com.company.inventory.model.entity.stock;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 序列号表实体(表 Serial)。
 *
 * <p>唯一键 (itemId, serialNo);状态 in_stock / out。</p>
 *
 * @author inventory
 */
@TableName("serial")
@Getter
@Setter
public class SerialDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 物品 ID。 */
    private Long itemId;
    /** 序列号。 */
    private String serialNo;
    /** 所在仓库 ID(可为空)。 */
    private Long warehouseId;
    /** 状态:in_stock / out。 */
    private String status;
    /** 入库时间。 */
    private LocalDateTime inboundTime;
    /** 出库时间。 */
    private LocalDateTime outboundTime;
    /** 最近入库单号(V9 追溯链,可空)。 */
    private String refDocNo;
    /** 最近出库单号(V9 追溯链,可空)。 */
    private String refOutDocNo;

}
