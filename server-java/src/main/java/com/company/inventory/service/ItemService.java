package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.item.ItemCreateDTO;
import com.company.inventory.dto.item.ItemUpdateDTO;
import com.company.inventory.query.ItemQuery;
import com.company.inventory.vo.item.ItemVO;














/**
 * 物品服务接口。
 *
 * @author inventory
 */
public interface ItemService {

    /**
     * 物品分页列表(按 id 升序,keyword 对编码/名称模糊不区分大小写)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    PageResult<ItemVO> list(ItemQuery query);

    /**
     * 物品详情,不存在抛 404。
     *
     * @param id 物品 ID
     * @return 物品
     */
    ItemVO get(long id);

    /**
     * 新建物品(仅 admin),编码重复抛 400。
     *
     * @param dto 入参
     * @return 新建物品
     */
    ItemVO create(ItemCreateDTO dto);

    /**
     * 编辑物品(仅 admin,编码不可改,至少一个字段非空)。
     *
     * @param id  物品 ID
     * @param dto 入参
     * @return 更新后的物品
     */
    ItemVO update(long id, ItemUpdateDTO dto);
}
