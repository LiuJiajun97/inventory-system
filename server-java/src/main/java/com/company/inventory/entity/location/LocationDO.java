package com.company.inventory.entity.location;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 库位表实体(表 Location)。
 *
 * @author inventory
 */
@TableName("\"Location\"")
public class LocationDO {

    /** 主键。 */
    @TableId(value = "\"id\"", type = IdType.AUTO)
    private Long id;
    /** 所属仓库 ID。 */
    @TableField("\"warehouseId\"")
    private Long warehouseId;
    /** 库位编码(仓库内唯一)。 */
    @TableField("\"locationCode\"")
    private String locationCode;
    /** 库位名称。 */
    @TableField("\"locationName\"")
    private String locationName;
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
     * 获取库位编码。
     *
     * @return 库位编码
     */
    public String getLocationCode() {
        return locationCode;
    }

    /**
     * 设置库位编码。
     *
     * @param locationCode 库位编码
     */
    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    /**
     * 获取库位名称。
     *
     * @return 库位名称
     */
    public String getLocationName() {
        return locationName;
    }

    /**
     * 设置库位名称。
     *
     * @param locationName 库位名称
     */
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
}
