package com.company.inventory.model.dto.stock;
import com.company.inventory.model.vo.stock.StockLine;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 库存操作请求(对应 stock-core 的 InboundParams/OutboundParams)。
 *
 * @author inventory
 */
@Getter
@Setter
public class StockOpRequest {

    /** 仓库 ID。 */
    private Long warehouseId;

    /** 操作行。 */
    private List<StockLine> lines;

    /** 单据号。 */
    private String docNo;

    /** 操作人(可空)。 */
    private String operator;

    /** 源仓库 ID(仅调拨使用,其余为 null)。 */
    private Long fromWarehouseId;

    /** 销售发货模式(优先扣预占行并同步释放预占,方案 §5.2)。 */
    private boolean salesShip;

    /** 流水业务类型(可空,空=按方向取 inbound/outbound 默认值;调整单传 adjust_in/adjust_out)。 */
    private String bizCode;

    /**
     * 构造空请求,由调用方逐字段填充。
     */
    public StockOpRequest() {
    }

}
