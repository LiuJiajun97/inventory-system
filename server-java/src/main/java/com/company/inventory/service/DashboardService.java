package com.company.inventory.service;

import com.company.inventory.model.vo.dashboard.DashboardVO;





/**
 * 总览服务接口。
 *
 * @author inventory
 */
public interface DashboardService {

    /**
     * 总览统计(仓库/物品/库存行数 + 当日出入库单数与数量)。
     *
     * @return 总览数据
     */
    DashboardVO summary();
}
