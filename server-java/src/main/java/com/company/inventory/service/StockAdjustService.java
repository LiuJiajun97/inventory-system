package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.adjust.StockAdjustCreateDTO;
import com.company.inventory.query.StockAdjustQuery;
import com.company.inventory.vo.adjust.StockAdjustDocVO;

/**
 * 库存调整单服务:gain 调整入库/loss 与 scrap 调整出库,审批通过时执行库存动作并 completed。
 *
 * @author inventory
 */
public interface StockAdjustService {

    /**
     * 新建调整单(draft,手工建单或盘点差异生成)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建调整单
     */
    StockAdjustDocVO create(StockAdjustCreateDTO dto, String username);

    /**
     * 调整单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<StockAdjustDocVO> list(StockAdjustQuery query);

    /**
     * 调整单详情(含行),不存在抛 404。
     *
     * @param id 调整单 ID
     * @return 调整单
     */
    StockAdjustDocVO get(long id);

    /**
     * 提交:draft/rejected → pending。
     *
     * @param id       调整单 ID
     * @param username 当前登录用户名
     * @return 更新后的调整单
     */
    StockAdjustDocVO submit(long id, String username);

    /**
     * 审批执行:pending → completed(同事务执行 gain 入库/loss 出库,失败整单回滚可重试)。
     *
     * @param id       调整单 ID
     * @param username 当前登录用户名
     * @return 更新后的调整单
     */
    StockAdjustDocVO approve(long id, String username);

    /**
     * 驳回:pending → rejected(原因必填)。
     *
     * @param id           调整单 ID
     * @param rejectReason 驳回原因
     * @param username     当前登录用户名
     * @return 更新后的调整单
     */
    StockAdjustDocVO reject(long id, String rejectReason, String username);

    /**
     * 作废:任意未执行状态 → voided。
     *
     * @param id       调整单 ID
     * @param username 当前登录用户名
     * @return 更新后的调整单
     */
    StockAdjustDocVO voidDoc(long id, String username);
}
