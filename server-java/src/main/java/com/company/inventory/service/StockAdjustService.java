package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.adjust.StockAdjustCreateDTO;
import com.company.inventory.model.query.StockAdjustQuery;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;

/**
 * 库存调整单服务:gain 调整入库/loss 与 scrap 调整出库,审批通过时执行库存动作并 completed。
 *
 * @author inventory
 */
public interface StockAdjustService {

    /**
     * 新建调整单(draft,手工建单;来源单号由服务端盘点差异生成写入,此处恒为空)。
     *
     * @param dto      入参(refDocNo 会被忽略)
     * @param username 当前登录用户名
     * @return 新建调整单
     */
    StockAdjustDocVO create(StockAdjustCreateDTO dto, String username);

    /**
     * 新建带来源单号的调整单(盘点差异生成专用,refDocNo 落库并参与防重复唯一约束)。
     *
     * @param dto        入参
     * @param refDocNo   来源盘点单号
     * @param username   当前登录用户名
     * @return 新建调整单
     */
    StockAdjustDocVO createWithRef(StockAdjustCreateDTO dto, String refDocNo, String username);

    /**
     * 编辑调整单(仅 draft/rejected 可编辑,已驳回编辑后回 draft 并清空驳回原因)。
     *
     * @param id       调整单 ID
     * @param dto      入参(与新建同结构,行明细全量替换)
     * @param username 当前登录用户名
     * @return 更新后的调整单
     * @throws BizException 调整单不存在、状态不可编辑或入参校验失败时抛出
     */
    StockAdjustDocVO update(long id, StockAdjustCreateDTO dto, String username);

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
