package com.company.inventory.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Size;

/**
 * 客户列表查询条件。
 *
 * @author inventory
 */
public class CustomerQuery extends PageQuery {

    /** 编码或名称关键字。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String keyword;

    /**
     * 获取编码或名称关键字。
     *
     * @return 编码或名称关键字
     */
    public String getKeyword() {
        return keyword;
    }

    /**
     * 设置编码或名称关键字。
     *
     * @param keyword 编码或名称关键字
     */
    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
