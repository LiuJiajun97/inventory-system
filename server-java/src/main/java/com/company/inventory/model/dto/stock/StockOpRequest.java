package com.company.inventory.model.dto.stock;
import com.company.inventory.model.vo.stock.StockLine;





import java.util.List;

/**
 * 库存操作请求(对应 stock-core 的 InboundParams/OutboundParams)。
 *
 * @author inventory
 */
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

    /**
     * 获取仓库 ID。
     *
     * @return 仓库 ID
     */
    public Long getWarehouseId() {
        return warehouseId;
    }

    /**
     * 设置仓库 ID。
     *
     * @param warehouseId 仓库 ID
     */
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    /**
     * 获取操作行。
     *
     * @return 操作行列表
     */
    public List<StockLine> getLines() {
        return lines;
    }

    /**
     * 设置操作行。
     *
     * @param lines 操作行列表
     */
    public void setLines(List<StockLine> lines) {
        this.lines = lines;
    }

    /**
     * 获取单据号。
     *
     * @return 单据号
     */
    public String getDocNo() {
        return docNo;
    }

    /**
     * 设置单据号。
     *
     * @param docNo 单据号
     */
    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    /**
     * 获取操作人。
     *
     * @return 操作人
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 设置操作人。
     *
     * @param operator 操作人
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * 获取源仓库 ID。
     *
     * @return 源仓库 ID
     */
    public Long getFromWarehouseId() {
        return fromWarehouseId;
    }

    /**
     * 设置源仓库 ID。
     *
     * @param fromWarehouseId 源仓库 ID
     */
    public void setFromWarehouseId(Long fromWarehouseId) {
        this.fromWarehouseId = fromWarehouseId;
    }

    /**
     * 是否销售发货模式。
     *
     * @return true 表示销售发货(优先扣预占)
     */
    public boolean isSalesShip() {
        return salesShip;
    }

    /**
     * 设置销售发货模式。
     *
     * @param salesShip true 表示销售发货
     */
    public void setSalesShip(boolean salesShip) {
        this.salesShip = salesShip;
    }

    /**
     * 获取流水业务类型。
     *
     * @return 流水业务类型
     */
    public String getBizCode() {
        return bizCode;
    }

    /**
     * 设置流水业务类型。
     *
     * @param bizCode 流水业务类型
     */
    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }
}
