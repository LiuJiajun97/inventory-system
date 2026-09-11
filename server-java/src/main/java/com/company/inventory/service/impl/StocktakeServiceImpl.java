package com.company.inventory.service.impl;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.ApprovalGuard;
import com.company.inventory.common.support.DocStateSupport;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.adjust.StockAdjustCreateDTO;
import com.company.inventory.model.dto.adjust.StockAdjustLineDTO;
import com.company.inventory.model.dto.stocktake.StocktakeActualDTO;
import com.company.inventory.model.dto.stocktake.StocktakeActualLineDTO;
import com.company.inventory.model.dto.stocktake.StocktakeCreateDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.StockDO;
import com.company.inventory.model.entity.stocktake.StocktakeDocDO;
import com.company.inventory.model.entity.stocktake.StocktakeDocItemDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockMapper;
import com.company.inventory.mapper.StocktakeDocItemMapper;
import com.company.inventory.mapper.StocktakeDocMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.StocktakeDocQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.StockAdjustService;
import com.company.inventory.service.StocktakeService;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;
import com.company.inventory.model.vo.stocktake.StocktakeDocItemVO;
import com.company.inventory.model.vo.stocktake.StocktakeDocVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 盘点单服务实现:保存即快照 bookQty + 实盘录入 + 刷新快照(拍板点 3 不冻结)+ 差异生成调整单。
 *
 * @author inventory
 */
