package com.company.inventory.service;

import com.company.inventory.common.constant.SettlementConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.company.inventory.model.entity.settlement.InvoiceDO;
import com.company.inventory.model.entity.settlement.PaymentDocDO;
import com.company.inventory.mapper.InvoiceMapper;
import com.company.inventory.mapper.PaymentDocMapper;
import com.company.inventory.model.entity.adjust.StockAdjustDocDO;
import com.company.inventory.model.entity.inbound.InboundDocDO;
import com.company.inventory.model.entity.outbound.OutboundDocDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.entity.stocktake.StocktakeDocDO;
import com.company.inventory.model.entity.transfer.TransferDocDO;
import com.company.inventory.model.entity.returns.PurchaseReturnDO;
import com.company.inventory.model.entity.returns.SalesReturnDO;
import com.company.inventory.model.entity.opening.OpeningStockDocDO;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.OutboundDocMapper;
import com.company.inventory.mapper.StockAdjustDocMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.StocktakeDocMapper;
import com.company.inventory.mapper.TransferDocMapper;
import com.company.inventory.mapper.PurchaseReturnMapper;
import com.company.inventory.mapper.SalesReturnMapper;
import com.company.inventory.mapper.OpeningStockDocMapper;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 单据编号生成服务:RK/CK(出入库)、CG(采购)、XS(销售)、DB(调拨)、PD(盘点)、TZ(调整)、
 * CT(采购退货)、XT(销售退货)、QC(期初)均为 PREFIX-YYYYMMDD-NNNN 按天序列。
 *
 * <p>序号取"当日已用最大值 + 1"(按单号前缀 LIKE 匹配),而非"当日计数 + 1":
 * 当天单据被物理删除后计数回退会重号撞唯一约束,最大值天然抗删除。</p>
 *
 * <p>注意:须在写单据的同一事务内调用,避免并发重号(重号由唯一约束兜底报错)。</p>
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

    /** 采购退货单号前缀。 */
    private static final String PREFIX_PURCHASE_RETURN = "CT";

    /** 销售退货单号前缀。 */
    private static final String PREFIX_SALES_RETURN = "XT";

    /** 期初单号前缀。 */
    private static final String PREFIX_OPENING = "QC";

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
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final SalesReturnMapper salesReturnMapper;
    private final OpeningStockDocMapper openingStockDocMapper;
    /** 发票 Mapper(发票号 FP-)。 */
    private final InvoiceMapper invoiceMapper;
    /** 付款/收款单 Mapper(FK-/SK-)。 */
    private final PaymentDocMapper paymentDocMapper;

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
     * @param purchaseReturnMapper 采购退货单 Mapper
     * @param salesReturnMapper    销售退货单 Mapper
     * @param openingStockDocMapper 期初单 Mapper
     * @param invoiceMapper    发票 Mapper
     * @param paymentDocMapper 付款/收款单 Mapper
     */
    public DocNoService(InboundDocMapper inboundDocMapper, OutboundDocMapper outboundDocMapper,
            PurchaseOrderMapper purchaseOrderMapper, SalesOrderMapper salesOrderMapper,
            TransferDocMapper transferDocMapper, StocktakeDocMapper stocktakeDocMapper,
            StockAdjustDocMapper stockAdjustDocMapper, PurchaseReturnMapper purchaseReturnMapper,
            SalesReturnMapper salesReturnMapper, OpeningStockDocMapper openingStockDocMapper,
            InvoiceMapper invoiceMapper, PaymentDocMapper paymentDocMapper) {
        this.inboundDocMapper = inboundDocMapper;
        this.outboundDocMapper = outboundDocMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.transferDocMapper = transferDocMapper;
        this.stocktakeDocMapper = stocktakeDocMapper;
        this.stockAdjustDocMapper = stockAdjustDocMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.salesReturnMapper = salesReturnMapper;
        this.openingStockDocMapper = openingStockDocMapper;
        this.invoiceMapper = invoiceMapper;
        this.paymentDocMapper = paymentDocMapper;
    }

    /**
     * 生成入库单号(RK-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateInboundDocNo() {
        return buildNo(PREFIX_INBOUND, inboundDocMapper, InboundDocDO::getDocNo);
    }

    /**
     * 生成出库单号(CK-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateOutboundDocNo() {
        return buildNo(PREFIX_OUTBOUND, outboundDocMapper, OutboundDocDO::getDocNo);
    }

    /**
     * 生成采购订单号(CG-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generatePurchaseOrderNo() {
        return buildNo(PREFIX_PURCHASE, purchaseOrderMapper, PurchaseOrderDO::getDocNo);
    }

    /**
     * 生成销售订单号(XS-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateSalesOrderNo() {
        return buildNo(PREFIX_SALES, salesOrderMapper, SalesOrderDO::getDocNo);
    }

    /**
     * 生成调拨单号(DB-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateTransferDocNo() {
        return buildNo(PREFIX_TRANSFER, transferDocMapper, TransferDocDO::getDocNo);
    }

    /**
     * 生成盘点单号(PD-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateStocktakeDocNo() {
        return buildNo(PREFIX_STOCKTAKE, stocktakeDocMapper, StocktakeDocDO::getDocNo);
    }

    /**
     * 生成调整单号(TZ-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateAdjustDocNo() {
        return buildNo(PREFIX_ADJUST, stockAdjustDocMapper, StockAdjustDocDO::getDocNo);
    }

    /**
     * 生成采购退货单号(CT-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generatePurchaseReturnNo() {
        return buildNo(PREFIX_PURCHASE_RETURN, purchaseReturnMapper, PurchaseReturnDO::getDocNo);
    }

    /**
     * 生成销售退货单号(XT-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateSalesReturnNo() {
        return buildNo(PREFIX_SALES_RETURN, salesReturnMapper, SalesReturnDO::getDocNo);
    }

    /**
     * 生成期初单号(QC-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateOpeningDocNo() {
        return buildNo(PREFIX_OPENING, openingStockDocMapper, OpeningStockDocDO::getDocNo);
    }

    /**
     * 生成发票号(FP-YYYYMMDD-NNNN,采购票/销售票/红字凭单共用)。
     *
     * @return 发票号
     */
    public String generateInvoiceNo() {
        return buildNo(SettlementConstants.PREFIX_INVOICE, invoiceMapper, InvoiceDO::getDocNo);
    }

    /**
     * 生成付款单号(FK-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generatePaymentDocNo() {
        return buildNo(SettlementConstants.PREFIX_PAYMENT, paymentDocMapper, PaymentDocDO::getDocNo);
    }

    /**
     * 生成收款单号(SK-YYYYMMDD-NNNN)。
     *
     * @return 单号
     */
    public String generateReceiptDocNo() {
        return buildNo(SettlementConstants.PREFIX_RECEIPT, paymentDocMapper, PaymentDocDO::getDocNo);
    }

    /**
     * 组装单号:前缀-当日日期-序号(序号 = 当日已用最大值 + 1)。
     *
     * @param prefix 单号前缀
     * @param mapper 单据表 Mapper
     * @param docCol 单号列
     * @param <T>    单据实体类型
     * @return 单号
     */
    private <T> String buildNo(String prefix, BaseMapper<T> mapper, SFunction<T, String> docCol) {
        String head = prefix + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        long max = maxSeqOfDay(mapper, docCol, head);
        return head + "-" + padSeq(max + 1);
    }

    /**
     * 取某单据表当日已用最大序号(按"前缀-日期-"前缀 LIKE 匹配,取末 4 位最大值)。
     *
     * @param mapper 单据表 Mapper
     * @param docCol 单号列
     * @param head   前缀-日期文本(如 CK-20260913)
     * @param <T>    单据实体类型
     * @return 当日已用最大序号(无单据返回 0)
     */
    private <T> long maxSeqOfDay(BaseMapper<T> mapper, SFunction<T, String> docCol, String head) {
        List<T> rows = mapper.selectList(new LambdaQueryWrapper<T>().select(docCol).likeRight(docCol, head + "-"));
        long max = 0L;
        for (T row : rows) {
            String no = docCol.apply(row);
            String seqPart = no.substring(no.lastIndexOf('-') + 1);
            try {
                max = Math.max(max, Long.parseLong(seqPart));
            } catch (NumberFormatException ignored) {
                // 非标准单号忽略
            }
        }
        return max;
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
