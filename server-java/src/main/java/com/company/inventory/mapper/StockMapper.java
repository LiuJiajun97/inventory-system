package com.company.inventory.mapper;

import com.company.inventory.entity.stock.StockDO;





import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 库存余额 Mapper 接口。
 *
 * <p>核心 SQL 见 mapper/StockMapper.xml:
 * 出库扣减是条件 UPDATE(带 quantity &gt;= qty),防并发穿仓的关键,禁止先查后改。</p>
 *
 * @author inventory
 */
@Mapper
public interface StockMapper extends BaseMapper<StockDO> {

    /**
     * 条件扣减库存(原子操作,防穿仓)。
     *
     * <p>SQL: UPDATE Stock SET quantity = quantity - #{qty}
     * WHERE 唯一键匹配 AND quantity &gt;= #{qty}。
     * 影响行数为 0 表示库存不足,调用方必须抛业务异常回滚。</p>
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID
     * @param locationId  库位 ID
     * @param qty         扣减数量
     * @return 影响行数(0 或 1)
     */
    int deductStock(@Param("warehouseId") Long warehouseId, @Param("itemId") Long itemId,
            @Param("batchId") Long batchId, @Param("locationId") Long locationId,
            @Param("qty") BigDecimal qty);

    /**
     * 条件扣减并原子返回扣后余额(UPDATE ... RETURNING,并发下 afterQty 精确)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID
     * @param locationId  库位 ID
     * @param qty         扣减数量
     * @return 扣后余额;null 表示条件不满足未扣减
     */
    java.math.BigDecimal deductStockReturning(@Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId, @Param("batchId") Long batchId,
            @Param("locationId") Long locationId, @Param("qty") BigDecimal qty);

    /**
     * 增加库存(余额行已存在时)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID
     * @param locationId  库位 ID
     * @param qty         增加数量
     * @return 影响行数(0 或 1)
     */
    int incrementStock(@Param("warehouseId") Long warehouseId, @Param("itemId") Long itemId,
            @Param("batchId") Long batchId, @Param("locationId") Long locationId,
            @Param("qty") BigDecimal qty);

    /**
     * 新建余额行(首次入库时)。
     *
     * @param stock 余额行
     * @return 影响行数
     */
    int insertStockRow(StockDO stock);

    /**
     * 查询某仓库/物品/库位下 quantity&gt;0 且属于批次的余额行(选批候选集)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param locationId  库位 ID
     * @return 候选余额行(按 id 升序,保证确定性)
     */
    List<StockDO> selectActiveStocks(@Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId, @Param("locationId") Long locationId);

    /**
     * 批量查询每个批次的最近一笔入库流水时间(FIFO 选批用,一次查完,禁止循环查库)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchIds    批次 ID 集合
     * @return 每行含 batchId 与 maxCreatedAt 两个键
     */
    List<Map<String, Object>> selectLatestInboundTimeByBatch(@Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId, @Param("batchIds") List<Long> batchIds);

    /**
     * 查询某仓/物品下余额大于 0 的所有余额行(预占候选集,含无批次行,按 id 升序)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @return 候选余额行
     */
    List<StockDO> selectPreAllocCandidates(@Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId);

    /**
     * 单行条件预占(可用量足够才加),防并发超预占。
     *
     * @param stockId 余额行 ID
     * @param qty     预占数量
     * @return 影响行数(0 或 1)
     */
    int preAllocRow(@Param("stockId") Long stockId, @Param("qty") BigDecimal qty);

    /**
     * 单行条件释放预占(预占量足够才减)。
     *
     * @param stockId 余额行 ID
     * @param qty     释放数量
     * @return 影响行数(0 或 1)
     */
    int releasePreAllocRow(@Param("stockId") Long stockId, @Param("qty") BigDecimal qty);

    /**
     * 单行销售发货扣减:同时扣库存与预占(预占量足够才扣)。
     *
     * @param stockId 余额行 ID
     * @param qty     发货数量
     * @return 影响行数(0 或 1)
     */
    int salesDeductRow(@Param("stockId") Long stockId, @Param("qty") BigDecimal qty);
}