@Service
public class StocktakeServiceImpl implements StocktakeService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(StocktakeServiceImpl.class);

    /** 单据中文名(错误提示用)。 */
    private static final String DOC_NAME = "盘点单";

    /** 表头 Mapper。 */
    private final StocktakeDocMapper docMapper;
    /** 行 Mapper。 */
    private final StocktakeDocItemMapper itemMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMasterMapper;
    /** 库存 Mapper(快照来源)。 */
    private final StockMapper stockMapper;
    /** 审批资格校验。 */
    private final ApprovalGuard approvalGuard;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;
    /** 调整单服务(差异生成;懒注入避免循环依赖)。 */
    private final StockAdjustService stockAdjustService;

    /**
     * 构造服务。
     *
     * @param docMapper          表头 Mapper
     * @param itemMapper         行 Mapper
     * @param warehouseMapper    仓库 Mapper
     * @param itemMasterMapper   物品 Mapper
     * @param stockMapper        库存 Mapper
     * @param approvalGuard      审批资格校验
     * @param docNoService       单据号服务
     * @param stateSupport       状态机支撑
     * @param stockAdjustService 调整单服务
     */
    public StocktakeServiceImpl(StocktakeDocMapper docMapper, StocktakeDocItemMapper itemMapper,
            WarehouseMapper warehouseMapper, ItemMapper itemMasterMapper, StockMapper stockMapper,
            ApprovalGuard approvalGuard, DocNoService docNoService, DocStateSupport stateSupport,
            @Lazy StockAdjustService stockAdjustService) {
        this.docMapper = docMapper;
        this.itemMapper = itemMapper;
        this.warehouseMapper = warehouseMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.stockMapper = stockMapper;
        this.approvalGuard = approvalGuard;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
        this.stockAdjustService = stockAdjustService;
    }

    /**
     * 新建盘点单:保存即按当前余额生成 bookQty 快照行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDocVO create(StocktakeCreateDTO dto, String username) {
        WarehouseDO warehouse = warehouseMapper.selectById(dto.warehouseId());
        if (warehouse == null) {
            throw new BizException("仓库不存在: id=" + dto.warehouseId());
        }
        String scope = StringUtils.hasText(dto.scopeType()) ? dto.scopeType() : ErrorCode.SCOPE_ALL;
        if (!ErrorCode.SCOPE_ALL.equals(scope) && !ErrorCode.SCOPE_ITEM.equals(scope)) {
            throw new BizException("盘点范围仅支持 all/item");
        }
        if (ErrorCode.SCOPE_ITEM.equals(scope)
                && (dto.itemIds() == null || dto.itemIds().isEmpty())) {
            throw new BizException("指定物品盘点必须提供物品 ID 列表");
        }
        StocktakeDocDO doc = new StocktakeDocDO();
        doc.setDocNo(docNoService.generateStocktakeDocNo());
        doc.setDocDate(dto.docDate());
        doc.setWarehouseId(dto.warehouseId());
        doc.setScopeType(scope);
        doc.setStatus(DocStatus.DRAFT);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);

        LambdaQueryWrapper<StockDO> stockWrapper = new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, dto.warehouseId())
                .ne(StockDO::getQuantity, BigDecimal.ZERO);
        if (dto.itemIds() != null && !dto.itemIds().isEmpty()) {
            stockWrapper.in(StockDO::getItemId, dto.itemIds());
        }
        stockWrapper.orderByAsc(StockDO::getId);
        List<StockDO> stocks = stockMapper.selectList(stockWrapper);
        if (stocks.isEmpty()) {
            throw new BizException("当前条件下无库存,不能创建盘点单");
        }
        applySnapshot(doc, stocks, 1);
        LOGGER.info("新建盘点单: docNo={}, 仓库={}, 快照行数={}, operator={}",
                doc.getDocNo(), dto.warehouseId(), stocks.size(), username);
        return get(doc.getId());
    }

    /**
     * 盘点单分页列表。
     */
    @Override
    public PageResult<StocktakeDocVO> list(StocktakeDocQuery query) {
        LambdaQueryWrapper<StocktakeDocDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getWarehouseId() != null) {
            wrapper.eq(StocktakeDocDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(StocktakeDocDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(StocktakeDocDO::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(StocktakeDocDO::getId);
        Page<StocktakeDocDO> page = docMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 盘点单详情。
     */
    @Override
    public StocktakeDocVO get(long id) {
        StocktakeDocDO doc = docMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("盘点单不存在");
        }
        return toVOs(List.of(doc)).get(0);
    }

    /**
     * 录入实盘(draft/pending 可录),重算 diffQty。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDocVO enterActual(long id, StocktakeActualDTO dto, String username) {
        StocktakeDocDO doc = requireDoc(id);
        if (!DocStatus.DRAFT.equals(doc.getStatus()) && !DocStatus.PENDING.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿/待审批状态可录入实盘");
        }
        Set<Long> lineIds = dto.lines().stream().map(StocktakeActualLineDTO::lineId).collect(Collectors.toSet());
        List<StocktakeDocItemDO> lines = itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeDocItemDO>()
                        .eq(StocktakeDocItemDO::getDocId, id));
        Map<Long, StocktakeDocItemDO> lineMap = lines.stream()
                .collect(Collectors.toMap(StocktakeDocItemDO::getId, l -> l));
        for (StocktakeActualLineDTO line : dto.lines()) {
            StocktakeDocItemDO target = lineMap.get(line.lineId());
            if (target == null) {
                throw new BizException("盘点行不存在或不属于该单: " + line.lineId());
            }
            target.setActualQty(line.actualQty());
            target.setDiffQty(line.actualQty() == null ? null
                    : line.actualQty().subtract(target.getBookQty()));
            itemMapper.updateById(target);
        }
        doc.setUpdater(username);
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        LOGGER.info("盘点实盘录入: docId={}, 行数={}, operator={}", id, lineIds.size(), username);
        return get(id);
    }

    /**
     * 刷新快照(draft 可刷):重取 bookQty,新增余额行补快照,已录实盘不覆盖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDocVO refreshBook(long id, String username) {
        StocktakeDocDO doc = requireDoc(id);
        if (!DocStatus.DRAFT.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿状态可刷新快照");
        }
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>()
                .eq(StockDO::getWarehouseId, doc.getWarehouseId())
                .ne(StockDO::getQuantity, BigDecimal.ZERO)
                .orderByAsc(StockDO::getId));
        List<StocktakeDocItemDO> existing = itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeDocItemDO>()
                        .eq(StocktakeDocItemDO::getDocId, id));
        Map<String, StocktakeDocItemDO> existingMap = existing.stream()
                .collect(Collectors.toMap(this::lineKey, l -> l));
        int lineNo = existing.isEmpty() ? 1 : existing.stream()
                .mapToInt(StocktakeDocItemDO::getLineNo).max().orElse(0) + 1;
        for (StockDO stock : stocks) {
            String key = lineKey(doc.getWarehouseId(), stock.getItemId(),
                    stock.getBatchId(), stock.getLocationId());
            StocktakeDocItemDO line = existingMap.get(key);
            if (line == null) {
                ItemDO item = itemMasterMapper.selectById(stock.getItemId());
                StocktakeDocItemDO fresh = new StocktakeDocItemDO();
                fresh.setDocId(id);
                fresh.setLineNo(lineNo++);
                fresh.setItemId(stock.getItemId());
                fresh.setSpecSnapshot(item == null ? null : item.getSpec());
                fresh.setUnit(item == null ? null : item.getUnit());
                fresh.setBatchId(stock.getBatchId());
                fresh.setLocationId(stock.getLocationId());
                fresh.setBookQty(stock.getQuantity());
                itemMapper.insert(fresh);
            } else {
                line.setBookQty(stock.getQuantity());
                line.setDiffQty(line.getActualQty() == null ? null
                        : line.getActualQty().subtract(stock.getQuantity()));
                itemMapper.updateById(line);
            }
        }
        doc.setUpdater(username);
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        LOGGER.info("盘点刷新快照: docId={}, operator={}", id, username);
        return get(id);
    }

    /**
     * 提交:draft/rejected → pending。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDocVO submit(long id, String username) {
        StocktakeDocDO doc = requireDoc(id);
        stateSupport.assertWritable(doc.getStatus(), DOC_NAME);
        int n = stateSupport.transition(docMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.REJECTED), DocStatus.PENDING, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 审批通过:pending → approved。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDocVO approve(long id, String username) {
        StocktakeDocDO doc = requireDoc(id);
        if (DocStatus.APPROVED.equals(doc.getStatus())) {
            return get(id);
        }
        if (!DocStatus.PENDING.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可审批");
        }
        approvalGuard.assertApprovable(username, doc.getCreator());
        Map<String, Object> extra = new HashMap<>();
        extra.put("\"approver\"", username);
        extra.put("\"approvedAt\"", LocalDateTime.now());
        int n = stateSupport.transitionWith(docMapper, id, List.of(DocStatus.PENDING),
                DocStatus.APPROVED, username, extra);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 驳回:pending → rejected(原因必填)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDocVO reject(long id, String rejectReason, String username) {
        StocktakeDocDO doc = requireDoc(id);
        if (!DocStatus.PENDING.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可驳回");
        }
        approvalGuard.assertApprovable(username, doc.getCreator());
        if (!StringUtils.hasText(rejectReason)) {
            throw new BizException("驳回原因必填");
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("\"approver\"", username);
        extra.put("\"approvedAt\"", LocalDateTime.now());
        extra.put("\"rejectReason\"", rejectReason.trim());
        int n = stateSupport.transitionWith(docMapper, id, List.of(DocStatus.PENDING),
                DocStatus.REJECTED, username, extra);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 作废:任意未执行状态 → voided。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDocVO voidDoc(long id, String username) {
        StocktakeDocDO doc = requireDoc(id);
        stateSupport.assertWritable(doc.getStatus(), DOC_NAME);
        int n = stateSupport.transition(docMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.PENDING, DocStatus.REJECTED, DocStatus.APPROVED),
                DocStatus.VOIDED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 差异生成调整单:盘盈 gain / 盘亏 loss 各一张草稿调整单,refDocNo 记录来源盘点单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StockAdjustDocVO> generateAdjust(long id, String username) {
        StocktakeDocDO doc = requireDoc(id);
        stateSupport.assertWritable(doc.getStatus(), DOC_NAME);
        List<StocktakeDocItemDO> lines = itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeDocItemDO>()
                        .eq(StocktakeDocItemDO::getDocId, id));
        List<StockAdjustLineDTO> gainLines = new ArrayList<>();
        List<StockAdjustLineDTO> lossLines = new ArrayList<>();
        for (StocktakeDocItemDO line : lines) {
            if (line.getActualQty() == null || line.getDiffQty() == null) {
                continue;
            }
            if (line.getDiffQty().signum() == 0) {
                continue;
            }
            StockAdjustLineDTO al = new StockAdjustLineDTO(line.getItemId(),
                    line.getDiffQty().abs(), null,
                    line.getBatchId() == 0 ? null : line.getBatchId(),
                    line.getLocationId() == 0 ? null : line.getLocationId(),
                    "盘点差异(" + doc.getDocNo() + "行" + line.getLineNo() + ")");
            if (line.getDiffQty().signum() > 0) {
                gainLines.add(al);
            } else {
                lossLines.add(al);
            }
        }
        if (gainLines.isEmpty() && lossLines.isEmpty()) {
            throw new BizException("无差异行,无需生成调整单");
        }
        List<StockAdjustDocVO> created = new ArrayList<>();
        if (!gainLines.isEmpty()) {
            created.add(stockAdjustService.create(buildAdjustDoc(doc, ErrorCode.ADJUST_TYPE_GAIN,
                    gainLines), username));
        }
        if (!lossLines.isEmpty()) {
            created.add(stockAdjustService.create(buildAdjustDoc(doc, ErrorCode.ADJUST_TYPE_LOSS,
                    lossLines), username));
        }
        LOGGER.info("盘点差异生成调整单: docId={}, 盘盈行={}, 盘亏行={}, operator={}",
                id, gainLines.size(), lossLines.size(), username);
        return created;
    }

    /**
     * 组装调整单入参。
     *
     * @param doc  盘点单
     * @param type 调整类型
     * @param lines 调整行
     * @return 调整单入参
     */
    private StockAdjustCreateDTO buildAdjustDoc(StocktakeDocDO doc, String type,
            List<StockAdjustLineDTO> lines) {
        return new StockAdjustCreateDTO(doc.getWarehouseId(), java.time.LocalDate.now(), type,
                doc.getDocNo(), "盘点差异生成(" + doc.getDocNo() + ")", lines);
    }

    /**
     * 生成快照行(新建用)。
     *
     * @param doc     盘点单
     * @param stocks  库存行
     * @param startNo 起始行号
     */
    private void applySnapshot(StocktakeDocDO doc, List<StockDO> stocks, int startNo) {
        Set<Long> itemIds = stocks.stream().map(StockDO::getItemId)
                .collect(Collectors.toCollection(HashSet::new));
        List<ItemDO> items = itemIds.isEmpty() ? List.of() : itemMasterMapper.selectByIds(itemIds);
        Map<Long, ItemDO> itemMap = new HashMap<>();
        for (ItemDO it : items) {
            itemMap.put(it.getId(), it);
        }
        int lineNo = startNo;
        for (StockDO stock : stocks) {
            ItemDO item = itemMap.get(stock.getItemId());
            StocktakeDocItemDO line = new StocktakeDocItemDO();
            line.setDocId(doc.getId());
            line.setLineNo(lineNo++);
            line.setItemId(stock.getItemId());
            line.setSpecSnapshot(item == null ? null : item.getSpec());
            line.setUnit(item == null ? null : item.getUnit());
            line.setBatchId(stock.getBatchId());
            line.setLocationId(stock.getLocationId());
            line.setBookQty(stock.getQuantity());
            itemMapper.insert(line);
        }
    }

    /**
     * 盘点行唯一键(仓内 item+batch+location)。
     *
     * @param warehouseId 仓库 ID
     * @param itemId      物品 ID
     * @param batchId     批次 ID
     * @param locationId  库位 ID
     * @return 键
     */
    private String lineKey(long warehouseId, long itemId, long batchId, long locationId) {
        return itemId + ":" + batchId + ":" + locationId;
    }

    /**
     * 盘点行唯一键(重载,直接取行)。
     *
     * @param line 盘点行
     * @return 键
     */
    private String lineKey(StocktakeDocItemDO line) {
        return line.getItemId() + ":" + line.getBatchId() + ":" + line.getLocationId();
    }

    /**
     * 查盘点单,不存在抛 404。
     *
     * @param id 盘点单 ID
     * @return 盘点单
     */
    private StocktakeDocDO requireDoc(long id) {
        StocktakeDocDO doc = docMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("盘点单不存在");
        }
        return doc;
    }

    /**
     * 批量组装 VO(仓库 + 物品 + 行)。
     *
     * @param docs 盘点单列表
     * @return VO 列表
     */
    private List<StocktakeDocVO> toVOs(List<StocktakeDocDO> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Set<Long> whIds = docs.stream().map(StocktakeDocDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(StocktakeDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        List<StocktakeDocItemDO> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<StocktakeDocItemDO>()
                        .in(StocktakeDocItemDO::getDocId, docIds)
                        .orderByAsc(StocktakeDocItemDO::getLineNo));
        Set<Long> itemIds = new HashSet<>();
        for (StocktakeDocItemDO it : allItems) {
            itemIds.add(it.getItemId());
        }
        Map<Long, ItemDO> itemMap = (itemIds.isEmpty() ? List.<ItemDO>of()
                : itemMasterMapper.selectByIds(itemIds)).stream()
                .collect(Collectors.toMap(ItemDO::getId, it -> it));
        Map<Long, List<StocktakeDocItemDO>> linesByDoc = allItems.stream()
                .collect(Collectors.groupingBy(StocktakeDocItemDO::getDocId));

        List<StocktakeDocVO> vos = new ArrayList<>();
        for (StocktakeDocDO doc : docs) {
            List<StocktakeDocItemDO> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            List<StocktakeDocItemVO> lineVos = new ArrayList<>();
            for (StocktakeDocItemDO line : lines) {
                ItemDO item = itemMap.get(line.getItemId());
                lineVos.add(new StocktakeDocItemVO(line.getId(), line.getLineNo(), line.getItemId(),
                        item == null ? null : item.getItemCode(),
                        item == null ? null : item.getItemName(),
                        line.getSpecSnapshot(), line.getUnit(), line.getBatchId(),
                        line.getLocationId(),
                        QtyUtils.toContractString(line.getBookQty()),
                        line.getActualQty() == null ? null : QtyUtils.toContractString(line.getActualQty()),
                        line.getDiffQty() == null ? null : QtyUtils.toContractString(line.getDiffQty())));
            }
            vos.add(new StocktakeDocVO(doc.getId(), doc.getDocNo(), doc.getDocDate(),
                    doc.getWarehouseId(), whMap.get(doc.getWarehouseId()), doc.getScopeType(),
                    doc.getStatus(), doc.getCreator(), doc.getCreatedAt(), doc.getUpdater(),
                    doc.getUpdatedAt(), doc.getApprover(), doc.getApprovedAt(),
                    doc.getRejectReason(), doc.getRemark(), lineVos));
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
