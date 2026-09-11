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
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.adjust.StockAdjustDocDO;
import com.company.inventory.model.entity.adjust.StockAdjustDocItemDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.StockAdjustDocItemMapper;
import com.company.inventory.mapper.StockAdjustDocMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.StockAdjustQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.StockAdjustService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.model.vo.adjust.StockAdjustDocItemVO;
import com.company.inventory.model.vo.adjust.StockAdjustDocVO;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存调整单服务实现:gain 调整入库 / loss、scrap 调整出库,审批通过时执行库存动作并 completed
 * (同事务,失败整单回滚可重试)。
 *
 * @author inventory
 */
@Service
public class StockAdjustServiceImpl implements StockAdjustService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(StockAdjustServiceImpl.class);

    /** 单据中文名(错误提示用)。 */
    private static final String DOC_NAME = "库存调整单";

    /** 表头 Mapper。 */
    private final StockAdjustDocMapper docMapper;
    /** 行 Mapper。 */
    private final StockAdjustDocItemMapper itemMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMasterMapper;
    /** 批次 Mapper(调整行携带批次时取批次号)。 */
    private final BatchMapper batchMapper;
    /** 审批资格校验。 */
    private final ApprovalGuard approvalGuard;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;
    /** 库存核心服务。 */
    private final StockCoreService stockCoreService;

    /**
     * 构造服务。
     *
     * @param docMapper        表头 Mapper
     * @param itemMapper       行 Mapper
     * @param warehouseMapper  仓库 Mapper
     * @param itemMasterMapper 物品 Mapper
     * @param batchMapper      批次 Mapper
     * @param approvalGuard    审批资格校验
     * @param docNoService     单据号服务
     * @param stateSupport     状态机支撑
     * @param stockCoreService 库存核心服务
     */
    public StockAdjustServiceImpl(StockAdjustDocMapper docMapper, StockAdjustDocItemMapper itemMapper,
            WarehouseMapper warehouseMapper, ItemMapper itemMasterMapper, BatchMapper batchMapper,
            ApprovalGuard approvalGuard, DocNoService docNoService, DocStateSupport stateSupport,
            StockCoreService stockCoreService) {
        this.docMapper = docMapper;
        this.itemMapper = itemMapper;
        this.warehouseMapper = warehouseMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.batchMapper = batchMapper;
        this.approvalGuard = approvalGuard;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
        this.stockCoreService = stockCoreService;
    }

    /**
     * 新建调整单(draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustDocVO create(StockAdjustCreateDTO dto, String username) {
        WarehouseDO warehouse = warehouseMapper.selectById(dto.warehouseId());
        if (warehouse == null) {
            throw new BizException("仓库不存在: id=" + dto.warehouseId());
        }
        if (!ErrorCode.ADJUST_TYPE_GAIN.equals(dto.adjustType())
                && !ErrorCode.ADJUST_TYPE_LOSS.equals(dto.adjustType())
                && !ErrorCode.ADJUST_TYPE_SCRAP.equals(dto.adjustType())) {
            throw new BizException("调整类型仅支持 gain/loss/scrap");
        }
        Set<Long> itemIds = dto.items().stream().map(StockAdjustLineDTO::itemId).collect(Collectors.toSet());
        List<ItemDO> items = itemIds.isEmpty() ? List.of() : itemMasterMapper.selectByIds(itemIds);
        Map<Long, ItemDO> itemMap = new HashMap<>();
        for (ItemDO it : items) {
            itemMap.put(it.getId(), it);
        }

        StockAdjustDocDO doc = new StockAdjustDocDO();
        doc.setDocNo(docNoService.generateAdjustDocNo());
        doc.setDocDate(dto.docDate());
        doc.setWarehouseId(dto.warehouseId());
        doc.setAdjustType(dto.adjustType());
        doc.setRefDocNo(dto.refDocNo());
        doc.setStatus(DocStatus.DRAFT);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);
        for (int i = 0; i < dto.items().size(); i++) {
            StockAdjustLineDTO line = dto.items().get(i);
            ItemDO item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException("物品不存在: id=" + line.itemId());
            }
            StockAdjustDocItemDO oi = new StockAdjustDocItemDO();
            oi.setDocId(doc.getId());
            oi.setLineNo(i + 1);
            oi.setItemId(line.itemId());
            oi.setSpecSnapshot(item.getSpec());
            oi.setUnit(item.getUnit());
            oi.setBatchId(line.batchId() == null ? 0L : line.batchId());
            oi.setLocationId(line.locationId() == null ? 0L : line.locationId());
            oi.setQty(line.qty());
            oi.setUnitPrice(line.unitPrice());
            oi.setReason(line.reason());
            itemMapper.insert(oi);
        }
        LOGGER.info("新建调整单: docNo={}, 类型={}, 行数={}, operator={}",
                doc.getDocNo(), dto.adjustType(), dto.items().size(), username);
        return get(doc.getId());
    }

    /**
     * 调整单分页列表。
     */
    @Override
    public PageResult<StockAdjustDocVO> list(StockAdjustQuery query) {
        LambdaQueryWrapper<StockAdjustDocDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getWarehouseId() != null) {
            wrapper.eq(StockAdjustDocDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getAdjustType())) {
            wrapper.eq(StockAdjustDocDO::getAdjustType, query.getAdjustType().trim());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(StockAdjustDocDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(StockAdjustDocDO::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(StockAdjustDocDO::getId);
        Page<StockAdjustDocDO> page = docMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 调整单详情。
     */
    @Override
    public StockAdjustDocVO get(long id) {
        StockAdjustDocDO doc = docMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("库存调整单不存在");
        }
        return toVOs(List.of(doc)).get(0);
    }

    /**
     * 提交:draft/rejected → pending。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustDocVO submit(long id, String username) {
        StockAdjustDocDO doc = requireDoc(id);
        stateSupport.assertWritable(doc.getStatus(), DOC_NAME);
        int n = stateSupport.transition(docMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.REJECTED), DocStatus.PENDING, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 审批执行:pending → completed(同事务执行库存动作,失败整单回滚可重试)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustDocVO approve(long id, String username) {
        StockAdjustDocDO doc = requireDoc(id);
        if (DocStatus.COMPLETED.equals(doc.getStatus())) {
            return get(id);
        }
        if (!DocStatus.PENDING.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可审批执行");
        }
        approvalGuard.assertApprovable(username, doc.getCreator());
        Map<String, Object> extra = new HashMap<>();
        extra.put("approver", username);
        extra.put("approved_at", LocalDateTime.now());
        int n = stateSupport.transitionWith(docMapper, id, List.of(DocStatus.PENDING),
                DocStatus.APPROVED, username, extra);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        doExecute(doc);
        stateSupport.transition(docMapper, id, List.of(DocStatus.APPROVED),
                DocStatus.COMPLETED, username);
        LOGGER.info("调整单审批执行完成: id={}, docNo={}, 类型={}, approver={}",
                id, doc.getDocNo(), doc.getAdjustType(), username);
        return get(id);
    }

    /**
     * 驳回:pending → rejected(原因必填)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustDocVO reject(long id, String rejectReason, String username) {
        StockAdjustDocDO doc = requireDoc(id);
        if (!DocStatus.PENDING.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可驳回");
        }
        approvalGuard.assertApprovable(username, doc.getCreator());
        if (!StringUtils.hasText(rejectReason)) {
            throw new BizException("驳回原因必填");
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("approver", username);
        extra.put("approved_at", LocalDateTime.now());
        extra.put("reject_reason", rejectReason.trim());
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
    public StockAdjustDocVO voidDoc(long id, String username) {
        StockAdjustDocDO doc = requireDoc(id);
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
     * 执行调整(加入调用方事务):gain 调整入库,loss/scrap 调整出库。
     *
     * @param doc 调整单
     */
    private void doExecute(StockAdjustDocDO doc) {
        List<StockAdjustDocItemDO> lines = itemMapper.selectList(
                new LambdaQueryWrapper<StockAdjustDocItemDO>()
                        .eq(StockAdjustDocItemDO::getDocId, doc.getId())
                        .orderByAsc(StockAdjustDocItemDO::getLineNo));
        if (lines.isEmpty()) {
            throw new BizException(DOC_NAME + "无调整行,不可执行");
        }
        boolean gain = ErrorCode.ADJUST_TYPE_GAIN.equals(doc.getAdjustType());
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(doc.getWarehouseId());
        request.setDocNo(doc.getDocNo());
        request.setOperator(doc.getApprover());
        request.setBizCode(gain ? ErrorCode.BIZ_CODE_ADJUST_IN : ErrorCode.BIZ_CODE_ADJUST_OUT);
        List<StockLine> stockLines = new ArrayList<>();
        for (StockAdjustDocItemDO line : lines) {
            StockLine sl = new StockLine();
            sl.setItemId(line.getItemId());
            sl.setQty(line.getQty());
            sl.setLocationId(line.getLocationId() == 0 ? null : line.getLocationId());
            if (line.getBatchId() != null && line.getBatchId() != 0L) {
                BatchDO batch = batchMapper.selectById(line.getBatchId());
                if (batch == null) {
                    throw new BizException("批次不存在: id=" + line.getBatchId());
                }
                sl.setBatchNo(batch.getBatchNo());
                sl.setProductionDate(batch.getProductionDate());
                sl.setExpiryDate(batch.getExpiryDate());
            }
            stockLines.add(sl);
        }
        request.setLines(stockLines);
        if (gain) {
            stockCoreService.inbound(request);
        } else {
            stockCoreService.outbound(request);
        }
    }

    /**
     * 查调整单,不存在抛 404。
     *
     * @param id 调整单 ID
     * @return 调整单
     */
    private StockAdjustDocDO requireDoc(long id) {
        StockAdjustDocDO doc = docMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("库存调整单不存在");
        }
        return doc;
    }

    /**
     * 批量组装 VO(仓库 + 物品 + 行)。
     *
     * @param docs 调整单列表
     * @return VO 列表
     */
    private List<StockAdjustDocVO> toVOs(List<StockAdjustDocDO> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Set<Long> whIds = docs.stream().map(StockAdjustDocDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(StockAdjustDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        List<StockAdjustDocItemDO> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<StockAdjustDocItemDO>()
                        .in(StockAdjustDocItemDO::getDocId, docIds)
                        .orderByAsc(StockAdjustDocItemDO::getLineNo));
        Set<Long> itemIds = new HashSet<>();
        for (StockAdjustDocItemDO it : allItems) {
            itemIds.add(it.getItemId());
        }
        Map<Long, ItemDO> itemMap = (itemIds.isEmpty() ? List.<ItemDO>of()
                : itemMasterMapper.selectByIds(itemIds)).stream()
                .collect(Collectors.toMap(ItemDO::getId, it -> it));
        Map<Long, List<StockAdjustDocItemDO>> linesByDoc = allItems.stream()
                .collect(Collectors.groupingBy(StockAdjustDocItemDO::getDocId));

        List<StockAdjustDocVO> vos = new ArrayList<>();
        for (StockAdjustDocDO doc : docs) {
            List<StockAdjustDocItemDO> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            List<StockAdjustDocItemVO> lineVos = new ArrayList<>();
            for (StockAdjustDocItemDO line : lines) {
                ItemDO item = itemMap.get(line.getItemId());
                lineVos.add(new StockAdjustDocItemVO(line.getId(), line.getLineNo(), line.getItemId(),
                        item == null ? null : item.getItemCode(),
                        item == null ? null : item.getItemName(),
                        line.getSpecSnapshot(), line.getUnit(), line.getBatchId(),
                        line.getLocationId(), QtyUtils.toContractString(line.getQty()),
                        line.getUnitPrice(), line.getReason()));
            }
            vos.add(new StockAdjustDocVO(doc.getId(), doc.getDocNo(), doc.getDocDate(),
                    doc.getWarehouseId(), whMap.get(doc.getWarehouseId()), doc.getAdjustType(),
                    doc.getRefDocNo(), doc.getStatus(), doc.getCreator(), doc.getCreatedAt(),
                    doc.getUpdater(), doc.getUpdatedAt(), doc.getApprover(), doc.getApprovedAt(),
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
