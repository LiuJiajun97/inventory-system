package com.company.inventory.model.vo.price;

import java.math.BigDecimal;

/**
 * 生效价目行出参(V26:单据新建选物品带价用,按物品去重取 valid_from 最近)。
 *
 * @param itemId    物品 ID
 * @param unitPrice 不含税单价
 * @param taxRate   税率(百分数,可空=NULL 按物品默认税率)
 * @author inventory
 */
public record EffectivePriceVO(Long itemId, BigDecimal unitPrice, BigDecimal taxRate) {
}
