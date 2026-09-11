package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.customer.CustomerCreateDTO;
import com.company.inventory.model.dto.customer.CustomerUpdateDTO;
import com.company.inventory.model.query.CustomerQuery;
import com.company.inventory.model.vo.customer.CustomerVO;

/**
 * 客户主数据服务接口(admin 可建可改,其余角色只读列表)。
 *
 * @author inventory
 */
public interface CustomerService {

    /**
     * 客户分页列表(keyword 对编码/名称模糊)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CustomerVO> list(CustomerQuery query);

    /**
     * 客户详情,不存在抛 404。
     *
     * @param id 客户 ID
     * @return 客户
     */
    CustomerVO get(long id);

    /**
     * 新建客户(仅 admin),编码重复抛 400。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建客户
     */
    CustomerVO create(CustomerCreateDTO dto, String username);

    /**
     * 编辑客户(仅 admin,编码不可改)。
     *
     * @param id       客户 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的客户
     */
    CustomerVO update(long id, CustomerUpdateDTO dto, String username);
}
