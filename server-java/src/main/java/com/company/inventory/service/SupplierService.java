package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.supplier.SupplierCreateDTO;
import com.company.inventory.model.dto.supplier.SupplierUpdateDTO;
import com.company.inventory.model.query.SupplierQuery;
import com.company.inventory.model.vo.supplier.SupplierVO;

/**
 * 供应商主数据服务接口(admin 可建可改,其余角色只读列表)。
 *
 * @author inventory
 */
public interface SupplierService {

    /**
     * 供应商分页列表(keyword 对编码/名称模糊)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SupplierVO> list(SupplierQuery query);

    /**
     * 供应商详情,不存在抛 404。
     *
     * @param id 供应商 ID
     * @return 供应商
     */
    SupplierVO get(long id);

    /**
     * 新建供应商(仅 admin),编码重复抛 400。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建供应商
     */
    SupplierVO create(SupplierCreateDTO dto, String username);

    /**
     * 编辑供应商(仅 admin,编码不可改)。
     *
     * @param id       供应商 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的供应商
     */
    SupplierVO update(long id, SupplierUpdateDTO dto, String username);
}
