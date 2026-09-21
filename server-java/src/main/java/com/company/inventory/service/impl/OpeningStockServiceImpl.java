package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.inbound.InboundCreateDTO;
import com.company.inventory.model.dto.inbound.InboundLineDTO;
import com.company.inventory.model.dto.opening.OpeningStockCreateDTO;
import com.company.inventory.model.dto.opening.OpeningStockLineDTO;
import com.company.inventory.model.entity.inbound.InboundDocDO;
import com.company.inventory.model.entity.inbound.InboundDocItemDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.opening.OpeningStockDocDO;
import com.company.inventory.model.entity.opening.OpeningStockDocItemDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.InboundDocItemMapper;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.OpeningStockDocItemMapper;
import com.company.inventory.mapper.OpeningStockDocMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.OpeningStockQuery;
import com.company.inventory.model.vo.opening.OpeningStockCreatedVO;
import com.company.inventory.model.vo.opening.OpeningStockDocItemVO;
import com.company.inventory.model.vo.opening.OpeningStockDocVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.OpeningStockService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 期初库存服务实现:整单一个事务,期初头 + 行 + 入库过账(现有链路)同事务。
 *
 * <p>过账方式:服务端构造 InboundCreateDTO(refType=opening,refDocId=期初单 ID)调
 * InboundService.create,库存/流水/批次全部走现有入库规则,StockCoreService 零改动。
 * 校验:仓库存在、序列号仓拒收、批次/保质期仓必填批次号、物品存在、同单物品不重复、
 * 每物品×仓库限一次期初(命中即整单回滚)。</p>
 *
 * @author inventory
 */
