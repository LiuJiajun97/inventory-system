package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.returns.SalesReturnCreateDTO;
import com.company.inventory.model.query.SalesReturnQuery;
import com.company.inventory.model.vo.returns.SalesReturnCreatedVO;
import com.company.inventory.model.vo.returns.SalesReturnVO;

/**
 * 销售退货单服务:create 即过账(单事务生成入库单走现有过账链路,回写原单行已退累计)。
 *
 * @author inventory
 */
public interface SalesReturnService {

    /**
     * 新建销售退货单并过账:校验原单已审批、逐行可退量(已发货-已退),
     * 锁原行快照价,同事务生成入库单(库存增加)并回写原销售订单行 returned_qty
     * (不改 shipped_qty,净发货=shipped-returned),任一失败整体回滚。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 创建结果(含联动入库单号)
     */
    SalesReturnCreatedVO create(SalesReturnCreateDTO dto, String username);

    /**
     * 销售退货单分页列表(数据权限:按退货入库仓过滤,admin 豁免,未授权查空)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesReturnVO> list(SalesReturnQuery query);

    /**
     * 销售退货单详情(含行),不存在抛 404。
     *
     * @param id 单据 ID
     * @return 单据
     */
    SalesReturnVO get(long id);
}
