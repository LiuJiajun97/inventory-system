package com.company.inventory.vo.stock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 库存操作结果(对应 stock-core 的 StockOpResult)。
 *
 * @author inventory
 */
public class StockOpResult {

    /** 结果行。 */
    private final List<StockOpRow> rows = new ArrayList<>();

    /**
     * 新增结果行。
     *
     * @param row 结果行
     */
    public void addRow(StockOpRow row) {
        rows.add(row);
    }

    /**
     * 获取结果行(永不为 null)。
     *
     * @return 结果行列表
     */
    public List<StockOpRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * 单行结果:物品/批次/库位/数量。
     *
     * @param itemId     物品 ID
     * @param batchId    批次 ID
     * @param locationId 库位 ID
     * @param qty        数量
     * @author inventory
     */
    public record StockOpRow(Long itemId, Long batchId, Long locationId, BigDecimal qty) {
    }
}
