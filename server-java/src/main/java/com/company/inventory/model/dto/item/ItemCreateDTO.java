package com.company.inventory.model.dto.item;
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
 * @param barcode        条码(可空,非空唯一)
 * @param secondUnit     辅助单位(可空)
 * @param convertFactor  换算率(可空,1 辅助单位对应的基本单位数)
 * @param brand          品牌(可空)
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
        java.math.BigDecimal defaultTaxRate,
        String barcode,
        String secondUnit,
        java.math.BigDecimal convertFactor,
        String brand) {

    /** 兼容旧签名构造器(V9 新增字段默认 null,既有调用/测试不受影响)。 */
    public ItemCreateDTO(String itemCode, String itemName, String unit, String spec,
            String attributes, String category, java.math.BigDecimal minStock,
            java.math.BigDecimal defaultTaxRate) {
        this(itemCode, itemName, unit, spec, attributes, category, minStock, defaultTaxRate,
                null, null, null, null);
    }
}
