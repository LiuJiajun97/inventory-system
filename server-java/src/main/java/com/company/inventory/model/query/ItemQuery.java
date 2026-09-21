package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;

import lombok.Getter;
import lombok.Setter;

/**
 * 物品列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/items 的查询参数绑定。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class ItemQuery extends PageQuery {

    /** 关键词(可空,对编码/名称模糊不区分大小写)。 */
    private String keyword;

    /** 物品分类字典值(可空,精确匹配)。 */
    private String itemCategory;


}
