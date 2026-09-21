package com.company.inventory.model.vo.stock;

import java.time.LocalDateTime;

/**
 * 库存冻结记录出参(V26:时间/物品/批次/操作/原因/操作人)。
 *
 * @param id          主键
 * @param warehouseId 仓库 ID
 * @param batchId     批次 ID
 * @param itemId      物品 ID
 * @param action      操作:freeze 冻结 / unfreeze 解冻
 * @param reason      原因(冻结必填,解冻可空)
 * @param operator    操作人
 * @param createdAt   创建时间
 * @param itemCode    物品编码(回填,可空)
 * @param itemName    物品名称(回填,可空)
 * @param batchNo     批次号(回填,可空)
 * @author inventory
 */
public record FreezeLogVO(Long id, Long warehouseId, Long batchId, Long itemId,
        String action, String reason, String operator, LocalDateTime createdAt,
        String itemCode, String itemName, String batchNo) {
}
