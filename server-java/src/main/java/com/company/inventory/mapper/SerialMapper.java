package com.company.inventory.mapper;

import com.company.inventory.model.entity.stock.SerialDO;





import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 序列号 Mapper 接口。
 *
 * @author inventory
 */
@Mapper
public interface SerialMapper extends BaseMapper<SerialDO> {

    /**
     * 条件标记序列号出库(状态 in_stock + 仓库匹配,原子操作)。
     *
     * <p>任一序列号影响行数为 0 即视为"不存在或已出库",整单回滚。</p>
     *
     * @param itemId      物品 ID
     * @param serialNo    序列号
     * @param warehouseId 仓库 ID
     * @return 影响行数(0 或 1)
     */
    int markOutbound(@Param("itemId") Long itemId, @Param("serialNo") String serialNo,
            @Param("warehouseId") Long warehouseId);

    /**
     * 调拨:条件修改序列号归属仓库(仅在源仓 in_stock 才迁移,原子操作)。
     *
     * @param itemId         物品 ID
     * @param serialNo       序列号
     * @param fromWarehouseId 源仓库 ID
     * @param toWarehouseId   目的仓库 ID
     * @return 影响行数(0 或 1)
     */
    int transferWarehouse(@Param("itemId") Long itemId, @Param("serialNo") String serialNo,
            @Param("fromWarehouseId") Long fromWarehouseId, @Param("toWarehouseId") Long toWarehouseId);

    /**
     * 退货回流:条件回置序列号入库(仅 out 状态才回置,原子操作)。
     *
     * <p>影响行数 0 表示该序列号不是 out 状态(需走新建入库,撞唯一键由 DB 约束兜底)。</p>
     *
     * @param itemId      物品 ID
     * @param serialNo    序列号
     * @param warehouseId 仓库 ID
     * @return 影响行数(0 或 1)
     */
    int markBackInStock(@Param("itemId") Long itemId, @Param("serialNo") String serialNo,
            @Param("warehouseId") Long warehouseId);
}
