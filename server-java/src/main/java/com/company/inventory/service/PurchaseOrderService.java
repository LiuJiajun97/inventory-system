package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.purchase.PurchaseActionDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.dto.purchase.ArrivalLine;
import com.company.inventory.model.query.PurchaseOrderQuery;
import com.company.inventory.model.vo.purchase.PurchaseOrderItemVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;

import java.util.List;
import java.util.Map;

/**
 * 采购订单服务:状态机(方案 §4)+ 部分到货回写 + 超收控制 + 自动关闭。
 *
 * @author inventory
 */
public interface PurchaseOrderService {

    /**
     * 新建采购订单(draft),保存时服务端重算行金额与表头三合计。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建订单
     */
    PurchaseOrderVO create(PurchaseOrderCreateDTO dto, String username);

    /**
     * 采购订单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PurchaseOrderVO> list(PurchaseOrderQuery query);

    /**
     * 订单详情(含行),不存在抛 404。
     *
     * @param id 订单 ID
     * @return 订单
     */
    PurchaseOrderVO get(long id);

    /**
     * 编辑订单(仅 draft/rejected;rejected 编辑后回 draft),重算金额。
     *
     * @param id       订单 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    PurchaseOrderVO update(long id, PurchaseOrderCreateDTO dto, String username);

    /**
     * 提交:draft/rejected → pending。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    PurchaseOrderVO submit(long id, String username);

    /**
     * 审批通过:pending → approved(审批人≠制单人;已 approved 幂等返回)。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    PurchaseOrderVO approve(long id, String username);

    /**
     * 驳回:pending → rejected(驳回原因必填,审批人≠制单人)。
     *
     * @param id       订单 ID
     * @param dto      入参(rejectReason)
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    PurchaseOrderVO reject(long id, PurchaseActionDTO dto, String username);

    /**
     * 作废:任意未执行状态 → voided(终态)。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    PurchaseOrderVO voidDoc(long id, String username);

    /**
     * 手工关闭:approved → closed(剩余未交量作废,不产生库存动作)。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    PurchaseOrderVO close(long id, String username);

    /**
     * 采购到货回写(入库单关联采购订单时调用,同事务):
     * 逐行条件 UPDATE 累加 arrivedQty(超收拒绝并提示行号),行累计达标置 closed,
     * 全部行 closed 则订单自动 completed。
     *
     * @param orderId 订单 ID
     * @param lines   到货行(订单行 ID + 实收数量)
     */
    void applyArrival(long orderId, List<ArrivalLine> lines);

    /**
     * 校验订单已审批并返回行(入库单关联采购订单时调用)。
     *
     * @param orderId 订单 ID
     * @return 行 ID → 行 VO
     */
    Map<Long, PurchaseOrderItemVO> requireApprovedItems(long orderId);
}
