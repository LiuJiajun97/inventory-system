package com.company.inventory.service.impl;

import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.entity.inbound.InboundDocDO;
import com.company.inventory.entity.inbound.InboundDocItemDO;
import com.company.inventory.entity.outbound.OutboundDocDO;
import com.company.inventory.entity.outbound.OutboundDocItemDO;
import com.company.inventory.mapper.InboundDocItemMapper;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.OutboundDocItemMapper;
import com.company.inventory.mapper.OutboundDocMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.service.DashboardService;
import com.company.inventory.vo.dashboard.DashboardVO;





























import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;














import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 总览服务实现(口径与 Fastify 版一致:当日 = [00:00:00.000, 23:59:59.999) 本地时间)。
 *
 * @author inventory
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 当日结束时刻(统计窗口上界:23:59:59 起再叠加 nano)。 */
    private static final LocalTime END_DAY_TIME = LocalTime.of(23, 59, 59);

    /** 当日结束时刻的纳秒部分(23:59:59.999,与 Fastify 版 end.setHours(23,59,59,999) 对齐)。 */
    private static final int END_DAY_NANO = 999_000_000;

    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMapper;
    /** 库存 Mapper。 */
    private final StockMapper stockMapper;
    /** 入库单 Mapper。 */
    private final InboundDocMapper inboundDocMapper;
    /** 入库单行 Mapper。 */
    private final InboundDocItemMapper inboundDocItemMapper;
    /** 出库单 Mapper。 */
    private final OutboundDocMapper outboundDocMapper;
    /** 出库单行 Mapper。 */
    private final OutboundDocItemMapper outboundDocItemMapper;

    /**
     * 构造服务。
     *
     * @param warehouseMapper       仓库 Mapper
     * @param itemMapper            物品 Mapper
     * @param stockMapper           库存 Mapper
     * @param inboundDocMapper      入库单 Mapper
     * @param inboundDocItemMapper  入库单行 Mapper
     * @param outboundDocMapper     出库单 Mapper
     * @param outboundDocItemMapper 出库单行 Mapper
     */
    public DashboardServiceImpl(WarehouseMapper warehouseMapper, ItemMapper itemMapper,
                                StockMapper stockMapper, InboundDocMapper inboundDocMapper,
                                InboundDocItemMapper inboundDocItemMapper,
                                OutboundDocMapper outboundDocMapper,
                                OutboundDocItemMapper outboundDocItemMapper) {
        this.warehouseMapper = warehouseMapper;
        this.itemMapper = itemMapper;
        this.stockMapper = stockMapper;
        this.inboundDocMapper = inboundDocMapper;
        this.inboundDocItemMapper = inboundDocItemMapper;
        this.outboundDocMapper = outboundDocMapper;
        this.outboundDocItemMapper = outboundDocItemMapper;
    }

    /**
     * 总览统计。
     *
     * @return 总览数据
     */
    @Override
    public DashboardVO summary() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(END_DAY_TIME).withNano(END_DAY_NANO);

        long warehouseCount = warehouseMapper.selectCount(null);
        long itemCount = itemMapper.selectCount(null);
        long stockLineCount = stockMapper.selectCount(null);

        List<InboundDocDO> todayInbound = inboundDocMapper.selectList(
                new LambdaQueryWrapper<InboundDocDO>()
                        .ge(InboundDocDO::getCreatedAt, start)
                        .lt(InboundDocDO::getCreatedAt, end));
        List<OutboundDocDO> todayOutbound = outboundDocMapper.selectList(
                new LambdaQueryWrapper<OutboundDocDO>()
                        .ge(OutboundDocDO::getCreatedAt, start)
                        .lt(OutboundDocDO::getCreatedAt, end));

        BigDecimal inboundQty = sumQty(inboundQtyOf(todayInbound));
        BigDecimal outboundQty = sumQty(outboundQtyOf(todayOutbound));

        return new DashboardVO(warehouseCount, itemCount, stockLineCount,
                todayInbound.size(), inboundQty, todayOutbound.size(), outboundQty);
    }

    /**
     * 汇总入库单行数量。
     *
     * @param docs 当日入库单
     * @return 各单行数量列表(单据无行则为空)
     */
    private List<BigDecimal> inboundQtyOf(List<InboundDocDO> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Set<Long> docIds = docs.stream().map(InboundDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        List<InboundDocItemDO> items = inboundDocItemMapper.selectList(
                new LambdaQueryWrapper<InboundDocItemDO>().in(InboundDocItemDO::getDocId, docIds));
        return items.stream().map(InboundDocItemDO::getQuantity).toList();
    }

    /**
     * 汇总出库单行数量。
     *
     * @param docs 当日出库单
     * @return 各单行数量列表(单据无行则为空)
     */
    private List<BigDecimal> outboundQtyOf(List<OutboundDocDO> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Set<Long> docIds = docs.stream().map(OutboundDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        List<OutboundDocItemDO> items = outboundDocItemMapper.selectList(
                new LambdaQueryWrapper<OutboundDocItemDO>().in(OutboundDocItemDO::getDocId, docIds));
        return items.stream().map(OutboundDocItemDO::getQuantity).toList();
    }

    /**
     * 数量求和并归一化(去尾零,保证 JSON number 契约)。
     *
     * @param qtys 数量列表
     * @return 求和后的归一化值
     */
    private BigDecimal sumQty(List<BigDecimal> qtys) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal qty : qtys) {
            sum = sum.add(qty);
        }
        return QtyUtils.normalize(sum);
    }
}
