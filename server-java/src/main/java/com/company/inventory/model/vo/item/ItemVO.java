package com.company.inventory.model.vo.item;
import java.time.LocalDateTime;

/**
 * 物品出参(契约:8 字段)。
 *
 * @param id         主键
 * @param itemCode   物品编码
 * @param itemName   物品名称
 * @param unit       单位
 * @param spec       规格
 * @param attributes 扩展属性(JSON 字符串)
 * @param status     状态
 * @param createdAt  创建时间
 * @author inventory
 */
/**
 * 物品出参(契约:8 字段 + 一期增量 3 字段)。
 *
 * @param id             主键
 * @param itemCode       物品编码
 * @param itemName       物品名称
 * @param unit           单位
 * @param spec           规格
 * @param attributes     扩展属性(JSON 字符串)
 * @param status         状态
 * @param createdAt      创建时间
 * @param category       物料分类(增量)
 * @param minStock       最低库存预警线(增量)
 * @param defaultTaxRate 默认税率(增量)
 * @author inventory
 */
public record ItemVO(Long id, String itemCode, String itemName, String unit,
        String spec, String attributes, Integer status, LocalDateTime createdAt,
        String category, java.math.BigDecimal minStock, java.math.BigDecimal defaultTaxRate) {
}
