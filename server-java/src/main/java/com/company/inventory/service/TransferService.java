package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.transfer.TransferActionDTO;
import com.company.inventory.model.dto.transfer.TransferCreateDTO;
import com.company.inventory.model.query.TransferDocQuery;
import com.company.inventory.model.vo.transfer.TransferDocVO;

/**
 * 调拨单服务:状态机 + 审批执行(同事务源扣目的加,拍板点 1 原子一步)。
 *
 * @author inventory
 */
public interface TransferService {

    /**
     * 新建调拨单(draft),表头合计服务端重算。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建调拨单
     */
    TransferDocVO create(TransferCreateDTO dto, String username);

    /**
     * 调拨单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TransferDocVO> list(TransferDocQuery query);

    /**
     * 调拨单详情(含行),不存在抛 404。
     *
     * @param id 调拨单 ID
     * @return 调拨单
     */
    TransferDocVO get(long id);

    /**
     * 编辑调拨单(仅 draft/rejected;rejected 编辑后回 draft)。
     *
     * @param id       调拨单 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的调拨单
     */
    TransferDocVO update(long id, TransferCreateDTO dto, String username);

    /**
     * 提交:draft/rejected → pending。
     *
     * @param id       调拨单 ID
     * @param username 当前登录用户名
     * @return 更新后的调拨单
     */
    TransferDocVO submit(long id, String username);

    /**
     * 审批执行:pending → completed(同一事务源仓扣减 + 目的仓入库,批次跟随源批;
     * 源仓可用量不足任一行整单回滚,单据保持可重试)。
     *
     * @param id       调拨单 ID
     * @param username 当前登录用户名
     * @return 更新后的调拨单
     */
    TransferDocVO approve(long id, String username);

    /**
     * 驳回:pending → rejected(原因必填)。
     *
     * @param id       调拨单 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的调拨单
     */
    TransferDocVO reject(long id, TransferActionDTO dto, String username);

    /**
     * 作废:任意未执行状态 → voided。
     *
     * @param id       调拨单 ID
     * @param username 当前登录用户名
     * @return 更新后的调拨单
     */
    TransferDocVO voidDoc(long id, String username);
}
