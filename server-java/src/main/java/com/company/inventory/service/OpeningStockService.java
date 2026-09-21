package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.opening.OpeningStockCreateDTO;
import com.company.inventory.model.query.OpeningStockQuery;
import com.company.inventory.model.vo.opening.OpeningStockCreatedVO;
import com.company.inventory.model.vo.opening.OpeningStockDocVO;

/**
 * 期初库存服务:系统启用时批量录入现有库存。
 *
 * <p>create 即过账(与出入库一致,无草稿流):同事务内服务端构造入库单调
 * InboundService.create 走现有入库链路(ref_type=opening,ref_doc_id=期初单 ID),
 * 每物品×仓库限一次期初,任一失败整单回滚。</p>
 *
 * @author inventory
 */
public interface OpeningStockService {

    /**
     * 新建期初单并过账(单事务)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 单据头
     */
    OpeningStockCreatedVO create(OpeningStockCreateDTO dto, String username);

    /**
     * 期初单分页列表。
     *
     * @param query 查询条件(warehouseId/docNo/status/from/to/page/pageSize)
     * @return 分页结果
     */
    PageResult<OpeningStockDocVO> list(OpeningStockQuery query);

    /**
     * 期初单详情。
     *
     * @param id 单据 ID
     * @return 单据(含仓库与单据行)
     */
    OpeningStockDocVO get(long id);
}
