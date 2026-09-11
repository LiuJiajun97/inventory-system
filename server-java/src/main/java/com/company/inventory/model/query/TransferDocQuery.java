package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 调拨单列表查询条件。
 *
 * @author inventory
 */
public class TransferDocQuery extends PageQuery {

    /** 源仓库 ID。 */
    @Positive(message = "源仓库 ID必须为正数")
    private Long fromWarehouseId;

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

    /** 目的仓库 ID。 */
    @Positive(message = "目的仓库 ID必须为正数")
    private Long toWarehouseId;

    /**
     * 获取目的仓库 ID。
     *
     * @return 目的仓库 ID
     */
    public Long getToWarehouseId() {
        return toWarehouseId;
    }

    /**
     * 设置目的仓库 ID。
     *
     * @param toWarehouseId 目的仓库 ID
     */
    public void setToWarehouseId(Long toWarehouseId) {
        this.toWarehouseId = toWarehouseId;
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

    /** 单据日期起(yyyy-MM-dd)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String from;

    /**
     * 获取单据日期起(yyyy-MM-dd)。
     *
     * @return 单据日期起(yyyy-MM-dd)
     */
    public String getFrom() {
        return from;
    }

    /**
     * 设置单据日期起(yyyy-MM-dd)。
     *
     * @param from 单据日期起(yyyy-MM-dd)
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /** 单据日期止(yyyy-MM-dd)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String to;

    /**
     * 获取单据日期止(yyyy-MM-dd)。
     *
     * @return 单据日期止(yyyy-MM-dd)
     */
    public String getTo() {
        return to;
    }

    /**
     * 设置单据日期止(yyyy-MM-dd)。
     *
     * @param to 单据日期止(yyyy-MM-dd)
     */
    public void setTo(String to) {
        this.to = to;
    }
}
