package com.company.inventory.model.vo.price;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 价目表详情出参(V26:表头 + 行明细含物品回填)。
 *
 * @param id         主键
 * @param ownerType  对方类型
 * @param ownerId    对方 ID
 * @param name       价目名称
 * @param validFrom  生效起(可空=不限)
 * @param validUntil 生效止(可空=不限)
 * @param status     状态
 * @param creator    创建人
 * @param createdAt  创建时间
 * @param lines      价目行明细
 * @author inventory
 */
public record PriceListDetailVO(Long id, String ownerType, Long ownerId,
        String name, LocalDate validFrom, LocalDate validUntil,
        Integer status, String creator, LocalDateTime createdAt,
        List<PriceListLineVO> lines) {
}
