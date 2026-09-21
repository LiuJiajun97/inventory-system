package com.company.inventory.model.vo.transfer;

import java.math.BigDecimal;

/**
 * 调拨单行出参。
 *
 * @param id             行 ID
 * @param lineNo         行号
 * @param itemId         物品 ID
 * @param itemCode       物品编码
 * @param itemName       物品名称
 * @param specSnapshot   规格快照
 * @param unit           单位快照
 * @param qty            调拨数量(字符串)
 * @param unitPrice      成本参考单价
 * @param fromLocationId 源库位 ID
 * @param toLocationId   目的库位 ID
 * @param lineRemark     行备注
 * @param vehicleNo      车牌(V9 增量)
 * @author inventory
 */
public record TransferDocItemVO(Long id, Integer lineNo, Long itemId, String itemCode,
        String itemName, String specSnapshot, String unit, String qty, BigDecimal unitPrice,
        Long fromLocationId, Long toLocationId, String lineRemark, String vehicleNo) {
}
