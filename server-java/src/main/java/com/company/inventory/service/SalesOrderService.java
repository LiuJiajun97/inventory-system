package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.sales.SalesActionDTO;
import com.company.inventory.dto.sales.SalesOrderCreateDTO;
import com.company.inventory.dto.sales.ShipLine;
import com.company.inventory.query.SalesOrderQuery;
import com.company.inventory.vo.sales.SalesOrderItemVO;
import com.company.inventory.vo.sales.SalesOrderVO;

import java.util.List;
import java.util.Map;

/**
 * 销售订单服务:状态机(方案 §4)+ 审批 FEFO 预占防超卖 + 发货回写 + 关闭/作废释放预占。
 *
 * @author inventory
 */
public interface SalesOrderService {

    /**
     * 新建销售订单(draft),保存时服务端重算行金额与表头三合计。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 新建订单
     */
    SalesOrderVO create(SalesOrderCreateDTO dto, String username);

    /**
     * 销售订单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesOrderVO> list(SalesOrderQuery query);

    /**
     * 订单详情(含行),不存在抛 404。
     *
     * @param id 订单 ID
     * @return 订单
     */
    SalesOrderVO get(long id);

    /**
     * 编辑订单(仅 draft/rejected;rejected 编辑后回 draft),重算金额。
     *
     * @param id       订单 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    SalesOrderVO update(long id, SalesOrderCreateDTO dto, String username);

    /**
     * 提交:draft/rejected → pending。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    SalesOrderVO submit(long id, String username);

    /**
     * 审批通过:pending → approved,审批时逐行 FEFO 预占(可用不足整单失败,订单回 draft)。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    SalesOrderVO approve(long id, String username);

    /**
     * 驳回:pending → rejected(原因必填)。
     *
     * @param id       订单 ID
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    SalesOrderVO reject(long id, SalesActionDTO dto, String username);

    /**
     * 作废:任意未执行状态 → voided;已审批订单先释放未发货部分预占。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    SalesOrderVO voidDoc(long id, String username);

    /**
     * 手工关闭:approved → closed,释放未发货部分预占。
     *
     * @param id       订单 ID
     * @param username 当前登录用户名
     * @return 更新后的订单
     */
    SalesOrderVO close(long id, String username);

    /**
     * 销售发货回写(出库单关联销售订单时调用,同事务):逐行条件 UPDATE 累加 shippedQty
     * (超发拒绝并提示行号),行达标置 closed,全行 closed 自动 completed。
     *
     * @param orderId 订单 ID
     * @param lines   发货行(订单行 ID + 实发数量)
     */
    void applyShipment(long orderId, List<ShipLine> lines);

    /**
     * 校验订单已审批并返回行(出库单关联销售订单时调用)。
     *
     * @param orderId 订单 ID
     * @return 行 ID → 行 VO
     */
    Map<Long, SalesOrderItemVO> requireApprovedItems(long orderId);

    /**
     * 订单发货仓库 ID(出库单仓库必须与之一致)。
     *
     * @param orderId 订单 ID
     * @return 仓库 ID
     */
    long requireWarehouseId(long orderId);
}
