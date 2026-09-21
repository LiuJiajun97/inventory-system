package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
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

/**
 * 单据明细行拍平查询服务(V17 主表/明细切换,纯只读):
 * 10 类带明细单据的列表接口各加 /lines 视图,行 = 单据行 JOIN 主表关键字段。
 *
 * <p>数据权限与主表列表同口径:仓维度单据按 DataScope 授权仓过滤
 * (null = admin 豁免,空列表 = 查空);采购订单/销售订单主表无数据权限,此处一致不过滤。</p>
 *
 * @author inventory
 */
public interface DocLineService {

    /**
     * 采购订单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PurchaseOrderLinesVO> listPurchaseOrderLines(PurchaseOrderLinesQuery query);

    /**
     * 销售订单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesOrderLinesVO> listSalesOrderLines(SalesOrderLinesQuery query);

    /**
     * 入库单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<InboundDocLineVO> listInboundLines(InboundDocLineQuery query);

    /**
     * 出库单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<OutboundDocLineVO> listOutboundLines(OutboundDocLineQuery query);

    /**
     * 调拨单明细行分页(授权仓口径:源仓或目的仓命中)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TransferDocLineVO> listTransferLines(TransferDocLineQuery query);

    /**
     * 盘点单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<StocktakeDocLineVO> listStocktakeLines(StocktakeDocLineQuery query);

    /**
     * 调整单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<StockAdjustDocLineVO> listAdjustLines(StockAdjustDocLineQuery query);

    /**
     * 期初库存单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<OpeningStockDocLineVO> listOpeningLines(OpeningStockDocLineQuery query);

    /**
     * 采购退货单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PurchaseReturnLinesVO> listPurchaseReturnLines(PurchaseReturnLinesQuery query);

    /**
     * 销售退货单明细行分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SalesReturnLinesVO> listSalesReturnLines(SalesReturnLinesQuery query);
}
