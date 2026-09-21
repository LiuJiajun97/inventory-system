package com.company.inventory.service.impl;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.ApprovalGuard;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DocStateSupport;
import com.company.inventory.common.constant.MessageType;
import com.company.inventory.service.MessageService;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.adjust.StockAdjustCreateDTO;
import com.company.inventory.model.dto.adjust.StockAdjustLineDTO;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.adjust.StockAdjustDocDO;
import com.company.inventory.model.entity.adjust.StockAdjustDocItemDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SerialMapper;
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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    /** 序列号台账 Mapper(序列号仓库盘盈自动生成/盘亏按台账选取)。 */
    private final SerialMapper serialMapper;
    /** 审批资格校验。 */
    private final ApprovalGuard approvalGuard;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;
    /** 库存核心服务。 */
    private final StockCoreService stockCoreService;
    /** 站内消息服务(V25 审批通知)。 */
    private final MessageService messageService;

    /**
     * 构造服务。
     *
     * @param docMapper        表头 Mapper
     * @param itemMapper       行 Mapper
     * @param warehouseMapper  仓库 Mapper
     * @param itemMasterMapper 物品 Mapper
     * @param batchMapper      批次 Mapper
     * @param serialMapper     序列号台账 Mapper
     * @param approvalGuard    审批资格校验
     * @param docNoService     单据号服务
     * @param stateSupport     状态机支撑
     * @param stockCoreService 库存核心服务
     * @param messageService   站内消息服务(V25 审批通知)
     */
    public StockAdjustServiceImpl(StockAdjustDocMapper docMapper, StockAdjustDocItemMapper itemMapper,
            WarehouseMapper warehouseMapper, ItemMapper itemMasterMapper, BatchMapper batchMapper,
            SerialMapper serialMapper, ApprovalGuard approvalGuard, DocNoService docNoService,
            DocStateSupport stateSupport, StockCoreService stockCoreService,
            MessageService messageService) {
        this.docMapper = docMapper;
        this.itemMapper = itemMapper;
        this.warehouseMapper = warehouseMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.batchMapper = batchMapper;
        this.serialMapper = serialMapper;
        this.approvalGuard = approvalGuard;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
        this.stockCoreService = stockCoreService;
        this.messageService = messageService;
    }

    /**
     * 新建调整单(draft,手工建单;refDocNo 恒为空,来源单号仅由盘点差异生成写入)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustDocVO create(StockAdjustCreateDTO dto, String username) {
        validateDto(dto);
        StockAdjustDocDO doc = new StockAdjustDocDO();
        doc.setDocNo(docNoService.generateAdjustDocNo());
        doc.setDocDate(dto.docDate());
        doc.setWarehouseId(dto.warehouseId());
        doc.setAdjustType(dto.adjustType());
        doc.setRefDocNo(null);
        doc.setStatus(DocStatus.DRAFT);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);
        insertLines(doc.getId(), dto);
        LOGGER.info("新建调整单: docNo={}, 类型={}, 行数={}, operator={}",
                doc.getDocNo(), doc.getAdjustType(), dto.items().size(), username);
        return get(doc.getId());
    }

    /**
     * 新建带来源单号的调整单(盘点差异生成专用,refDocNo 落库并参与防重复唯一约束)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustDocVO createWithRef(StockAdjustCreateDTO dto, String refDocNo, String username) {
        validateDto(dto);
        StockAdjustDocDO doc = new StockAdjustDocDO();
        doc.setDocNo(docNoService.generateAdjustDocNo());
        doc.setDocDate(dto.docDate());
        doc.setWarehouseId(dto.warehouseId());
        doc.setAdjustType(dto.adjustType());
        doc.setRefDocNo(refDocNo);
        doc.setStatus(DocStatus.DRAFT);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);
        insertLines(doc.getId(), dto);
        LOGGER.info("新建调整单(盘点生成): docNo={}, 来源={}, 类型={}, 行数={}, operator={}",
                doc.getDocNo(), refDocNo, doc.getAdjustType(), dto.items().size(), username);
        return get(doc.getId());
    }

    /**
     * 编辑调整单(仅 draft/rejected 可编辑,行明细全量替换;已驳回编辑后回 draft 并清空驳回原因)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockAdjustDocVO update(long id, StockAdjustCreateDTO dto, String username) {
        StockAdjustDocDO doc = requireDoc(id);
        stateSupport.assertWritable(doc.getStatus(), DOC_NAME);
        if (!DocStatus.DRAFT.equals(doc.getStatus()) && !DocStatus.REJECTED.equals(doc.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿/已驳回状态可编辑");
        }
        validateDto(dto);
        doc.setDocDate(dto.docDate());
        doc.setWarehouseId(dto.warehouseId());
        doc.setAdjustType(dto.adjustType());
        doc.setRemark(dto.remark());
        doc.setUpdater(username);
        doc.setUpdatedAt(LocalDateTime.now());
        boolean wasRejected = DocStatus.REJECTED.equals(doc.getStatus());
        if (wasRejected) {
            doc.setStatus(DocStatus.DRAFT);
            doc.setRejectReason(null);
        }
        itemMapper.delete(new LambdaQueryWrapper<StockAdjustDocItemDO>()
                .eq(StockAdjustDocItemDO::getDocId, id));
        insertLines(id, dto);
        // updateById 默认忽略 null 字段,已驳回单的驳回原因需显式 SET NULL 才能清空
        LambdaUpdateWrapper<StockAdjustDocDO> updateWrapper = new LambdaUpdateWrapper<StockAdjustDocDO>()
                .eq(StockAdjustDocDO::getId, id);
        if (wasRejected) {
            updateWrapper.set(StockAdjustDocDO::getRejectReason, null);
        }
        docMapper.update(doc, updateWrapper);
        LOGGER.info("编辑调整单: id={}, docNo={}, 类型={}, 行数={}, operator={}",
                id, doc.getDocNo(), dto.adjustType(), dto.items().size(), username);
        return get(id);
    }

    /**
     * 校验调整单入参(仓库存在、调整类型合法)。
     *
     * @param dto 入参
     */
    private void validateDto(StockAdjustCreateDTO dto) {
        WarehouseDO warehouse = warehouseMapper.selectById(dto.warehouseId());
        if (warehouse == null) {
            throw new BizException("仓库不存在: id=" + dto.warehouseId());
        }
        if (!ErrorCode.ADJUST_TYPE_GAIN.equals(dto.adjustType())
                && !ErrorCode.ADJUST_TYPE_LOSS.equals(dto.adjustType())
                && !ErrorCode.ADJUST_TYPE_SCRAP.equals(dto.adjustType())) {
            throw new BizException("调整类型仅支持 gain/loss/scrap");
        }
    }

    /**
     * 批量插入调整行(新建/编辑共用,行级物品规格单位快照由服务端带出)。
     *
     * @param docId 调整单 ID(必须已落库)
     * @param dto   入参
     */
    private void insertLines(long docId, StockAdjustCreateDTO dto) {
        Set<Long> itemIds = dto.items().stream().map(StockAdjustLineDTO::itemId).collect(Collectors.toSet());
        List<ItemDO> items = itemIds.isEmpty() ? List.of() : itemMasterMapper.selectByIds(itemIds);
        Map<Long, ItemDO> itemMap = new HashMap<>();
        for (ItemDO it : items) {
            itemMap.put(it.getId(), it);
        }
        for (int i = 0; i < dto.items().size(); i++) {
            StockAdjustLineDTO line = dto.items().get(i);
            ItemDO item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException("物品不存在: id=" + line.itemId());
            }
            StockAdjustDocItemDO oi = new StockAdjustDocItemDO();
            oi.setDocId(docId);
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
    }

    /**
     * 调整单分页列表。
     */
    @Override
    public PageResult<StockAdjustDocVO> list(StockAdjustQuery query) {
        // 数据权限:未授权用户查空;授权用户只查授权仓(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<StockAdjustDocDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(StockAdjustDocDO::getWarehouseId, allowed);
        }
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
        StockAdjustDocDO doc = requireDoc(id);
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
        notifyApprovalResult(doc, username, true, null);
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
        notifyApprovalResult(doc, username, false, rejectReason);
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
     * 序列号仓库:盘点录入只录数量无序列号,盘盈自动生成台账序列号,
     * 盘亏按 in_stock 台账按物品选取(台账不足拒绝——数量超台账说明盘点数据有误,不静默扣)。
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
        WarehouseDO warehouse = warehouseMapper.selectById(doc.getWarehouseId());
        boolean serialWh = warehouse != null && Boolean.TRUE.equals(warehouse.getEnableSerial());
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
            if (serialWh) {
                sl.setSerialNos(gain ? generateSerials(doc, line) : pickSerials(doc, line));
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
     * 盘盈自动生成序列号:格式 物品编码-ADJ-调整单号-3位序号(台账唯一,冲突 400 可重试)。
     *
     * @param doc  调整单
     * @param line 调整行
     * @return 序列号列表(数量=行数量)
     */
    private List<String> generateSerials(StockAdjustDocDO doc, StockAdjustDocItemDO line) {
        long qty = line.getQty().longValue();
        if (line.getQty().stripTrailingZeros().scale() > 0) {
            throw new BizException(DOC_NAME + "行" + line.getLineNo() + "序列号仓库数量必须为整数");
        }
        ItemDO item = itemMasterMapper.selectById(line.getItemId());
        String prefix = (item == null ? "ITEM" : item.getItemCode()) + "-ADJ-" + doc.getDocNo() + "-";
        List<String> serials = new ArrayList<>();
        for (long i = 1; i <= qty; i++) {
            serials.add(prefix + String.format("%03d", i));
        }
        Long dup = serialMapper.selectCount(new LambdaQueryWrapper<SerialDO>()
                .in(SerialDO::getSerialNo, serials));
        if (dup != null && dup > 0) {
            throw new BizException("自动生成的序列号已存在,请作废本单后重新生成");
        }
        return serials;
    }

    /**
     * 盘亏按序列号台账选取:本仓本物品 in_stock 状态按入库序取前 N 个,不足 400。
     *
     * @param doc  调整单
     * @param line 调整行
     * @return 选中的序列号列表(数量=行数量)
     */
    private List<String> pickSerials(StockAdjustDocDO doc, StockAdjustDocItemDO line) {
        long qty = line.getQty().longValue();
        List<SerialDO> pool = serialMapper.selectList(new LambdaQueryWrapper<SerialDO>()
                .eq(SerialDO::getItemId, line.getItemId())
                .eq(SerialDO::getWarehouseId, doc.getWarehouseId())
                .eq(SerialDO::getStatus, ErrorCode.SERIAL_STATUS_IN_STOCK)
                .orderByAsc(SerialDO::getId)
                .last("limit " + qty));
        if (pool.size() != qty) {
            throw new BizException(DOC_NAME + "行" + line.getLineNo() + "序列号台账不足" + qty
                    + "个(现有" + pool.size() + "个),实盘数量与台账不符,请作废后重新盘点");
        }
        return pool.stream().map(SerialDO::getSerialNo).collect(Collectors.toList());
    }

    /**
     * 查调整单,不存在抛 404,非 admin 无权访问该仓单据抛 403。
     *
     * @param id 调整单 ID
     * @return 调整单
     */
    private StockAdjustDocDO requireDoc(long id) {
        StockAdjustDocDO doc = docMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("库存调整单不存在");
        }
        assertWarehouseAccess(doc.getWarehouseId());
        return doc;
    }

    /**
     * 数据权限按单据所属仓校验:非 admin 且所属仓不在授权列表 → 403。
     *
     * @param warehouseId 单据所属仓 ID
     */
    private void assertWarehouseAccess(Long warehouseId) {
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed == null) {
            return;
        }
        if (!allowed.contains(warehouseId)) {
            throw BizException.forbidden("无权操作该仓库的单据");
        }
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

    /**
     * 发送审批结果通知(失败只打 warn,不影响主流程)。
     *
     * @param doc       单据头
     * @param approver  审批人
     * @param approved  true 批准 / false 驳回
     * @param reason    驳回原因(批准时为 null)
     */
    private void notifyApprovalResult(StockAdjustDocDO doc, String approver,
            boolean approved, String reason) {
        try {
            if (doc.getCreator() == null || doc.getCreator().equals(approver)) {
                return;
            }
            MessageServiceImpl impl = (MessageServiceImpl) messageService;
            Long receiverId = impl.lookupUserId(doc.getCreator());
            if (receiverId == null) {
                return;
            }
            String title = approved ? "你的库存调整已批准" : "你的库存调整已驳回";
            String content = approved
                    ? "你的库存调整 " + doc.getDocNo() + " 已批准"
                    : "你的库存调整 " + doc.getDocNo() + " 已驳回" + (reason == null ? "" : ",原因:" + reason);
            messageService.trySendQuietly(receiverId, MessageType.APPROVAL,
                    title, content, "stock_adjust", doc.getId());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(StockAdjustServiceImpl.class)
                    .warn("库存调整审批通知失败: {}", e.getMessage());
        }
    }
}
