package com.company.inventory.model.vo.returns;

import java.time.LocalDateTime;

/**
 * 销售退货单创建结果出参(含联动生成的入库单号)。
 *
 * @param id           退货单 ID
 * @param docNo        退货单号
 * @param warehouseId  退货入库仓库 ID
 * @param status       单据状态(finished)
 * @param remark       备注
 * @param creator      创建人
 * @param inDocNo      联动生成的入库单号(过账链路)
 * @param createdAt    创建时间
 * @author inventory
 */
public record SalesReturnCreatedVO(Long id, String docNo, Long warehouseId,
        String status, String remark, String creator, String inDocNo,
        LocalDateTime createdAt) {
}
