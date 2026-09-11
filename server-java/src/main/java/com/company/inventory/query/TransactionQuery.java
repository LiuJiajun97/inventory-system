package com.company.inventory.query;

import com.company.inventory.common.page.PageQuery;

/**
 * 库存流水列表查询条件(继承分页基类)。
 *
 * <p>对应 GET /api/v1/transactions 的查询参数绑定;
 * from/to 为字符串(兼容 ISO 本地时间或带 Z 的 UTC 时间),由服务层解析。</p>
 *
 * @author inventory
 */
public class TransactionQuery extends PageQuery {

    /** 仓库 ID(可空)。 */
    private Long warehouseId;

    /** 物品 ID(可空)。 */
    private Long itemId;

    /** 开始时间(可空,含,yyyy-MM-dd 或 ISO 格式字符串)。 */
    private String from;

    /** 结束时间(可空,含,yyyy-MM-dd 或 ISO 格式字符串)。 */
    private String to;

    /** 业务编码(可空,inbound/outbound)。 */
    private String bizCode;

    /**
     * 获取仓库 ID。
     *
     * @return 仓库 ID(可空)
     */
    public Long getWarehouseId() {
        return warehouseId;
    }

    /**
     * 设置仓库 ID。
     *
     * @param warehouseId 仓库 ID(可空)
     */
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    /**
     * 获取物品 ID。
     *
     * @return 物品 ID(可空)
     */
    public Long getItemId() {
        return itemId;
    }

    /**
     * 设置物品 ID。
     *
     * @param itemId 物品 ID(可空)
     */
    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    /**
     * 获取开始时间。
     *
     * @return 开始时间字符串(可空)
     */
    public String getFrom() {
        return from;
    }

    /**
     * 设置开始时间。
     *
     * @param from 开始时间字符串(可空)
     */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
     * 获取结束时间。
     *
     * @return 结束时间字符串(可空)
     */
    public String getTo() {
        return to;
    }

    /**
     * 设置结束时间。
     *
     * @param to 结束时间字符串(可空)
     */
    public void setTo(String to) {
        this.to = to;
    }

    /**
     * 获取业务编码。
     *
     * @return 业务编码(可空)
     */
    public String getBizCode() {
        return bizCode;
    }

    /**
     * 设置业务编码。
     *
     * @param bizCode 业务编码(可空)
     */
    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }
}
