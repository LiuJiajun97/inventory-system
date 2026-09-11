package com.company.inventory.entity.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 字典表实体(表 Dict):主数据下拉枚举(仓库类型/物品分类/结算方式等)。
 *
 * <p>状态机枚举(单据 status/bizCode/adjustType)不进字典,保持常量类。</p>
 *
 * @author inventory
 */
@TableName("\"Dict\"")
public class DictDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 字典类型:warehouseType / itemCategory / settleMethod。 */
    @TableField("\"dictType\"")
    private String dictType;
    /** 字典键值(下拉 value,如 raw/finished)。 */
    @TableField("\"dictKey\"")
    private String dictKey;
    /** 字典标签(下拉显示文案,如 原材料仓)。 */
    @TableField("\"dictLabel\"")
    private String dictLabel;
    /** 排序号(升序)。 */
    @TableField("\"sortOrder\"")
    private Integer sortOrder;
    /** 状态:1 启用 / 0 停用。 */
    @TableField("\"status\"")
    private Integer status;

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键。
     *
     * @param id 主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取字典类型。
     *
     * @return 字典类型
     */
    public String getDictType() {
        return dictType;
    }

    /**
     * 设置字典类型。
     *
     * @param dictType 字典类型
     */
    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    /**
     * 获取字典键值。
     *
     * @return 字典键值
     */
    public String getDictKey() {
        return dictKey;
    }

    /**
     * 设置字典键值。
     *
     * @param dictKey 字典键值
     */
    public void setDictKey(String dictKey) {
        this.dictKey = dictKey;
    }

    /**
     * 获取字典标签。
     *
     * @return 字典标签
     */
    public String getDictLabel() {
        return dictLabel;
    }

    /**
     * 设置字典标签。
     *
     * @param dictLabel 字典标签
     */
    public void setDictLabel(String dictLabel) {
        this.dictLabel = dictLabel;
    }

    /**
     * 获取排序号。
     *
     * @return 排序号
     */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /**
     * 设置排序号。
     *
     * @param sortOrder 排序号
     */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * 获取状态。
     *
     * @return 状态
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
}