@Service
public class OpeningStockServiceImpl implements OpeningStockService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(OpeningStockServiceImpl.class);

    /** 期初单 Mapper。 */
    private final OpeningStockDocMapper openingStockDocMapper;
    /** 期初单行 Mapper。 */
    private final OpeningStockDocItemMapper openingStockDocItemMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMapper;
    /** 入库单 Mapper(限次校验)。 */
    private final InboundDocMapper inboundDocMapper;
    /** 入库单行 Mapper(限次校验)。 */
    private final InboundDocItemMapper inboundDocItemMapper;
    /** 入库单服务(过账走现有链路)。 */
    private final InboundService inboundService;
    /** 单据号服务。 */
    private final DocNoService docNoService;

    /**
     * 构造服务。
     *
     * @param openingStockDocMapper 期初单 Mapper
     * @param openingStockDocItemMapper 期初单行 Mapper
     * @param warehouseMapper 仓库 Mapper
     * @param itemMapper 物品 Mapper
     * @param inboundDocMapper 入库单 Mapper
     * @param inboundDocItemMapper 入库单行 Mapper
     * @param inboundService 入库单服务
     * @param docNoService 单据号服务
     */
    public OpeningStockServiceImpl(OpeningStockDocMapper openingStockDocMapper,
            OpeningStockDocItemMapper openingStockDocItemMapper, WarehouseMapper warehouseMapper,
            ItemMapper itemMapper, InboundDocMapper inboundDocMapper,
            InboundDocItemMapper inboundDocItemMapper, InboundService inboundService,
            DocNoService docNoService) {
        this.openingStockDocMapper = openingStockDocMapper;
        this.openingStockDocItemMapper = openingStockDocItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.itemMapper = itemMapper;
        this.inboundDocMapper = inboundDocMapper;
        this.inboundDocItemMapper = inboundDocItemMapper;
        this.inboundService = inboundService;
        this.docNoService = docNoService;
    }

    /**
     * 新建期初单并过账(单事务,任一失败整单回滚)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 单据头
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpeningStockCreatedVO create(OpeningStockCreateDTO dto, String username) {
        WarehouseDO warehouse = warehouseMapper.selectById(dto.warehouseId());
        if (warehouse == null) {
            throw new BizException("仓库不存在: id=" + dto.warehouseId());
        }
        if (Boolean.TRUE.equals(warehouse.getEnableSerial())) {
            // 期初不采集序列号,无法通过序列号仓入库校验,直接拒
            throw new BizException("期初不支持序列号物品,请走入库单");
        }
        // 批次/保质期仓必填批次号(与入库行规则对齐:启用保质期的仓入库必须指定批次)
        boolean batchRequired = Boolean.TRUE.equals(warehouse.getEnableBatch())
                || Boolean.TRUE.equals(warehouse.getEnableExpiry());
        List<ItemDO> items = loadItems(dto);
        if (batchRequired) {
            for (int i = 0; i < dto.items().size(); i++) {
                if (!StringUtils.hasText(dto.items().get(i).batchNo())) {
                    throw new BizException("第 " + (i + 1) + " 行:批次仓期初必须填写批次号");
                }
            }
        }
        // 限次校验:该仓已有 finished 期初入库的物品,本单出现即整单拒
        Set<Long> opened = findOpenedItemIds(dto.warehouseId(), items);
        for (ItemDO item : items) {
            if (opened.contains(item.getId())) {
                throw new BizException("物品 " + item.getItemCode() + " 在此仓库已有期初");
            }
        }

        OpeningStockDocDO doc = new OpeningStockDocDO();
        doc.setDocNo(docNoService.generateOpeningDocNo());
        doc.setDocDate(dto.docDate());
        doc.setWarehouseId(dto.warehouseId());
        doc.setStatus(ErrorCode.DOC_STATUS_FINISHED);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        BigDecimal totalQty = BigDecimal.ZERO;
        List<InboundLineDTO> inboundLines = new ArrayList<>();
        List<ItemDO> itemRows = loadItems(dto);
        for (int i = 0; i < dto.items().size(); i++) {
            OpeningStockLineDTO line = dto.items().get(i);
            totalQty = totalQty.add(line.quantity());
            inboundLines.add(new InboundLineDTO(line.itemId(), line.quantity(), line.batchNo(),
                    line.productionDate(), line.expiryDate(), null, line.locationId(), null,
                    line.unitPrice(), null, null, null));
        }
        doc.setTotalQty(totalQty);
        openingStockDocMapper.insert(doc);
        // 头先插(取 id),行随后插;过账失败时整事务回滚
        for (int i = 0; i < dto.items().size(); i++) {
            ItemDO item = itemRows.get(i);
            OpeningStockDocItemDO docItem = new OpeningStockDocItemDO();
            docItem.setDocId(doc.getId());
            docItem.setLineNo(i + 1);
            docItem.setItemId(item.getId());
            docItem.setSpecSnapshot(item.getSpec());
            docItem.setUnit(item.getUnit());
            docItem.setQuantity(dto.items().get(i).quantity());
            docItem.setUnitPrice(dto.items().get(i).unitPrice());
            docItem.setBatchNo(dto.items().get(i).batchNo());
            docItem.setProductionDate(dto.items().get(i).productionDate());
            docItem.setExpiryDate(dto.items().get(i).expiryDate());
            docItem.setLocationId(dto.items().get(i).locationId());
            docItem.setCreator(username);
            openingStockDocItemMapper.insert(docItem);
        }
        // 过账:走现有入库链路(同事务,失败整单回滚)
        InboundCreateDTO inboundDto = new InboundCreateDTO(dto.warehouseId(), dto.remark(),
                inboundLines, ErrorCode.REF_TYPE_OPENING, doc.getId(), dto.docDate(),
                null, null, null, "期初", null);
        inboundService.create(inboundDto, username);
        LOGGER.info("新建期初单: docNo={}, warehouseId={}, 行数={}, 总量={}, operator={}",
                doc.getDocNo(), doc.getWarehouseId(), itemRows.size(),
                QtyUtils.toContractString(totalQty), username);
        return new OpeningStockCreatedVO(doc.getId(), doc.getDocNo(), doc.getWarehouseId(),
                doc.getStatus(), doc.getRemark(), doc.getCreator(), doc.getCreatedAt());
    }

    /**
     * 期初单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<OpeningStockDocVO> list(OpeningStockQuery query) {
        // 数据权限:未授权用户查空;授权用户只查授权仓(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<OpeningStockDocDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(OpeningStockDocDO::getWarehouseId, allowed);
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(OpeningStockDocDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(OpeningStockDocDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(OpeningStockDocDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(OpeningStockDocDO::getDocDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(OpeningStockDocDO::getDocDate, to);
        }
        wrapper.orderByDesc(OpeningStockDocDO::getId);
        Page<OpeningStockDocDO> result = openingStockDocMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<OpeningStockDocDO> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(),
                    query.getPage(), query.getPageSize());
        }
        return PageResult.of(toVOs(rows), result.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 期初单详情。
     *
     * @param id 单据 ID
     * @return 单据(含仓库与单据行)
     */
    @Override
    public OpeningStockDocVO get(long id) {
        OpeningStockDocDO doc = openingStockDocMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("期初单不存在");
        }
        List<OpeningStockDocVO> vos = toVOs(List.of(doc));
        return vos.get(0);
    }

    /**
     * 批量取期初行物品对象(一次查库,空集合防护,物品不存在/同单重复拒绝整单)。
     *
     * @param dto 入参
     * @return 物品列表(与行一一对应)
     */
    private List<ItemDO> loadItems(OpeningStockCreateDTO dto) {
        Set<Long> itemIds = new HashSet<>();
        for (OpeningStockLineDTO line : dto.items()) {
            if (!itemIds.add(line.itemId())) {
                throw new BizException("期初行物品重复: id=" + line.itemId());
            }
        }
        if (itemIds.isEmpty()) {
            return List.of();
        }
        List<ItemDO> rows = itemMapper.selectByIds(itemIds);
        Map<Long, ItemDO> map = rows.stream()
                .collect(Collectors.toMap(ItemDO::getId, Function.identity()));
        List<ItemDO> items = new ArrayList<>();
        for (OpeningStockLineDTO line : dto.items()) {
            ItemDO item = map.get(line.itemId());
            if (item == null) {
                throw new BizException("物品不存在: id=" + line.itemId());
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 限次校验查询:该仓库已完成期初入库(ref_type=opening 且 finished)覆盖的物品 ID 集合。
     *
     * @param warehouseId 仓库 ID
     * @param items       本单物品列表(空则直接返回空集)
     * @return 已期初物品 ID 集合(与本单物品求交)
     */
    private Set<Long> findOpenedItemIds(Long warehouseId, List<ItemDO> items) {
        if (items.isEmpty()) {
            return Set.of();
        }
        List<InboundDocDO> docs = inboundDocMapper.selectList(new LambdaQueryWrapper<InboundDocDO>()
                .eq(InboundDocDO::getRefType, ErrorCode.REF_TYPE_OPENING)
                .eq(InboundDocDO::getStatus, ErrorCode.DOC_STATUS_FINISHED)
                .eq(InboundDocDO::getWarehouseId, warehouseId));
        if (docs.isEmpty()) {
            return Set.of();
        }
        Set<Long> docIds = docs.stream().map(InboundDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        List<ItemDO> thisDocItems = items;
        Set<Long> opened = new HashSet<>();
        for (InboundDocItemDO item : inboundDocItemMapper.selectList(
                new LambdaQueryWrapper<InboundDocItemDO>()
                        .select(InboundDocItemDO::getItemId)
                        .in(InboundDocItemDO::getDocId, docIds))) {
            if (thisDocItems.stream().anyMatch(t -> t.getId().equals(item.getItemId()))) {
                opened.add(item.getItemId());
            }
        }
        return opened;
    }

    /**
     * 批量组装单据 VO(仓库 + 单据行 + 物品编码/名称)。
     *
     * @param docs 单据列表
     * @return VO 列表
     */
    private List<OpeningStockDocVO> toVOs(List<OpeningStockDocDO> docs) {
        Set<Long> whIds = docs.stream().map(OpeningStockDocDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(OpeningStockDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        List<OpeningStockDocItemDO> allItems = openingStockDocItemMapper.selectList(
                new LambdaQueryWrapper<OpeningStockDocItemDO>()
                        .in(OpeningStockDocItemDO::getDocId, docIds)
                        .orderByAsc(OpeningStockDocItemDO::getId));
        Set<Long> itemIds = allItems.stream().map(OpeningStockDocItemDO::getItemId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, ItemDO> itemMap = (itemIds.isEmpty() ? List.<ItemDO>of()
                : itemMapper.selectByIds(itemIds)).stream()
                .collect(Collectors.toMap(ItemDO::getId, Function.identity()));
        Map<Long, List<OpeningStockDocItemDO>> itemRowsByDoc = allItems.stream()
                .collect(Collectors.groupingBy(OpeningStockDocItemDO::getDocId));

        List<OpeningStockDocVO> vos = new ArrayList<>();
        for (OpeningStockDocDO doc : docs) {
            List<OpeningStockDocItemDO> rows =
                    itemRowsByDoc.getOrDefault(doc.getId(), List.of());
            List<OpeningStockDocItemVO> itemVos = new ArrayList<>();
            for (OpeningStockDocItemDO row : rows) {
                ItemDO item = itemMap.get(row.getItemId());
                itemVos.add(new OpeningStockDocItemVO(row.getId(), row.getDocId(),
                        row.getLineNo(), row.getItemId(),
                        item == null ? null : item.getItemCode(),
                        item == null ? null : item.getItemName(),
                        row.getSpecSnapshot(), row.getUnit(),
                        QtyUtils.toContractString(row.getQuantity()), row.getUnitPrice(),
                        row.getBatchNo(), row.getProductionDate(), row.getExpiryDate(),
                        row.getLocationId()));
            }
            vos.add(new OpeningStockDocVO(doc.getId(), doc.getDocNo(), doc.getDocDate(),
                    doc.getWarehouseId(), QtyUtils.toContractString(doc.getTotalQty()),
                    doc.getRemark(), doc.getStatus(), doc.getCreator(), doc.getCreatedAt(),
                    whMap.get(doc.getWarehouseId()), itemVos));
        }
        return vos;
    }

    /**
     * 实体转仓库 VO。
     *
     * @param warehouse 实体
     * @return VO
     */
    private WarehouseVO toWarehouseVO(WarehouseDO warehouse) {
        return new WarehouseVO(warehouse.getId(), warehouse.getWarehouseCode(),
                warehouse.getWarehouseName(), warehouse.getWarehouseType(),
                warehouse.getEnableBatch(), warehouse.getEnableExpiry(),
                warehouse.getEnableSerial(), warehouse.getEnableLocation(),
                warehouse.getStatus(), warehouse.getCreatedAt());
    }
}
