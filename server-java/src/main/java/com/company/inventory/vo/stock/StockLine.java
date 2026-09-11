package com.company.inventory.vo.stock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存操作行(入库/出库共用,对应 stock-core 的 InboundLine/OutboundLine)。
 *
 * @author inventory
 */
public class StockLine {

    /** 物品 ID。 */
    private Long itemId;

    /** 数量(必须为正数)。 */
    private BigDecimal qty;

    /** 批次号(可选)。 */
    private String batchNo;

    /** 生产日期(入库可选)。 */
    private LocalDate productionDate;

    /** 保质期到期日(入库可选)。 */
    private LocalDate expiryDate;

    /** 供应商(入库可选)。 */
    private String supplier;

    /** 库位 ID(可选,缺省 0)。 */
    private Long locationId;

    /** 序列号列表(可选)。 */
    private List<String> serialNos;

    /** 调拨目的库位 ID(仅调拨使用,可选)。 */
    private Long toLocationId;

    /**
     * 构造空行,由调用方逐字段填充。
     */
    public StockLine() {
    }

    /**
     * 获取物品 ID。
     *
     * @return 物品 ID
     */
    public Long getItemId() {
        return itemId;
    }

    /**
     * 设置物品 ID。
     *
     * @param itemId 物品 ID
     */
    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    /**
     * 获取数量。
     *
     * @return 数量
     */
    public BigDecimal getQty() {
        return qty;
    }

    /**
     * 设置数量。
     *
     * @param qty 数量
     */
    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    /**
     * 获取批次号。
     *
     * @return 批次号
     */
    public String getBatchNo() {
        return batchNo;
    }

    /**
     * 设置批次号。
     *
     * @param batchNo 批次号
     */
    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    /**
     * 获取生产日期。
     *
     * @return 生产日期
     */
    public LocalDate getProductionDate() {
        return productionDate;
    }

    /**
     * 设置生产日期。
     *
     * @param productionDate 生产日期
     */
    public void setProductionDate(LocalDate productionDate) {
        this.productionDate = productionDate;
    }

    /**
     * 获取保质期到期日。
     *
     * @return 到期日
     */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    /**
     * 设置保质期到期日。
     *
     * @param expiryDate 到期日
     */
    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * 获取供应商。
     *
     * @return 供应商
     */
    public String getSupplier() {
        return supplier;
    }

    /**
     * 设置供应商。
     *
     * @param supplier 供应商
     */
    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    /**
     * 获取库位 ID。
     *
     * @return 库位 ID
     */
    public Long getLocationId() {
        return locationId;
    }

    /**
     * 设置库位 ID。
     *
     * @param locationId 库位 ID
     */
    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    /**
     * 获取序列号列表。
     *
     * @return 序列号列表
     */
    public List<String> getSerialNos() {
        return serialNos;
    }

    /**
     * 设置序列号列表。
     *
     * @param serialNos 序列号列表
     */
    public void setSerialNos(List<String> serialNos) {
        this.serialNos = serialNos;
    }

    /**
     * 获取调拨目的库位 ID。
     *
     * @return 调拨目的库位 ID
     */
    public Long getToLocationId() {
        return toLocationId;
    }

    /**
     * 设置调拨目的库位 ID。
     *
     * @param toLocationId 调拨目的库位 ID
     */
    public void setToLocationId(Long toLocationId) {
        this.toLocationId = toLocationId;
    }
}
