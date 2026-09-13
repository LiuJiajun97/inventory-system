package com.company.inventory.service.impl;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.OpeningStockDocMapper;
import com.company.inventory.mapper.OutboundDocMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.PurchaseReturnMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.SalesReturnMapper;
import com.company.inventory.mapper.StockAdjustDocMapper;
import com.company.inventory.mapper.StocktakeDocMapper;
import com.company.inventory.mapper.TransferDocMapper;
import com.company.inventory.model.query.InboundDocLineQuery;
import com.company.inventory.model.query.OpeningStockDocLineQuery;
import com.company.inventory.model.query.OutboundDocLineQuery;
import com.company.inventory.model.query.PurchaseOrderLinesQuery;
import com.company.inventory.model.query.PurchaseReturnLinesQuery;
import com.company.inventory.model.query.SalesOrderLinesQuery;
import com.company.inventory.model.query.SalesReturnLinesQuery;
import com.company.inventory.model.query.StockAdjustDocLineQuery;
import com.company.inventory.model.query.StocktakeDocLineQuery;
import com.company.inventory.model.query.TransferDocLineQuery;
import com.company.inventory.model.vo.adjust.StockAdjustDocLineVO;
import com.company.inventory.model.vo.inbound.InboundDocLineVO;
import com.company.inventory.model.vo.opening.OpeningStockDocLineVO;
import com.company.inventory.model.vo.outbound.OutboundDocLineVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderLinesVO;
import com.company.inventory.model.vo.returns.PurchaseReturnLinesVO;
import com.company.inventory.model.vo.returns.SalesReturnLinesVO;
import com.company.inventory.model.vo.sales.SalesOrderLinesVO;
import com.company.inventory.model.vo.stocktake.StocktakeDocLineVO;
import com.company.inventory.model.vo.transfer.TransferDocLineVO;
import com.company.inventory.service.DocLineService;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 单据明细行拍平查询服务实现(V17,纯只读,不碰任何过账/扣减/状态机)。
 *
 * <p>查询方法统一走各单据主表 Mapper 的 selectDocLines(自定义 XML,数据库层分页);
 * 仓维度单据按 DataScope 授权仓过滤(口径与主表列表一致);
 * 采购订单/销售订单与主表一致不做数据权限过滤。</p>
 *
 * @author inventory
 */
@Service
public class DocLineServiceImpl implements DocLineService {

    /** 采购订单主表 Mapper。 */
    private final PurchaseOrderMapper purchaseOrderMapper;
    /** 销售订单主表 Mapper。 */
    private final SalesOrderMapper salesOrderMapper;
    /** 入库单主表 Mapper。 */
    private final InboundDocMapper inboundDocMapper;
    /** 出库单主表 Mapper。 */
    private final OutboundDocMapper outboundDocMapper;
    /** 调拨单主表 Mapper。 */
    private final TransferDocMapper transferDocMapper;
    /** 盘点单主表 Mapper。 */
    private final StocktakeDocMapper stocktakeDocMapper;
    /** 调整单主表 Mapper。 */
    private final StockAdjustDocMapper stockAdjustDocMapper;
    /** 期初单主表 Mapper。 */
    private final OpeningStockDocMapper openingStockDocMapper;
    /** 采购退货单主表 Mapper。 */
    private final PurchaseReturnMapper purchaseReturnMapper;
    /** 销售退货单主表 Mapper。 */
    private final SalesReturnMapper salesReturnMapper;

    /**
     * 构造服务(10 个单据主表 Mapper 注入,明细行查询走 selectDocLines)。
     *
     * @param purchaseOrderMapper 采购订单主表 Mapper
     * @param salesOrderMapper 销售订单主表 Mapper
     * @param inboundDocMapper 入库单主表 Mapper
     * @param outboundDocMapper 出库单主表 Mapper
     * @param transferDocMapper 调拨单主表 Mapper
     * @param stocktakeDocMapper 盘点单主表 Mapper
     * @param stockAdjustDocMapper 调整单主表 Mapper
     * @param openingStockDocMapper 期初单主表 Mapper
     * @param purchaseReturnMapper 采购退货单主表 Mapper
     * @param salesReturnMapper 销售退货单主表 Mapper
     */
    public DocLineServiceImpl(PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper,
            InboundDocMapper inboundDocMapper,
            OutboundDocMapper outboundDocMapper,
            TransferDocMapper transferDocMapper,
            StocktakeDocMapper stocktakeDocMapper,
            StockAdjustDocMapper stockAdjustDocMapper,
            OpeningStockDocMapper openingStockDocMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            SalesReturnMapper salesReturnMapper) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.inboundDocMapper = inboundDocMapper;
        this.outboundDocMapper = outboundDocMapper;
        this.transferDocMapper = transferDocMapper;
        this.stocktakeDocMapper = stocktakeDocMapper;
        this.stockAdjustDocMapper = stockAdjustDocMapper;
        this.openingStockDocMapper = openingStockDocMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.salesReturnMapper = salesReturnMapper;
    }

    /**
     * 采购订单明细行分页(与主表一致不做数据权限过滤)。
     */
    @Override
    public PageResult<PurchaseOrderLinesVO> listPurchaseOrderLines(PurchaseOrderLinesQuery query) {
        IPage<PurchaseOrderLinesVO> page = purchaseOrderMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 销售订单明细行分页(与主表一致不做数据权限过滤)。
     */
    @Override
    public PageResult<SalesOrderLinesVO> listSalesOrderLines(SalesOrderLinesQuery query) {
        IPage<SalesOrderLinesVO> page = salesOrderMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 入库单明细行分页。
     */
    @Override
    public PageResult<InboundDocLineVO> listInboundLines(InboundDocLineQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<InboundDocLineVO> page = inboundDocMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 出库单明细行分页。
     */
    @Override
    public PageResult<OutboundDocLineVO> listOutboundLines(OutboundDocLineQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<OutboundDocLineVO> page = outboundDocMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 调拨单明细行分页(授权仓口径:源仓或目的仓命中,同主表)。
     */
    @Override
    public PageResult<TransferDocLineVO> listTransferLines(TransferDocLineQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<TransferDocLineVO> page = transferDocMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 盘点单明细行分页。
     */
    @Override
    public PageResult<StocktakeDocLineVO> listStocktakeLines(StocktakeDocLineQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<StocktakeDocLineVO> page = stocktakeDocMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 调整单明细行分页。
     */
    @Override
    public PageResult<StockAdjustDocLineVO> listAdjustLines(StockAdjustDocLineQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<StockAdjustDocLineVO> page = stockAdjustDocMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 期初库存单明细行分页。
     */
    @Override
    public PageResult<OpeningStockDocLineVO> listOpeningLines(OpeningStockDocLineQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<OpeningStockDocLineVO> page = openingStockDocMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 采购退货单明细行分页。
     */
    @Override
    public PageResult<PurchaseReturnLinesVO> listPurchaseReturnLines(PurchaseReturnLinesQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<PurchaseReturnLinesVO> page = purchaseReturnMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 销售退货单明细行分页。
     */
    @Override
    public PageResult<SalesReturnLinesVO> listSalesReturnLines(SalesReturnLinesQuery query) {
        // 数据权限:未授权任何仓库查空;授权用户只查授权仓(admin 豁免不过滤),口径同主表
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
            }
            query.setAllowedWarehouseIds(allowed);
        }

        IPage<SalesReturnLinesVO> page = salesReturnMapper.selectDocLines(
                new Page<>(query.getPage(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPage(), query.getPageSize());
    }

}
