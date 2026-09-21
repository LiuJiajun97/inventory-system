package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.price.PriceListCreateDTO;
import com.company.inventory.model.query.PriceListQuery;
import com.company.inventory.model.vo.price.EffectivePriceVO;
import com.company.inventory.model.vo.price.PriceListDetailVO;
import com.company.inventory.model.vo.price.PriceListVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 价目表服务接口(V26:供应商/客户维度带价,新建单据选物品预填单价/税率)。
 *
 * @author inventory
 */
public interface PriceListService {

    /**
     * 新建价目表(名称可空默认对方单位简称,行至少 1 条,同表同物品唯一)。
     *
     * @param dto      入参
     * @param username 操作人
     * @return 价目表
     */
    PriceListVO create(PriceListCreateDTO dto, String username);

    /**
     * 编辑价目表(整表替换:表头 + 行)。
     *
     * @param id       价目表 ID
     * @param dto      入参
     * @param username 操作人
     * @return 价目表
     */
    PriceListVO update(long id, PriceListCreateDTO dto, String username);

    /**
     * 价目表详情(表头 + 行明细含物品回填)。
     *
     * @param id 价目表 ID
     * @return 详情
     */
    PriceListDetailVO get(long id);

    /**
     * 价目表分页列表(owner_type 必填 + 对方单位关键字筛选)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PriceListVO> list(PriceListQuery query);

    /**
     * 删除价目表(级联删行)。
     *
     * @param id       价目表 ID
     * @param username 操作人
     */
    void delete(long id, String username);

    /**
     * 生效价目:该对方在指定日期(可空默认今天)命中的全部行。
     *
     * <p>命中规则:owner+物品+日期在 [validFrom, validUntil] 内(边界任一可空=不限)
     * 且 status=1;同物品多条命中取 validFrom 最近一条。</p>
     *
     * @param ownerType 对方类型:supplier / customer
     * @param ownerId   对方 ID
     * @param date      日期(可空默认今天)
     * @return 生效价目行(按物品去重)
     */
    List<EffectivePriceVO> effectivePrices(String ownerType, long ownerId, LocalDate date);

}
