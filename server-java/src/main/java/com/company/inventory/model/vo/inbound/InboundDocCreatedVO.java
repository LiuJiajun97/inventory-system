package com.company.inventory.model.vo.inbound;
import java.time.LocalDateTime;

/**
 * 新建入库单响应(契约:仅单据头 7 字段,不含 warehouse/items)。
 *
 * @param id          主键
 * @param docNo       单据号
 * @param warehouseId 仓库 ID
 * @param status      单据状态
 * @param remark      备注(可为 null)
 * @param creator     创建人
 * @param createdAt   创建时间
 * @author inventory
 */
public record InboundDocCreatedVO(Long id, String docNo, Long warehouseId, String status,
        String remark, String creator, LocalDateTime createdAt) {
}
