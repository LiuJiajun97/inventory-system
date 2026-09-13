package com.company.inventory.service.impl;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.ApprovalGuard;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.support.DocStateSupport;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.dto.transfer.TransferActionDTO;
import com.company.inventory.model.dto.transfer.TransferCreateDTO;
import com.company.inventory.model.dto.transfer.TransferLineDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.transfer.TransferDocDO;
import com.company.inventory.model.entity.transfer.TransferDocItemDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.TransferDocItemMapper;
import com.company.inventory.mapper.TransferDocMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.TransferDocQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.service.TransferService;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.model.vo.transfer.TransferDocItemVO;
import com.company.inventory.model.vo.transfer.TransferDocVO;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 调拨单服务实现:状态机 + 审批执行(拍板点 1 原子一步:同事务源仓扣减 + 目的仓入库,
 * 批次跟随源批,序列号台账同步改仓库;源仓可用量不足任一行整单回滚)。
 *
 * @author inventory
 */
@Service
public class TransferServiceImpl implements TransferService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferServiceImpl.class);

    /** 单据中文名(错误提示用)。 */
    private static final String DOC_NAME = "调拨单";

    /** 表头 Mapper。 */
    private final TransferDocMapper docMapper;
    /** 行 Mapper。 */
    private final TransferDocItemMapper itemMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMasterMapper;
    /** 审批资格校验。 */
    private final ApprovalGuard approvalGuard;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;
    /** 库存核心服务(调拨编排)。 */
    private final StockCoreService stockCoreService;

    /**
     * 构造服务。
     *
     * @param docMapper        表头 Mapper
     * @param itemMapper       行 Mapper
     * @param warehouseMapper  仓库 Mapper
     * @param itemMasterMapper 物品 Mapper
     * @param approvalGuard    审批资格校验
     * @param docNoService     单据号服务
     * @param stateSupport     状态机支撑
     * @param stockCoreService 库存核心服务
     */
    public TransferServiceImpl(TransferDocMapper docMapper, TransferDocItemMapper itemMapper,
            WarehouseMapper warehouseMapper, ItemMapper itemMasterMapper, ApprovalGuard approvalGuard,
            DocNoService docNoService, DocStateSupport stateSupport, StockCoreService stockCoreService) {
        this.docMapper = docMapper;
        this.itemMapper = itemMapper;
        this.warehouseMapper = warehouseMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.approvalGuard = approvalGuard;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
        this.stockCoreService = stockCoreService;
    }

    /**
     * 新建调拨单(draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferDocVO create(TransferCreateDTO dto, String username) {
        if (dto.fromWarehouseId().equals(dto.toWarehouseId())) {
            throw new BizException("调拨源仓与目的仓不能相同");
        }
        requireWarehouse(dto.fromWarehouseId());
        requireWarehouse(dto.toWarehouseId());
        TransferDocDO doc = new TransferDocDO();
        doc.setDocNo(docNoService.generateTransferDocNo());
        doc.setDocDate(dto.docDate());
        doc.setFromWarehouseId(dto.fromWarehouseId());
        doc.setToWarehouseId(dto.toWarehouseId());
        doc.setStatus(DocStatus.DRAFT);
        doc.setRemark(dto.remark());
        doc.setCarrier(dto.carrier());
        doc.setCreator(username);
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);
        applyLinesAndTotal(doc, dto);
        docMapper.updateById(doc);
        LOGGER.info("新建调拨单: docNo={}, 源仓={}, 目的仓={}, 行数={}, operator={}",
                doc.getDocNo(), dto.fromWarehouseId(), dto.toWarehouseId(), dto.items().size(), username);
        return get(doc.getId());
    }

    /**
     * 调拨单分页列表。
     */
    @Override
    public PageResult<TransferDocVO> list(TransferDocQuery query) {
        // 数据权限:未授权用户查空;授权用户只看源仓或目的仓命中授权仓的调拨单(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<TransferDocDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.and(w -> w.in(TransferDocDO::getFromWarehouseId, allowed)
                    .or().in(TransferDocDO::getToWarehouseId, allowed));
        }
        if (query.getFromWarehouseId() != null) {
            wrapper.eq(TransferDocDO::getFromWarehouseId, query.getFromWarehouseId());
        }
        if (query.getToWarehouseId() != null) {
            wrapper.eq(TransferDocDO::getToWarehouseId, query.getToWarehouseId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(TransferDocDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(TransferDocDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(TransferDocDO::getDocDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(TransferDocDO::getDocDate, to);
        }
        wrapper.orderByDesc(TransferDocDO::getId);
        Page<TransferDocDO> page = docMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 调拨单详情。
     */
    @Override
    public TransferDocVO get(long id) {
        TransferDocDO doc = requireDoc(id);
        return toVOs(List.of(doc)).get(0);
    }

    /**
     * 编辑调拨单(仅 draft/rejected)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferDocVO update(long id, TransferCreateDTO dto, String username) {
        TransferDocDO doc = requireDoc(id);
        stateSupport.assertWritable(doc.getStatus(), DOC_NAME);
        if (!DocStatus.DRAFT.equals(doc.getStatus()) && !DocStatus.REJECTED.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿/已驳回状态可编辑");
        }
        if (dto.fromWarehouseId().equals(dto.toWarehouseId())) {
            throw new BizException("调拨源仓与目的仓不能相同");
        }
        requireWarehouse(dto.fromWarehouseId());
        requireWarehouse(dto.toWarehouseId());
        doc.setDocDate(dto.docDate());
        doc.setFromWarehouseId(dto.fromWarehouseId());
        doc.setToWarehouseId(dto.toWarehouseId());
        doc.setRemark(dto.remark());
        doc.setCarrier(dto.carrier());
        doc.setUpdater(username);
        doc.setUpdatedAt(LocalDateTime.now());
        if (DocStatus.REJECTED.equals(doc.getStatus())) {
            doc.setStatus(DocStatus.DRAFT);
            doc.setRejectReason(null);
        }
        itemMapper.delete(new LambdaQueryWrapper<TransferDocItemDO>()
                .eq(TransferDocItemDO::getDocId, id));
        applyLinesAndTotal(doc, dto);
        docMapper.updateById(doc);
        return get(id);
    }

    /**
     * 提交:draft/rejected → pending。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferDocVO submit(long id, String username) {
        TransferDocDO doc = requireDoc(id);
        stateSupport.assertWritable(doc.getStatus(), DOC_NAME);
        int n = stateSupport.transition(docMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.REJECTED), DocStatus.PENDING, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 审批执行:pending → completed(同事务源扣目的加;失败整单回滚,单据保持可重试)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferDocVO approve(long id, String username) {
        TransferDocDO doc = requireDoc(id);
        if (DocStatus.COMPLETED.equals(doc.getStatus())) {
            return get(id);
        }
        if (!DocStatus.PENDING.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可审批执行");
        }
        approvalGuard.assertApprovable(username, doc.getCreator());
        // 先条件迁移 approved(执行失败整单回滚,含本次状态变更,单据保持 pending 可重试)
        Map<String, Object> extra = new HashMap<>();
        extra.put("approver", username);
        extra.put("approved_at", LocalDateTime.now());
        int n = stateSupport.transitionWith(docMapper, id, List.of(DocStatus.PENDING),
                DocStatus.APPROVED, username, extra);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        doExecute(doc);
        // 执行成功:approved → completed(同事务)
        stateSupport.transition(docMapper, id, List.of(DocStatus.APPROVED),
                DocStatus.COMPLETED, username);
        LOGGER.info("调拨单审批执行完成: id={}, docNo={}, approver={}", id, doc.getDocNo(), username);
        return get(id);
    }

    /**
     * 驳回:pending → rejected(原因必填)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferDocVO reject(long id, TransferActionDTO dto, String username) {
        TransferDocDO doc = requireDoc(id);
        if (!DocStatus.PENDING.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可驳回");
        }
        approvalGuard.assertApprovable(username, doc.getCreator());
        if (!StringUtils.hasText(dto.rejectReason())) {
            throw new BizException("驳回原因必填");
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("approver", username);
        extra.put("approved_at", LocalDateTime.now());
        extra.put("reject_reason", dto.rejectReason().trim());
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
    public TransferDocVO voidDoc(long id, String username) {
        TransferDocDO doc = requireDoc(id);
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
     * 执行调拨(加入调用方事务):组装库存请求并调核心服务。
     *
     * @param doc 调拨单
     */
    private void doExecute(TransferDocDO doc) {
        List<TransferDocItemDO> lines = itemMapper.selectList(
                new LambdaQueryWrapper<TransferDocItemDO>()
                        .eq(TransferDocItemDO::getDocId, doc.getId())
                        .orderByAsc(TransferDocItemDO::getLineNo));
        if (lines.isEmpty()) {
            throw new BizException(DOC_NAME + "无调拨行,不可执行");
        }
        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(doc.getToWarehouseId());
        request.setFromWarehouseId(doc.getFromWarehouseId());
        request.setDocNo(doc.getDocNo());
        request.setOperator(doc.getApprover());
        List<StockLine> stockLines = new ArrayList<>();
        for (TransferDocItemDO line : lines) {
            StockLine sl = new StockLine();
            sl.setItemId(line.getItemId());
            sl.setQty(line.getQty());
            sl.setLocationId(line.getFromLocationId());
            sl.setToLocationId(line.getToLocationId());
            stockLines.add(sl);
        }
        request.setLines(stockLines);
        stockCoreService.transfer(request);
    }

    /**
     * 行与表头合计落库(行快照 + 成本参考合计,服务端重算)。
     *
     * @param doc 调拨单头(必须已 insert)
     * @param dto 入参
     */
    private void applyLinesAndTotal(TransferDocDO doc, TransferCreateDTO dto) {
        Set<Long> itemIds = new HashSet<>();
        for (TransferLineDTO line : dto.items()) {
            itemIds.add(line.itemId());
        }
        List<ItemDO> items = itemIds.isEmpty() ? List.of() : itemMasterMapper.selectByIds(itemIds);
        Map<Long, ItemDO> itemMap = new HashMap<>();
        for (ItemDO it : items) {
            itemMap.put(it.getId(), it);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < dto.items().size(); i++) {
            TransferLineDTO line = dto.items().get(i);
            ItemDO item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException("物品不存在: id=" + line.itemId());
            }
            total = total.add(line.qty().multiply(line.unitPrice()));
            TransferDocItemDO oi = new TransferDocItemDO();
            oi.setDocId(doc.getId());
            oi.setLineNo(i + 1);
            oi.setItemId(line.itemId());
            oi.setSpecSnapshot(item.getSpec());
            oi.setUnit(item.getUnit());
            oi.setQty(line.qty());
            oi.setUnitPrice(line.unitPrice());
            oi.setFromLocationId(line.fromLocationId());
            oi.setToLocationId(line.toLocationId());
            oi.setVehicleNo(line.vehicleNo());
            oi.setLineRemark(line.lineRemark());
            itemMapper.insert(oi);
        }
        doc.setTotalAmount(total);
    }

    /**
     * 校验仓库存在。
     *
     * @param warehouseId 仓库 ID
     */
    private void requireWarehouse(long warehouseId) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new BizException("仓库不存在: id=" + warehouseId);
        }
    }

    /**
     * 查调拨单,不存在抛 404,非 admin 无权访问该仓单据抛 403。
     *
     * @param id 调拨单 ID
     * @return 调拨单
     */
    private TransferDocDO requireDoc(long id) {
        TransferDocDO doc = docMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("调拨单不存在");
        }
        assertWarehouseAccess(doc.getFromWarehouseId(), doc.getToWarehouseId());
        return doc;
    }

    /**
     * 数据权限按单据所属仓校验:非 admin 且源仓/目的仓均不在授权列表 → 403。
     *
     * @param fromWarehouseId 源仓 ID
     * @param toWarehouseId   目的仓 ID
     */
    private void assertWarehouseAccess(Long fromWarehouseId, Long toWarehouseId) {
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed == null) {
            return;
        }
        if (!allowed.contains(fromWarehouseId) && !allowed.contains(toWarehouseId)) {
            throw BizException.forbidden("无权操作该仓库的单据");
        }
    }

    /**
     * 批量组装 VO(仓库 + 物品 + 行)。
     *
     * @param docs 调拨单列表
     * @return VO 列表
     */
    private List<TransferDocVO> toVOs(List<TransferDocDO> docs) {
        if (docs.isEmpty()) {
            return List.of();
        }
        Set<Long> whIds = new HashSet<>();
        Set<Long> docIds = new HashSet<>();
        for (TransferDocDO d : docs) {
            whIds.add(d.getFromWarehouseId());
            whIds.add(d.getToWarehouseId());
            docIds.add(d.getId());
        }
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        List<TransferDocItemDO> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<TransferDocItemDO>()
                        .in(TransferDocItemDO::getDocId, docIds)
                        .orderByAsc(TransferDocItemDO::getLineNo));
        Set<Long> itemIds = new HashSet<>();
        for (TransferDocItemDO it : allItems) {
            itemIds.add(it.getItemId());
        }
        Map<Long, ItemDO> itemMap = (itemIds.isEmpty() ? List.<ItemDO>of()
                : itemMasterMapper.selectByIds(itemIds)).stream()
                .collect(Collectors.toMap(ItemDO::getId, it -> it));
        Map<Long, List<TransferDocItemDO>> linesByDoc = allItems.stream()
                .collect(Collectors.groupingBy(TransferDocItemDO::getDocId));

        List<TransferDocVO> vos = new ArrayList<>();
        for (TransferDocDO doc : docs) {
            List<TransferDocItemDO> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            List<TransferDocItemVO> lineVos = new ArrayList<>();
            for (TransferDocItemDO line : lines) {
                ItemDO item = itemMap.get(line.getItemId());
                lineVos.add(new TransferDocItemVO(line.getId(), line.getLineNo(), line.getItemId(),
                        item == null ? null : item.getItemCode(),
                        item == null ? null : item.getItemName(),
                        line.getSpecSnapshot(), line.getUnit(),
                        QtyUtils.toContractString(line.getQty()), line.getUnitPrice(),
                        line.getFromLocationId(), line.getToLocationId(), line.getLineRemark(),
                        line.getVehicleNo()));
            }
            vos.add(new TransferDocVO(doc.getId(), doc.getDocNo(), doc.getDocDate(),
                    doc.getFromWarehouseId(), whMap.get(doc.getFromWarehouseId()),
                    doc.getToWarehouseId(), whMap.get(doc.getToWarehouseId()),
                    QtyUtils.toContractString(doc.getTotalAmount()), doc.getStatus(),
                    doc.getCreator(), doc.getCreatedAt(), doc.getUpdater(), doc.getUpdatedAt(),
                    doc.getApprover(), doc.getApprovedAt(), doc.getRejectReason(),
                    doc.getRemark(), doc.getCarrier(), lineVos));
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
