package com.company.inventory.dto.item;
import jakarta.validation.constraints.NotBlank;

/**
 * 新建物品入参。
 *
 * @param itemCode       物品编码
 * @param itemName       物品名称
 * @param unit           单位
 * @param spec           规格
 * @param attributes     扩展属性(JSON 字符串)
 * @param category       物料分类(可空)
 * @param minStock       最低库存预警线(可空)
 * @param defaultTaxRate 默认税率(百分数,可空默认 13)
 * @author inventory
 */
public record ItemCreateDTO(
        @NotBlank(message = "物品编码必填") String itemCode,
        @NotBlank(message = "物品名称必填") String itemName,
        @NotBlank(message = "单位必填") String unit,
        String spec,
        String attributes,
        String category,
        java.math.BigDecimal minStock,
        java.math.BigDecimal defaultTaxRate) {
}
