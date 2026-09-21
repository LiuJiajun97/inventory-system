package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * 价目表列表查询条件(V26:owner_type 必填 + 对方单位关键字筛选 + 分页)。
 *
 * @author inventory
 */
@Getter
@Setter
public class PriceListQuery extends PageQuery {

    /** 对方类型(必填):supplier / customer。 */
    @NotBlank(message = "对方类型必填")
    private String ownerType;

    /** 对方单位编码/名称模糊关键词(可空)。 */
    private String ownerKeyword;

}
