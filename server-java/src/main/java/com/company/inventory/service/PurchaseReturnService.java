package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.returns.PurchaseReturnCreateDTO;
import com.company.inventory.model.query.PurchaseReturnQuery;
import com.company.inventory.model.vo.returns.PurchaseReturnCreatedVO;
import com.company.inventory.model.vo.returns.PurchaseReturnVO;

/**
 * 采购退货单服务:create 即过账(单事务生成出库单走现有过账链路,回写原单行已退累计)。
 *
 * @author inventory
 */
public interface PurchaseReturnService {

    /**
     * 新建采购退货单并过账:校验原单已审批、逐行可退量,锁原行快照价,
     * 同事务生成出库单(库存扣减)并回写原采购订单行 returned_qty,任一失败整体回滚。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 创建结果(含联动出库单号)
     */
    PurchaseReturnCreatedVO create(PurchaseReturnCreateDTO dto, String username);

    /**
     * 采购退货单分页列表(数据权限:按退货仓过滤,admin 豁免,未授权查空)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PurchaseReturnVO> list(PurchaseReturnQuery query);

    /**
     * 采购退货单详情(含行),不存在抛 404。
     *
     * @param id 单据 ID
     * @return 单据
     */
    PurchaseReturnVO get(long id);
}
