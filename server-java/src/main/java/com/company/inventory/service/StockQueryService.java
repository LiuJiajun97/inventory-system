package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.query.StockQuery;
import com.company.inventory.query.TransactionQuery;
import com.company.inventory.vo.stock.StockVO;
import com.company.inventory.vo.stock.TransactionVO;


















/**
 * 库存查询服务接口。
 *
 * @author inventory
 */
public interface StockQueryService {

    /**
     * 库存分页查询(quantity 为 0 的行不返回,按仓库/物品升序)。
     *
     * @param query 查询条件(warehouseId/itemKeyword/batchNo/page/pageSize)
     * @return 分页结果
     */
    PageResult<StockVO> queryStock(StockQuery query);

    /**
     * 库存流水分页查询(id 降序)。
     *
     * @param query 查询条件(warehouseId/itemId/from/to/bizCode/page/pageSize)
     * @return 分页结果
     */
    PageResult<TransactionVO> queryTransactions(TransactionQuery query);
}
