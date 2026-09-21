package com.company.inventory.model.query;

import com.company.inventory.common.page.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 期初库存明细行列表查询条件(单据行拍平视图,与主表列表公共筛选字段一致)。
 *
 * @author inventory
 */
@Getter
@Setter
public class OpeningStockDocLineQuery extends PageQuery {

    /** 仓库 ID(可空,指定时仅查该仓库单据)。 */
    @Positive(message = "仓库 ID必须为正数")
    private Long warehouseId;

    /** 单号关键字(可空,模糊)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String docNo;

    /** 单据状态(可空)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String status;

    /** 单据日期起(可空,yyyy-MM-dd)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String from;

    /** 单据日期止(可空,yyyy-MM-dd)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String to;
    /** 物品关键字(可空,编码或名称模糊)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String itemKeyword;

    /** 批次号(可空,精确)。 */
    @Size(max = 100, message = "长度不能超过 100")
    private String batchNo;

    /** 数据权限授权仓(服务层注入,null = 豁免,仅授权仓维度单据使用)。 */
    private List<Long> allowedWarehouseIds;
}
