package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.stock.StockFreezeDTO;
import com.company.inventory.model.dto.stock.StockUnfreezeDTO;
import com.company.inventory.model.query.FreezeLogQuery;
import com.company.inventory.model.vo.stock.FreezeLogVO;

/**
 * 库存冻结服务接口(V26:仓+批次粒度,冻结只拦出不拦入)。
 *
 * @author inventory
 */
public interface StockFreezeService {

    /**
     * 冻结批次:该仓+批次下所有库存行置 frozen=true,写审计流水。
     *
     * @param dto      入参(仓库/批次号/原因必填)
     * @param username 操作人
     */
    void freeze(StockFreezeDTO dto, String username);

    /**
     * 解冻批次:该仓+批次下所有冻结行置 frozen=false,写审计流水。
     *
     * @param dto      入参(仓库/批次号)
     * @param username 操作人
     */
    void unfreeze(StockUnfreezeDTO dto, String username);

    /**
     * 冻结记录分页查询(时间倒序,带物品编码/名称回填)。
     *
     * @param query 查询条件(仓库/批次号模糊,分页)
     * @return 分页结果
     */
    PageResult<FreezeLogVO> logs(FreezeLogQuery query);

}
