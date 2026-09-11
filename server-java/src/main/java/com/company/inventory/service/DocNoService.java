package com.company.inventory.service;

import com.company.inventory.entity.adjust.StockAdjustDocDO;
import com.company.inventory.entity.inbound.InboundDocDO;
import com.company.inventory.entity.outbound.OutboundDocDO;
import com.company.inventory.entity.purchase.PurchaseOrderDO;
import com.company.inventory.entity.sales.SalesOrderDO;
import com.company.inventory.entity.stocktake.StocktakeDocDO;
import com.company.inventory.entity.transfer.TransferDocDO;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.OutboundDocMapper;
import com.company.inventory.mapper.StockAdjustDocMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.StocktakeDocMapper;
import com.company.inventory.mapper.TransferDocMapper;











import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;




import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单据编号生成服务:RK/CK(出入库)、CG(采购)、XS(销售)、DB(调拨)、PD(盘点)、TZ(调整)
 * 均为 PREFIX-YYYYMMDD-NNNN 按天序列。
 *
 * <p>规则与 Fastify 版一致:查当日单据数 + 1,补零 4 位;须在写单据的同一事务内调用。</p>
 *
 * @author inventory
 */
@Service
public class DocNoService {

    /** 入库单号前缀。 */
    private static final String PREFIX_INBOUND = "RK";

    /** 出库单号前缀。 */
    private static final String PREFIX_OUTBOUND = "CK";

    /** 采购订单号前缀。 */
    private static final String PREFIX_PURCHASE = "CG";

    /** 销售订单号前缀。 */
    private static final String PREFIX_SALES = "XS";

    /** 调拨单号前缀。 */
    private static final String PREFIX_TRANSFER = "DB";

    /** 盘点单号前缀。 */
    private static final String PREFIX_STOCKTAKE = "PD";

    /** 调整单号前缀。 */
    private static final String PREFIX_ADJUST = "TZ";

    /** 日期格式 yyyyMMdd。 */
    private static final String DATE_FORMAT = "yyyyMMdd";

    /** 序号补零位数。 */
    private static final int SEQ_WIDTH = 4;

    private final InboundDocMapper inboundDocMapper;
    private final OutboundDocMapper outboundDocMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final TransferDocMapper transferDocMapper;
    private final StocktakeDocMapper stocktakeDocMapper;
    private final StockAdjustDocMapper stockAdjustDocMapper;

    /**
     * 构造服务。
     *
     * @param inboundDocMapper    入库单 Mapper
     * @param outboundDocMapper   出库单 Mapper
     * @param purchaseOrderMapper 采购订单 Mapper
     * @param salesOrderMapper    销售订单 Mapper
     * @param transferDocMapper   调拨单 Mapper
     * @param stocktakeDocMapper  盘点单 Mapper
     * @param stockAdjustDocMapper 调整单 Mapper
     */
    public DocNoService(InboundDocMapper inboundDocMapper, OutboundDocMapper outboundDocMapper,
            PurchaseOrderMapper purchaseOrderMapper, SalesOrderMapper salesOrderMapper,
            TransferDocMapper transferDocMapper, StocktakeDocMapper stocktakeDocMapper,
            StockAdjustDocMapper stockAdjustDocMapper) {
        this.inboundDocMapper = inboundDocMapper;
        this.outboundDocMapper = outboundDocMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.transferDocMapper = transferDocMapper;
        this.stocktakeDocMapper = stocktakeDocMapper;
        this.stockAdjustDocMapper = stockAdjustDocMapper;
    }

    /**
     * 生成入库单号(RK-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateInboundDocNo() {
        long count = inboundDocMapper.selectCount(new LambdaQueryWrapper<InboundDocDO>()
                .ge(InboundDocDO::getCreatedAt, todayStart())
                .lt(InboundDocDO::getCreatedAt, todayEnd()));
        return PREFIX_INBOUND + "-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(DATE_FORMAT))
                + "-" + padSeq(count + 1);
    }

    /**
     * 生成出库单号(CK-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateOutboundDocNo() {
        long count = outboundDocMapper.selectCount(new LambdaQueryWrapper<OutboundDocDO>()
                .ge(OutboundDocDO::getCreatedAt, todayStart())
                .lt(OutboundDocDO::getCreatedAt, todayEnd()));
        return PREFIX_OUTBOUND + "-" + todayText() + "-" + padSeq(count + 1);
    }

    /**
     * 生成采购订单号(CG-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generatePurchaseOrderNo() {
        long count = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrderDO>()
                .ge(PurchaseOrderDO::getCreatedAt, todayStart())
                .lt(PurchaseOrderDO::getCreatedAt, todayEnd()));
        return PREFIX_PURCHASE + "-" + todayText() + "-" + padSeq(count + 1);
    }

    /**
     * 生成销售订单号(XS-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateSalesOrderNo() {
        long count = salesOrderMapper.selectCount(new LambdaQueryWrapper<SalesOrderDO>()
                .ge(SalesOrderDO::getCreatedAt, todayStart())
                .lt(SalesOrderDO::getCreatedAt, todayEnd()));
        return PREFIX_SALES + "-" + todayText() + "-" + padSeq(count + 1);
    }

    /**
     * 生成调拨单号(DB-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateTransferDocNo() {
        long count = transferDocMapper.selectCount(new LambdaQueryWrapper<TransferDocDO>()
                .ge(TransferDocDO::getCreatedAt, todayStart())
                .lt(TransferDocDO::getCreatedAt, todayEnd()));
        return PREFIX_TRANSFER + "-" + todayText() + "-" + padSeq(count + 1);
    }

    /**
     * 生成盘点单号(PD-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateStocktakeDocNo() {
        long count = stocktakeDocMapper.selectCount(new LambdaQueryWrapper<StocktakeDocDO>()
                .ge(StocktakeDocDO::getCreatedAt, todayStart())
                .lt(StocktakeDocDO::getCreatedAt, todayEnd()));
        return PREFIX_STOCKTAKE + "-" + todayText() + "-" + padSeq(count + 1);
    }

    /**
     * 生成调整单号(TZ-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateAdjustDocNo() {
        long count = stockAdjustDocMapper.selectCount(new LambdaQueryWrapper<StockAdjustDocDO>()
                .ge(StockAdjustDocDO::getCreatedAt, todayStart())
                .lt(StockAdjustDocDO::getCreatedAt, todayEnd()));
        return PREFIX_ADJUST + "-" + todayText() + "-" + padSeq(count + 1);
    }

    /**
     * 当日日期文本(yyyyMMdd)。
     *
     * @return 日期文本
     */
    private String todayText() {
        return LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern(DATE_FORMAT));
    }

    /**
     * 当日 0 点。
     *
     * @return 当日开始时间
     */
    private LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }

    /**
     * 次日 0 点(当日范围右开区间)。
     *
     * @return 次日开始时间
     */
    private LocalDateTime todayEnd() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    /**
     * 序号补零。
     *
     * @param seq 序号
     * @return 补零后的序号字符串
     */
    private String padSeq(long seq) {
        return String.format("%0" + SEQ_WIDTH + "d", seq);
    }
}
