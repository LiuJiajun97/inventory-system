package com.company.inventory.model.vo.returns;

import java.time.LocalDateTime;

/**
 * 采购退货单创建结果出参(含联动生成的出库单号)。
 *
 * @param id        退货单 ID
 * @param docNo     退货单号
 * @param warehouseId 退货仓库 ID
 * @param status    单据状态(finished)
 * @param remark    备注
 * @param creator   创建人
 * @param outDocNo  联动生成的出库单号(过账链路)
 * @author inventory
 */
public record PurchaseReturnCreatedVO(Long id, String docNo, Long warehouseId,
        String status, String remark, String creator, String outDocNo,
        LocalDateTime createdAt) {
}
