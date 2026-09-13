package com.company.inventory.mapper.report;

import com.company.inventory.model.query.report.ReportCriteria;
import com.company.inventory.model.vo.report.AgeingRow;
import com.company.inventory.model.vo.report.MonthlyAggRow;
import com.company.inventory.model.vo.report.MonthlyWhRow;
import com.company.inventory.model.vo.report.ReconAggRow;
import com.company.inventory.model.vo.report.ReconDocRow;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 报表中心 Mapper:进销存月报/库龄呆滞/采购对账/销售对账的只读聚合 SQL。
 *
 * <p>全部为 SELECT(报表只读);SQL 见 resources/mapper/report/ReportMapper.xml;
 * 入参统一 ReportCriteria(服务层已做数据权限与空集合归一化,
 * XML 中 allowedWarehouseIds/itemIds 非空即非空集合)。</p>
 *
 * @author inventory
 */
@Mapper
public interface ReportMapper {

    /**
     * 月报 item 集合计数(区间末前有任何流水的物品)。
     *
     * @param q 查询参数
     * @return 物品数
     */
    long countMonthlyItems(@Param("q") ReportCriteria q);

    /**
     * 月报聚合(item 维度:期初/期末/入/出/入库金额/出库金额),SQL 分页。
     *
     * @param q 查询参数
     * @return 当前页聚合行
     */
    List<MonthlyAggRow> selectMonthlyItems(@Param("q") ReportCriteria q);

    /**
     * 月报行展开:指定物品的各仓期末量(窗口函数取各粒度组合最后一次流水)。
     *
     * @param q       查询参数(to/warehouseId 口径同主查询)
     * @param itemIds 当前页物品 ID(非空)
     * @return 物品×仓库期末明细行
     */
    List<MonthlyWhRow> selectMonthlyWarehouseClosing(@Param("q") ReportCriteria q,
                                                     @Param("itemIds") List<Long> itemIds);

    /**
     * 库龄/呆滞计数(当前量 > 0 的 仓库×物品×批次 组合)。
     *
     * @param q 查询参数
     * @return 行数
     */
    long countAgeing(@Param("q") ReportCriteria q);

    /**
     * 库龄/呆滞聚合(SQL 分页)。
     *
     * @param q 查询参数
     * @return 当前页行
     */
    List<AgeingRow> selectAgeing(@Param("q") ReportCriteria q);

    /**
     * 采购对账计数(期间内有采购单或退货单的供应商数)。
     *
     * @param q 查询参数
     * @return 供应商数
     */
    long countPurchaseRecon(@Param("q") ReportCriteria q);

    /**
     * 采购对账聚合(供应商维度,SQL 分页)。
     *
     * @param q 查询参数
     * @return 当前页供应商聚合行
     */
    List<ReconAggRow> selectPurchaseRecon(@Param("q") ReportCriteria q);

    /**
     * 采购对账行展开:期间内指定供应商的采购单+退货单明细。
     *
     * @param q        查询参数
     * @param partyIds 当前页供应商 ID(非空)
     * @return 单据明细行
     */
    List<ReconDocRow> selectPurchaseReconDocs(@Param("q") ReportCriteria q,
                                              @Param("partyIds") List<Long> partyIds);

    /**
     * 销售对账计数(期间内有销售单或退货单的客户数)。
     *
     * @param q 查询参数
     * @return 客户数
     */
    long countSalesRecon(@Param("q") ReportCriteria q);

    /**
     * 销售对账聚合(客户维度,SQL 分页)。
     *
     * @param q 查询参数
     * @return 当前页客户聚合行
     */
    List<ReconAggRow> selectSalesRecon(@Param("q") ReportCriteria q);

    /**
     * 销售对账行展开:期间内指定客户的销售单+退货单明细。
     *
     * @param q        查询参数
     * @param partyIds 当前页客户 ID(非空)
     * @return 单据明细行
     */
    List<ReconDocRow> selectSalesReconDocs(@Param("q") ReportCriteria q,
                                           @Param("partyIds") List<Long> partyIds);
}
