package com.company.inventory.model.entity.rbac;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户-仓库授权实体(表 sys_user_warehouse,数据权限,admin 豁免)。
 *
 * @author inventory
 */
@TableName("sys_user_warehouse")
@Getter
@Setter
public class UserWarehouseDO {

    /** 用户 ID。 */
    private Long userId;
    /** 仓库 ID。 */
    private Long warehouseId;
}
