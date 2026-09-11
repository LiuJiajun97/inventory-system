package com.company.inventory.query;

import com.company.inventory.common.page.PageQuery;

/**
 * 物品列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/items 的查询参数绑定。</p>
 *
 * @author inventory
 */
public class ItemQuery extends PageQuery {

    /** 关键词(可空,对编码/名称模糊不区分大小写)。 */
    private String keyword;

    /**
     * 获取关键词。
     *
     * @return 关键词(可空)
     */
    public String getKeyword() {
        return keyword;
    }

    /**
     * 设置关键词。
     *
     * @param keyword 关键词(可空)
     */
    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
