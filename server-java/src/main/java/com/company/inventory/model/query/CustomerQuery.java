package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 客户列表查询条件。
 *
 * @author inventory
 */
@Getter
@Setter
public class CustomerQuery extends PageQuery {

    /** 编码或名称关键字。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String keyword;


}
