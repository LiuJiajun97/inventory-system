package com.company.inventory.model.query.report;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 报表 SQL 内部参数(由服务层把对外 Query + 数据权限归一化后传给 Mapper XML)。
 *
 * <p>统一持有:日期区间(LocalDate,单据日期与流水日期口径一致,含当天:流水按
 * created_at 落区间当日 00:00:00 至次日 00:00:00)、仓库/物品/供应商/客户过滤、
 * 数据权限授权仓(allowedWarehouseIds:null = 豁免,空列表 = 查空)、
 * 分页 limit/offset。空集合字段一律 null 而非 empty,
 * 与 MyBatis-Plus 空集合防护口径一致。</p>
 *
 * @author inventory
 */
@Getter
@Setter
public class ReportCriteria {

    /** 日期区间起(可空,含;流水 created_at 与单据 doc_date 统一口径)。 */
    private LocalDate fromDate;

    /** 单据日期区间止(可空,含)。 */
    private LocalDate toDate;

    /** 仓库过滤(可空)。 */
    private Long warehouseId;

    /** 物品 ID 过滤(可空,空集已归一为 null)。 */
    private List<Long> itemIds;

    /** 授权仓(可空;null = 豁免,空列表 = 查空)。 */
    private List<Long> allowedWarehouseIds;

    /** 供应商过滤(可空)。 */
    private Long supplierId;

    /** 客户过滤(可空)。 */
    private Long customerId;

    /** 库龄天数区间起(可空)。 */
    private Integer ageFrom;

    /** 库龄天数区间止(可空)。 */
    private Integer ageTo;

    /** 呆滞判定天数(默认 90)。 */
    private int stagnantDays = StockAgeingQuery.DEFAULT_STAGNANT_DAYS;

    /** 分页每页条数。 */
    private long limit;

    /** 分页偏移(0 起)。 */
    private long offset;
}
