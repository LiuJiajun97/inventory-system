package com.company.inventory.service.impl;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.PriceListItemMapper;
import com.company.inventory.mapper.PriceListMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.model.dto.price.PriceListCreateDTO;
import com.company.inventory.model.dto.price.PriceListLineDTO;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.price.PriceListItemDO;
import com.company.inventory.model.entity.price.PriceListDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.query.PriceListQuery;
import com.company.inventory.model.vo.price.EffectivePriceVO;
import com.company.inventory.model.vo.price.PriceListDetailVO;
import com.company.inventory.model.vo.price.PriceListLineVO;
import com.company.inventory.model.vo.price.PriceListVO;
import com.company.inventory.service.PriceListService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 价目表服务实现(V26)。
 *
 * <p>命中规则(单据新建带价用):owner_type+owner_id+物品+单据日期在
 * [validFrom, validUntil] 内(边界任一可空=不限)且 status=1;
 * 同物品多条命中取 validFrom 最近一条(validFrom 可空视为最早)。</p>
 *
 * @author inventory
 */
@Service
public class PriceListServiceImpl implements PriceListService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(PriceListServiceImpl.class);

    /** 对方类型:供应商。 */
    private static final String OWNER_SUPPLIER = "supplier";

    /** 对方类型:客户。 */
    private static final String OWNER_CUSTOMER = "customer";

    /** 状态:启用。 */
    private static final int STATUS_ENABLED = 1;

    /** 价目表 Mapper。 */
    private final PriceListMapper priceListMapper;
    /** 价目表行 Mapper。 */
    private final PriceListItemMapper priceListItemMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMapper;
    /** 供应商 Mapper。 */
    private final SupplierMapper supplierMapper;
    /** 客户 Mapper。 */
    private final CustomerMapper customerMapper;

    /**
     * 构造服务。
     *
     * @param priceListMapper     价目表 Mapper
     * @param priceListItemMapper 价目表行 Mapper
     * @param itemMapper          物品 Mapper
     * @param supplierMapper      供应商 Mapper
     * @param customerMapper      客户 Mapper
     */
    public PriceListServiceImpl(PriceListMapper priceListMapper, PriceListItemMapper priceListItemMapper,
            ItemMapper itemMapper, SupplierMapper supplierMapper, CustomerMapper customerMapper) {
        this.priceListMapper = priceListMapper;
        this.priceListItemMapper = priceListItemMapper;
        this.itemMapper = itemMapper;
        this.supplierMapper = supplierMapper;
        this.customerMapper = customerMapper;
    }

    /**
     * 新建价目表。
     *
     * @param dto      入参
     * @param username 操作人
     * @return 价目表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PriceListVO create(PriceListCreateDTO dto, String username) {
        OwnerInfo owner = requireOwner(dto.ownerType(), dto.ownerId());
        requireLines(dto);

        PriceListDO list = new PriceListDO();
        list.setOwnerType(dto.ownerType());
        list.setOwnerId(dto.ownerId());
        list.setName(StringUtils.hasText(dto.name()) ? dto.name().trim() : owner.name());
        list.setValidFrom(dto.validFrom());
        list.setValidUntil(dto.validUntil());
        list.setStatus(STATUS_ENABLED);
        list.setCreator(username);
        list.setCreatedAt(LocalDateTime.now());
        priceListMapper.insert(list);
        insertLines(list.getId(), dto.lines(), username);
        LOGGER.info("价目表新建: id={}, 对方={}/{}, 行数={}, 操作人={}",
                list.getId(), dto.ownerType(), dto.ownerId(), dto.lines().size(), username);
        return toVO(list, owner.code(), owner.name(), dto.lines().size());
    }

    /**
     * 编辑价目表(整表替换)。
     *
     * @param id       价目表 ID
     * @param dto      入参
     * @param username 操作人
     * @return 价目表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PriceListVO update(long id, PriceListCreateDTO dto, String username) {
        PriceListDO list = requireList(id);
        OwnerInfo owner = requireOwner(dto.ownerType(), dto.ownerId());
        requireLines(dto);

        list.setOwnerType(dto.ownerType());
        list.setOwnerId(dto.ownerId());
        list.setName(StringUtils.hasText(dto.name()) ? dto.name().trim() : owner.name());
        list.setValidFrom(dto.validFrom());
        list.setValidUntil(dto.validUntil());
        list.setUpdater(username);
        list.setUpdatedAt(LocalDateTime.now());
        priceListMapper.updateById(list);

        priceListItemMapper.delete(new LambdaQueryWrapper<PriceListItemDO>()
                .eq(PriceListItemDO::getPriceListId, id));
        insertLines(id, dto.lines(), username);
        LOGGER.info("价目表编辑: id={}, 对方={}/{}, 行数={}, 操作人={}",
                id, dto.ownerType(), dto.ownerId(), dto.lines().size(), username);
        return toVO(list, owner.code(), owner.name(), dto.lines().size());
    }

    /**
     * 价目表详情。
     *
     * @param id 价目表 ID
     * @return 详情
     */
    @Override
    public PriceListDetailVO get(long id) {
        PriceListDO list = requireList(id);
        List<PriceListItemDO> lines = priceListItemMapper.selectList(
                new LambdaQueryWrapper<PriceListItemDO>()
                        .eq(PriceListItemDO::getPriceListId, id)
                        .orderByAsc(PriceListItemDO::getId));
        return new PriceListDetailVO(list.getId(), list.getOwnerType(), list.getOwnerId(),
                list.getName(), list.getValidFrom(), list.getValidUntil(), list.getStatus(),
                list.getCreator(), list.getCreatedAt(), toLineVOs(lines));
    }

    /**
     * 价目表分页列表(对方单位关键字命中编码/名称)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<PriceListVO> list(PriceListQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();
        requireOwnerType(query.getOwnerType());

        List<Long> ownerIds = null;
        if (StringUtils.hasText(query.getOwnerKeyword())) {
            String like = query.getOwnerKeyword().trim();
            ownerIds = isSupplier(query.getOwnerType())
                    ? supplierMapper.selectList(new LambdaQueryWrapper<SupplierDO>()
                            .and(w -> w.like(SupplierDO::getSupplierCode, like)
                                    .or().like(SupplierDO::getSupplierName, like)))
                            .stream().map(SupplierDO::getId).toList()
                    : customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .and(w -> w.like(CustomerDO::getCustomerCode, like)
                                    .or().like(CustomerDO::getCustomerName, like)))
                            .stream().map(CustomerDO::getId).toList();
            if (ownerIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, page, pageSize);
            }
        }

        LambdaQueryWrapper<PriceListDO> wrapper = new LambdaQueryWrapper<PriceListDO>()
                .eq(PriceListDO::getOwnerType, query.getOwnerType())
                .orderByDesc(PriceListDO::getId);
        if (ownerIds != null) {
            wrapper.in(PriceListDO::getOwnerId, ownerIds);
        }
        Page<PriceListDO> pageResult = priceListMapper.selectPage(
                Page.of(page, pageSize), wrapper);
        List<PriceListDO> rows = pageResult.getRecords();

        Set<Long> ownerRowIds = rows.stream().map(PriceListDO::getOwnerId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, OwnerInfo> ownerMap = ownerRowIds.isEmpty() ? Map.of() : loadOwnerMap(
                isSupplier(query.getOwnerType()), ownerRowIds);

        // 行数:当前页价目表一行一次查会 N+1,按页内 ID 集合一次查完再分组
        Set<Long> listIds = rows.stream().map(PriceListDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Long> lineCounts = listIds.isEmpty() ? Map.of()
                : priceListItemMapper.selectList(new LambdaQueryWrapper<PriceListItemDO>()
                        .select(PriceListItemDO::getPriceListId)
                        .in(PriceListItemDO::getPriceListId, listIds)).stream()
                        .collect(Collectors.groupingBy(PriceListItemDO::getPriceListId,
                                Collectors.counting()));

        List<PriceListVO> result = new ArrayList<>();
        for (PriceListDO row : rows) {
            OwnerInfo owner = ownerMap.get(row.getOwnerId());
            result.add(toVO(row,
                    owner == null ? null : owner.code(),
                    owner == null ? null : owner.name(),
                    lineCounts.getOrDefault(row.getId(), 0L).intValue()));
        }
        return PageResult.of(result, pageResult.getTotal(), page, pageSize);
    }

    /**
     * 删除价目表(级联删行)。
     *
     * @param id       价目表 ID
     * @param username 操作人
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id, String username) {
        requireList(id);
        priceListItemMapper.delete(new LambdaQueryWrapper<PriceListItemDO>()
                .eq(PriceListItemDO::getPriceListId, id));
        priceListMapper.deleteById(id);
        LOGGER.info("价目表删除: id={}, 操作人={}", id, username);
    }

    /**
     * 生效价目(命中规则见接口 Javadoc)。
     *
     * @param ownerType 对方类型
     * @param ownerId   对方 ID
     * @param date      日期(可空默认今天)
     * @return 生效价目行(按物品去重取 validFrom 最近)
     */
    @Override
    public List<EffectivePriceVO> effectivePrices(String ownerType, long ownerId, LocalDate date) {
        requireOwnerType(ownerType);
        LocalDate d = date == null ? LocalDate.now() : date;

        List<PriceListDO> lists = priceListMapper.selectList(new LambdaQueryWrapper<PriceListDO>()
                .eq(PriceListDO::getOwnerType, ownerType)
                .eq(PriceListDO::getOwnerId, ownerId)
                .eq(PriceListDO::getStatus, STATUS_ENABLED)
                .and(w -> w.isNull(PriceListDO::getValidFrom)
                        .or().le(PriceListDO::getValidFrom, d))
                .and(w -> w.isNull(PriceListDO::getValidUntil)
                        .or().ge(PriceListDO::getValidUntil, d)));
        if (lists.isEmpty()) {
            return List.of();
        }
        Map<Long, PriceListDO> listMap = lists.stream()
                .collect(Collectors.toMap(PriceListDO::getId, Function.identity()));
        List<PriceListItemDO> items = priceListItemMapper.selectList(
                new LambdaQueryWrapper<PriceListItemDO>()
                        .in(PriceListItemDO::getPriceListId, listMap.keySet()));
        if (items.isEmpty()) {
            return List.of();
        }

        // 同物品多条命中:validFrom 最近优先(可空视为最早),同值按价目表 id 保证确定性
        Map<Long, PriceListItemDO> best = new HashMap<>();
        for (PriceListItemDO item : items) {
            PriceListItemDO cur = best.get(item.getItemId());
            if (cur == null) {
                best.put(item.getItemId(), item);
                continue;
            }
            PriceListDO curList = listMap.get(cur.getPriceListId());
            PriceListDO newList = listMap.get(item.getPriceListId());
            if (compareValidFrom(newList.getValidFrom(), curList.getValidFrom()) > 0
                    || (compareValidFrom(newList.getValidFrom(), curList.getValidFrom()) == 0
                            && newList.getId() > curList.getId())) {
                best.put(item.getItemId(), item);
            }
        }
        return best.values().stream()
                .map(i -> new EffectivePriceVO(i.getItemId(), i.getUnitPrice(), i.getTaxRate()))
                .sorted(Comparator.comparing(EffectivePriceVO::itemId))
                .toList();
    }

    /**
     * validFrom 比较:可空(不限)视为最早,排最后。
     *
     * @param a 值 a
     * @param b 值 b
     * @return 负数/0/正数
     */
    private int compareValidFrom(LocalDate a, LocalDate b) {
        if (a == null) {
            return b == null ? 0 : -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    /**
     * 批量加载对方单位编码/名称映射。
     *
     * @param supplier 是否供应商维度
     * @param ids      对方 ID 集合
     * @return ID → 编码/名称
     */
    private Map<Long, OwnerInfo> loadOwnerMap(boolean supplier, Set<Long> ids) {
        if (supplier) {
            return supplierMapper.selectByIds(ids).stream()
                    .collect(Collectors.toMap(SupplierDO::getId,
                            s -> new OwnerInfo(s.getSupplierCode(), s.getSupplierName())));
        }
        return customerMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CustomerDO::getId,
                        c -> new OwnerInfo(c.getCustomerCode(), c.getCustomerName())));
    }

    /**
     * 行明细转 VO(批量回填物品编码/名称,空集合防护)。
     *
     * @param lines 行明细
     * @return VO 列表
     */
    private List<PriceListLineVO> toLineVOs(List<PriceListItemDO> lines) {
        Set<Long> itemIds = lines.stream().map(PriceListItemDO::getItemId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, ItemDO> itemMap = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectByIds(itemIds).stream()
                        .collect(Collectors.toMap(ItemDO::getId, Function.identity()));
        return lines.stream()
                .map(l -> {
                    ItemDO item = itemMap.get(l.getItemId());
                    return new PriceListLineVO(l.getItemId(),
                            item == null ? null : item.getItemCode(),
                            item == null ? null : item.getItemName(),
                            l.getUnitPrice(), l.getTaxRate());
                })
                .toList();
    }

    /**
     * 实体转列表 VO。
     *
     * @param list      实体
     * @param ownerCode 对方编码(可空)
     * @param ownerName 对方名称(可空)
     * @param lineCount 行数
     * @return VO
     */
    private PriceListVO toVO(PriceListDO list, String ownerCode, String ownerName, int lineCount) {
        return new PriceListVO(list.getId(), list.getOwnerType(), list.getOwnerId(),
                ownerCode, ownerName, list.getName(), list.getValidFrom(),
                list.getValidUntil(), lineCount, list.getStatus(), list.getCreator(),
                list.getCreatedAt(), list.getUpdater(), list.getUpdatedAt());
    }

    /**
     * 校验对方类型合法(supplier / customer)。
     *
     * @param ownerType 对方类型
     */
    private void requireOwnerType(String ownerType) {
        if (!OWNER_SUPPLIER.equals(ownerType) && !OWNER_CUSTOMER.equals(ownerType)) {
            throw new BizException("对方类型不合法: 仅支持 supplier / customer");
        }
    }

    /**
     * 供应商维度判定。
     *
     * @param ownerType 对方类型
     * @return true 表示供应商
     */
    private boolean isSupplier(String ownerType) {
        return OWNER_SUPPLIER.equals(ownerType);
    }

    /**
     * 校验对方类型合法 + 对方单位存在,返回编码/名称。
     *
     * @param ownerType 对方类型
     * @param ownerId   对方 ID
     * @return 编码/名称
     */
    private OwnerInfo requireOwner(String ownerType, long ownerId) {
        requireOwnerType(ownerType);
        if (OWNER_SUPPLIER.equals(ownerType)) {
            SupplierDO supplier = supplierMapper.selectById(ownerId);
            if (supplier == null) {
                throw new BizException("供应商不存在: id=" + ownerId);
            }
            return new OwnerInfo(supplier.getSupplierCode(), supplier.getSupplierName());
        }
        CustomerDO customer = customerMapper.selectById(ownerId);
        if (customer == null) {
            throw new BizException("客户不存在: id=" + ownerId);
        }
        return new OwnerInfo(customer.getCustomerCode(), customer.getCustomerName());
    }

    /**
     * 校验价目行:至少 1 条(Bean Validation 兜底)、同表同物品唯一。
     *
     * @param dto 入参
     */
    private void requireLines(PriceListCreateDTO dto) {
        if (dto.lines() == null || dto.lines().isEmpty()) {
            throw new BizException("价目行至少 1 条");
        }
        Set<Long> seen = new HashSet<>();
        for (PriceListLineDTO line : dto.lines()) {
            if (!seen.add(line.itemId())) {
                throw new BizException("价目行物品重复: 物品 ID=" + line.itemId());
            }
        }
    }

    /**
     * 批量插入价目行。
     *
     * @param listId   价目表 ID
     * @param lines    行入参
     * @param username 操作人
     */
    private void insertLines(long listId, List<PriceListLineDTO> lines, String username) {
        for (PriceListLineDTO line : lines) {
            PriceListItemDO item = new PriceListItemDO();
            item.setPriceListId(listId);
            item.setItemId(line.itemId());
            item.setUnitPrice(line.unitPrice());
            item.setTaxRate(line.taxRate());
            item.setCreator(username);
            priceListItemMapper.insert(item);
        }
    }

    /**
     * 查价目表,不存在则 400。
     *
     * @param id 价目表 ID
     * @return 实体
     */
    private PriceListDO requireList(long id) {
        PriceListDO list = priceListMapper.selectById(id);
        if (list == null) {
            throw new BizException("价目表不存在: id=" + id);
        }
        return list;
    }

    /** 对方单位编码/名称。 */
    private record OwnerInfo(String code, String name) {
    }

}
