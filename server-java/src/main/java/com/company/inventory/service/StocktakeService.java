package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.stocktake.StocktakeActualDTO;
import com.company.inventory.model.dto.stocktake.StocktakeCreateDTO;
import com.company.inventory.model.query.StocktakeDocQuery;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;
import com.company.inventory.model.vo.stocktake.StocktakeDocVO;

import java.util.List;

/**
 * 盘点单服务:快照 bookQty + 实盘录入 + 刷新快照 + 差异生成调整单(方案 §5.4)。
 *
 * @author inventory
 */
public interface StocktakeService {

    /**
     * 新建盘点单(draft),保存即按当前余额生成 bookQty 快照行。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建盘点单
     */
    StocktakeDocVO create(StocktakeCreateDTO dto, String username);

    /**
     * 盘点单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<StocktakeDocVO> list(StocktakeDocQuery query);

    /**
     * 盘点单详情(含行),不存在抛 404。
     *
     * @param id 盘点单 ID
     * @return 盘点单
     */
    StocktakeDocVO get(long id);

    /**
     * 录入实盘(draft/pending 可录,actualQty 为空=取消实盘,保存时重算 diffQty)。
     *
     * @param id       盘点单 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的盘点单
     */
    StocktakeDocVO enterActual(long id, StocktakeActualDTO dto, String username);

    /**
     * 刷新快照(draft 可刷):重取当前余额 bookQty,新增余额行补快照,已录实盘不覆盖。
     *
     * @param id       盘点单 ID
     * @param username 当前登录用户名
     * @return 更新后的盘点单
     */
    StocktakeDocVO refreshBook(long id, String username);

    /**
     * 提交:draft/rejected → pending。
     *
     * @param id       盘点单 ID
     * @param username 当前登录用户名
     * @return 更新后的盘点单
     */
    StocktakeDocVO submit(long id, String username);

    /**
     * 审批通过:pending → approved。
     *
     * @param id       盘点单 ID
     * @param username 当前登录用户名
     * @return 更新后的盘点单
     */
    StocktakeDocVO approve(long id, String username);

    /**
     * 驳回:pending → rejected(原因必填)。
     *
     * @param id       盘点单 ID
     * @param rejectReason 驳回原因
     * @param username 当前登录用户名
     * @return 更新后的盘点单
     */
    StocktakeDocVO reject(long id, String rejectReason, String username);

    /**
     * 作废:任意未执行状态 → voided。
     *
     * @param id       盘点单 ID
     * @param username 当前登录用户名
     * @return 更新后的盘点单
     */
    StocktakeDocVO voidDoc(long id, String username);

    /**
     * 差异生成调整单:有差异行按盘盈 gain/盘亏 loss 各生成一张草稿调整单(refDocNo=盘点单号)。
     *
     * @param id       盘点单 ID
     * @param username 当前登录用户名
     * @return 生成的调整单列表(0/1/2 张)
     */
    List<StockAdjustDocVO> generateAdjust(long id, String username);
}
