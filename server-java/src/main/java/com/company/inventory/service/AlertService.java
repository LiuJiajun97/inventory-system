package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.query.ExpiryAlertQuery;
import com.company.inventory.model.query.LowStockQuery;
import com.company.inventory.model.vo.alert.ExpiryAlertVO;
import com.company.inventory.model.vo.alert.LowStockVO;

/**
 * 预警查询服务:临期(到期日 N 天内)+ 低库存(全仓可用 < minStock),仅查询聚合不做推送。
 *
 * @author inventory
 */
public interface AlertService {

    /**
     * 临期预警分页列表。
     *
     * @param query 查询条件(warehouseId/itemId/page/pageSize)
     * @return 分页结果
     */
    PageResult<ExpiryAlertVO> expiry(ExpiryAlertQuery query);

    /**
     * 低库存预警分页列表。
     *
     * @param query 查询条件(itemId/itemKeyword/page/pageSize)
     * @return 分页结果
     */
    PageResult<LowStockVO> lowStock(LowStockQuery query);
}
