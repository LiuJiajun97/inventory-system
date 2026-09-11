package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.dto.item.ItemCreateDTO;
import com.company.inventory.dto.item.ItemUpdateDTO;
import com.company.inventory.entity.item.ItemDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.query.ItemQuery;
import com.company.inventory.service.ItemService;
import com.company.inventory.vo.item.ItemVO;















import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;







import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 物品服务实现。
 *
 * @author inventory
 */
@Service
public class ItemServiceImpl implements ItemService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemServiceImpl.class);

    /** 默认税率(百分数,13%)。 */
    private static final java.math.BigDecimal DEFAULT_TAX_RATE = new java.math.BigDecimal("13.00");

    /** 物品 Mapper。 */
    private final ItemMapper itemMapper;

    /**
     * 构造服务。
     *
     * @param itemMapper 物品 Mapper
     */
    public ItemServiceImpl(ItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    /**
     * 物品分页列表(按 id 升序,keyword 对编码/名称模糊不区分大小写)。
     *
     * @param query 查询条件(keyword/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<ItemVO> list(ItemQuery query) {
        LambdaQueryWrapper<ItemDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String like = query.getKeyword().trim();
            wrapper.and(w -> w.like(ItemDO::getItemCode, like)
                    .or().like(ItemDO::getItemName, like));
        }
        wrapper.orderByAsc(ItemDO::getId);
        Page<ItemDO> page = itemMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<ItemVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 物品详情。
     *
     * @param id 物品 ID
     * @return 物品
     */
    @Override
    public ItemVO get(long id) {
        ItemDO item = itemMapper.selectById(id);
        if (item == null) {
            throw BizException.notFound("物品不存在");
        }
        return toVO(item);
    }

    /**
     * 新建物品。
     *
     * @param dto 入参
     * @return 新建物品
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItemVO create(ItemCreateDTO dto) {
        Long exist = itemMapper.selectCount(new LambdaQueryWrapper<ItemDO>()
                .eq(ItemDO::getItemCode, dto.itemCode()));
        if (exist != null && exist > 0) {
            throw new BizException("物品编码已存在", ErrorCode.ITEM_CODE_DUP, ErrorCode.HTTP_BAD_REQUEST);
        }
        ItemDO item = new ItemDO();
        item.setItemCode(dto.itemCode());
        item.setItemName(dto.itemName());
        item.setUnit(dto.unit());
        item.setSpec(dto.spec());
        item.setAttributes(dto.attributes());
        item.setCategory(dto.category());
        item.setMinStock(dto.minStock());
        item.setDefaultTaxRate(dto.defaultTaxRate() == null ? DEFAULT_TAX_RATE : dto.defaultTaxRate());
        itemMapper.insert(item);
        LOGGER.info("新建物品: code={}, name={}", dto.itemCode(), dto.itemName());
        return toVO(item);
    }

    /**
     * 编辑物品(仅 admin,编码不可改)。
     *
     * @param id  物品 ID
     * @param dto 入参(至少一个字段非空)
     * @return 更新后的物品
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ItemVO update(long id, ItemUpdateDTO dto) {
        ItemDO item = itemMapper.selectById(id);
        if (item == null) {
            throw BizException.notFound("物品不存在");
        }
        if (dto.itemName() != null) {
            item.setItemName(dto.itemName());
        }
        if (dto.unit() != null) {
            item.setUnit(dto.unit());
        }
        if (dto.spec() != null) {
            item.setSpec(dto.spec());
        }
        if (dto.attributes() != null) {
            item.setAttributes(dto.attributes());
        }
        if (dto.category() != null) {
            item.setCategory(dto.category());
        }
        if (dto.minStock() != null) {
            item.setMinStock(dto.minStock());
        }
        if (dto.defaultTaxRate() != null) {
            item.setDefaultTaxRate(dto.defaultTaxRate());
        }
        if (dto.status() != null) {
            item.setStatus(dto.status());
        }
        itemMapper.updateById(item);
        LOGGER.info("编辑物品: id={}, code={}", id, item.getItemCode());
        return toVO(item);
    }

    /**
     * 实体转 VO。
     *
     * @param item 实体
     * @return VO
     */
    private ItemVO toVO(ItemDO item) {
        return new ItemVO(item.getId(), item.getItemCode(), item.getItemName(),
                item.getUnit(), item.getSpec(), item.getAttributes(),
                item.getStatus(), item.getCreatedAt(),
                item.getCategory(), item.getMinStock(), item.getDefaultTaxRate());
    }
}
