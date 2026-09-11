package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 采购订单列表查询条件。
 *
 * @author inventory
 */
public class PurchaseOrderQuery extends PageQuery {

    /** 供应商 ID。 */
    @Positive(message = "供应商 ID必须为正数")
    private Long supplierId;

    /**
     * 获取供应商 ID。
     *
     * @return 供应商 ID
     */
    public Long getSupplierId() {
        return supplierId;
    }

    /**
     * 设置供应商 ID。
     *
     * @param supplierId 供应商 ID
     */
    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
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
