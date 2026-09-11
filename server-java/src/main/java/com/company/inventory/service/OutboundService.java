package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.outbound.OutboundCreateDTO;
import com.company.inventory.query.OutboundDocQuery;
import com.company.inventory.vo.outbound.OutboundDocCreatedVO;
import com.company.inventory.vo.outbound.OutboundDocVO;











/**
 * 出库单服务接口。
 *
 * @author inventory
 */
public interface OutboundService {

    /**
     * 新建出库单:整单一个事务,单据头 + 行 + 库存扣减 + 序列号同事务。
     *
     * @param dto      入参
     * @param username 当前登录用户名(记为 creator/operator)
     * @return 单据头
     */
    OutboundDocCreatedVO create(OutboundCreateDTO dto, String username);

    /**
     * 出库单分页列表(id 降序,含仓库与单据行)。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    PageResult<OutboundDocVO> list(OutboundDocQuery query);

    /**
     * 出库单详情,不存在抛 404。
     *
     * @param id 单据 ID
     * @return 单据(含仓库与单据行)
     */
    OutboundDocVO get(long id);
}
