package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 库存调整单列表查询条件。
 *
 * @author inventory
 */
public class StockAdjustQuery extends PageQuery {

    /** 仓库 ID。 */
    @Positive(message = "仓库 ID必须为正数")
    private Long warehouseId;

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

    /** 调整类型。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String adjustType;

    /**
     * 获取调整类型。
     *
     * @return 调整类型
     */
    public String getAdjustType() {
        return adjustType;
    }

    /**
     * 设置调整类型。
     *
     * @param adjustType 调整类型
     */
    public void setAdjustType(String adjustType) {
        this.adjustType = adjustType;
    }

    /** 单号关键字。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String docNo;

    /**
     * 获取单号关键字。
     *
     * @return 单号关键字
     */
    public String getDocNo() {
        return docNo;
    }

    /**
     * 设置单号关键字。
     *
     * @param docNo 单号关键字
     */
    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    /** 单据状态。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String status;

    /**
     * 获取单据状态。
     *
     * @return 单据状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置单据状态。
     *
     * @param status 单据状态
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
