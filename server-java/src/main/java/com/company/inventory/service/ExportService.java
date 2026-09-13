package com.company.inventory.service;

import com.company.inventory.model.query.CustomerQuery;
import com.company.inventory.model.query.InboundDocQuery;
import com.company.inventory.model.query.ItemQuery;
import com.company.inventory.model.query.OutboundDocQuery;
import com.company.inventory.model.query.PurchaseOrderQuery;
import com.company.inventory.model.query.SalesOrderQuery;
import com.company.inventory.model.query.StockQuery;
import com.company.inventory.model.query.SupplierQuery;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 导出服务:8 个列表的 xlsx 导出(不分页,复用既有列表 Query DTO 与 Service 查询)。
 *
 * <p>权限与各自列表读一致(导出=只读操作);库存导出沿用库存查询的数据权限
 * (DataScope 仓库授权过滤)。</p>
 *
 * @author inventory
 */
public interface ExportService {

    /**
     * 物品导出。
     *
     * @param query 列表查询条件(与 GET /items 一致,分页参数忽略)
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportItems(ItemQuery query, HttpServletResponse resp);

    /**
     * 供应商导出。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportSuppliers(SupplierQuery query, HttpServletResponse resp);

    /**
     * 客户导出。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportCustomers(CustomerQuery query, HttpServletResponse resp);

    /**
     * 库存导出。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportStock(StockQuery query, HttpServletResponse resp);

    /**
     * 入库单导出(头信息一行一单)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportInbound(InboundDocQuery query, HttpServletResponse resp);

    /**
     * 出库单导出(头信息一行一单)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportOutbound(OutboundDocQuery query, HttpServletResponse resp);

    /**
     * 采购订单导出(头信息一行一单)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportPurchaseOrders(PurchaseOrderQuery query, HttpServletResponse resp);

    /**
     * 销售订单导出(头信息一行一单)。
     *
     * @param query 列表查询条件
     * @param resp  HTTP 响应(xlsx 流)
     */
    void exportSalesOrders(SalesOrderQuery query, HttpServletResponse resp);
}
