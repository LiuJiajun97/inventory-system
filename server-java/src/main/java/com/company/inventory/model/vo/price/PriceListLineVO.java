package com.company.inventory.model.vo.price;

import java.math.BigDecimal;

/**
 * 价目表行明细出参(V26:含物品编码/名称回填)。
 *
 * @param itemId     物品 ID
 * @param itemCode   物品编码(回填)
 * @param itemName   物品名称(回填)
 * @param unitPrice  不含税单价
 * @param taxRate    税率(百分数,可空=NULL 按物品默认税率)
 * @author inventory
 */
public record PriceListLineVO(Long itemId, String itemCode, String itemName,
        BigDecimal unitPrice, BigDecimal taxRate) {
}
