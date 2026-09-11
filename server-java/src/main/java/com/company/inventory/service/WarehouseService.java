package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.warehouse.WarehouseCreateDTO;
import com.company.inventory.model.dto.warehouse.WarehouseUpdateDTO;
import com.company.inventory.model.query.WarehouseQuery;
import com.company.inventory.model.vo.warehouse.WarehouseVO;














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

    /**
     * 编辑仓库(仅 admin,编码不可改)。
     *
     * <p>防不一致校验:
     * enableLocation 由 1→0 时,该仓存在库位或库位库存行,400 拒绝;
     * enableSerial 由 1→0 时,该仓存在序列号台账,400 拒绝;
     * status 由 1→0 时,该仓存在 quantity&gt;0 或 preAllocatedQty&gt;0 的库存,400 拒绝。</p>
     *
     * @param id  仓库 ID
     * @param dto 入参(至少一个字段非空)
     * @return 更新后的仓库
     */
    WarehouseVO update(long id, WarehouseUpdateDTO dto);
}
