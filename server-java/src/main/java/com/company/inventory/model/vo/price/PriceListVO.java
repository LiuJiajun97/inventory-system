package com.company.inventory.model.vo.price;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 价目表列表行出参(V26:含对方单位回填与行数)。
 *
 * @param id         主键
 * @param ownerType  对方类型:supplier / customer
 * @param ownerId    对方 ID
 * @param ownerCode  对方编码(回填)
 * @param ownerName  对方名称(回填)
 * @param name       价目名称
 * @param validFrom  生效起(可空=不限)
 * @param validUntil 生效止(可空=不限)
 * @param lineCount  行数
 * @param status     状态:1 启用 / 0 停用
 * @param creator    创建人
 * @param createdAt  创建时间
 * @param updater    更新人
 * @param updatedAt  更新时间
 * @author inventory
 */
public record PriceListVO(Long id, String ownerType, Long ownerId,
        String ownerCode, String ownerName, String name,
        LocalDate validFrom, LocalDate validUntil, Integer lineCount,
        Integer status, String creator, LocalDateTime createdAt,
        String updater, LocalDateTime updatedAt) {
}
