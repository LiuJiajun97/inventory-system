package com.company.inventory.model.dto.item;

import java.math.BigDecimal;

/**
 * 编辑物品入参(编码不可改,其余字段可空=不更新;至少提供一个字段)。
 *
 * @param itemName       物品名称
 * @param unit           单位
 * @param spec           规格
 * @param attributes     扩展属性(JSON 字符串)
 * @param category       物料分类
 * @param minStock       最低库存预警线
 * @param defaultTaxRate 默认税率(百分数)
 * @param status         状态,1 启用 0 停用
 * @author inventory
 */
public record ItemUpdateDTO(
        String itemName,
        String unit,
        String spec,
        String attributes,
        String category,
        BigDecimal minStock,
        BigDecimal defaultTaxRate,
        Integer status) {
}
