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
 * @param barcode        条码
 * @param secondUnit     辅助单位
 * @param convertFactor  换算率
 * @param brand          品牌
 * @param referencePurchasePrice 参考采购价
 * @param referenceSalePrice 参考销售价
 * @param origin         产地
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
        String barcode,
        String secondUnit,
        BigDecimal convertFactor,
        String brand,
        BigDecimal referencePurchasePrice,
        BigDecimal referenceSalePrice,
        String origin,
        Integer status) {

    /** 兼容旧签名构造器(V9/V10 新增字段默认 null,既有调用/测试不受影响)。 */
    public ItemUpdateDTO(String itemName, String unit, String spec, String attributes,
            String category, BigDecimal minStock, BigDecimal defaultTaxRate, Integer status) {
        this(itemName, unit, spec, attributes, category, minStock, defaultTaxRate,
                null, null, null, null, null, null, null, status);
    }

    /** 兼容 V9 签名构造器(V10 新增参考采购价/参考销售价/产地默认 null)。 */
    public ItemUpdateDTO(String itemName, String unit, String spec, String attributes,
            String category, BigDecimal minStock, BigDecimal defaultTaxRate, String barcode,
            String secondUnit, BigDecimal convertFactor, String brand, Integer status) {
        this(itemName, unit, spec, attributes, category, minStock, defaultTaxRate,
                barcode, secondUnit, convertFactor, brand, null, null, null, status);
    }
}
