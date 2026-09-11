package com.company.inventory.service.impl;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.mapper.AlertMapper;
import com.company.inventory.query.ExpiryAlertQuery;
import com.company.inventory.query.LowStockQuery;
import com.company.inventory.service.AlertService;
import com.company.inventory.vo.alert.ExpiryAlertRow;
import com.company.inventory.vo.alert.ExpiryAlertVO;
import com.company.inventory.vo.alert.LowStockRow;
import com.company.inventory.vo.alert.LowStockVO;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 预警查询服务实现:临期 N 天默认 30(全局常量,一期不做配置化)+ 低库存按全仓可用量聚合。
 *
 * @author inventory
 */
@Service
public class AlertServiceImpl implements AlertService {

    /** 临期预警天数阈值(全局配置,一期常量)。 */
    private static final int EXPIRY_ALERT_DAYS = 30;

    /** 预警 Mapper。 */
    private final AlertMapper alertMapper;

    /**
     * 构造服务。
     *
     * @param alertMapper 预警 Mapper
     */
    public AlertServiceImpl(AlertMapper alertMapper) {
        this.alertMapper = alertMapper;
    }

    /**
     * 临期预警分页列表(到期日 ≤ today+30 天,按到期日升序)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<ExpiryAlertVO> expiry(ExpiryAlertQuery query) {
        IPage<ExpiryAlertRow> page = alertMapper.selectExpiryAlerts(
                Page.of(query.getPage(), query.getPageSize()), query, EXPIRY_ALERT_DAYS);
        List<ExpiryAlertVO> rows = page.getRecords().stream().map(row ->
                new ExpiryAlertVO(row.itemId(), row.itemCode(), row.itemName(), row.unit(),
                        row.batchNo(), row.warehouseId(), row.warehouseName(),
                        row.productionDate(), row.expiryDate(), row.daysLeft(),
                        QtyUtils.toContractString(row.quantity()),
                        QtyUtils.toContractString(row.availableQty())))
                .collect(Collectors.toList());
        return PageResult.of(rows, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 低库存预警分页列表(minStock 非空且全仓可用 < minStock)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<LowStockVO> lowStock(LowStockQuery query) {
        IPage<LowStockRow> page = alertMapper.selectLowStock(
                Page.of(query.getPage(), query.getPageSize()), query);
        List<LowStockVO> rows = page.getRecords().stream().map(row ->
                new LowStockVO(row.itemId(), row.itemCode(), row.itemName(), row.unit(),
                        row.minStock(), QtyUtils.toContractString(row.totalQty()),
                        QtyUtils.toContractString(row.availableQty())))
                .collect(Collectors.toList());
        return PageResult.of(rows, page.getTotal(), query.getPage(), query.getPageSize());
    }
}
