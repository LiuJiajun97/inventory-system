package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.warehouse.WarehouseCreateDTO;
import com.company.inventory.query.WarehouseQuery;
import com.company.inventory.vo.warehouse.WarehouseVO;














/**
 * 仓库服务接口。
 *
 * @author inventory
 */
public interface WarehouseService {

    /**
     * 仓库分页列表(按 id 升序,任何登录用户可查)。
     *
     * @param query 查询条件(page/pageSize)
     * @return 分页结果
     */
    PageResult<WarehouseVO> list(WarehouseQuery query);

    /**
     * 仓库详情,不存在抛 404。
     *
     * @param id 仓库 ID
     * @return 仓库
     */
    WarehouseVO get(long id);

    /**
     * 新建仓库(仅 admin),编码重复抛 400。
     *
     * @param dto 入参
     * @return 新建仓库
     */
    WarehouseVO create(WarehouseCreateDTO dto);
}
