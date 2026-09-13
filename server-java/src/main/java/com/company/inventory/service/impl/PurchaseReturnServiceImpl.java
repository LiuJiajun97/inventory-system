package com.company.inventory.service.impl;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.util.MoneyUtils;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.mapper.PurchaseOrderItemMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.PurchaseReturnItemMapper;
import com.company.inventory.mapper.PurchaseReturnMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.outbound.OutboundCreateDTO;
import com.company.inventory.model.dto.outbound.OutboundLineDTO;
import com.company.inventory.model.dto.returns.PurchaseReturnCreateDTO;
import com.company.inventory.model.dto.returns.PurchaseReturnLineDTO;
import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderItemDO;
import com.company.inventory.model.entity.returns.PurchaseReturnDO;
import com.company.inventory.model.entity.returns.PurchaseReturnItemDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.PurchaseReturnQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.InvoiceService;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.PurchaseReturnService;
import com.company.inventory.model.vo.returns.PurchaseReturnCreatedVO;
import com.company.inventory.model.vo.returns.PurchaseReturnItemVO;
import com.company.inventory.model.vo.returns.PurchaseReturnVO;
import com.company.inventory.model.vo.outbound.OutboundDocCreatedVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderItemVO;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购退货单服务实现:整单一个事务,退货单头 + 行 + 出库过账 + 原单行已退回写。
 *
 * <p>方案 A:退货单不另建扣减逻辑,过账时生成出库单(refType=purchase_return,
 * refDocId=退货单 ID)走现有过账链路;价格锁原采购订单行快照(服务端取值,不信前端);
 * 行退货量上限 = 原行已到货 - 已退累计。</p>
 *
 * @author inventory
 */
@Service
public class PurchaseReturnServiceImpl implements PurchaseReturnService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseReturnServiceImpl.class);

    /** 退货单头 Mapper。 */
    private final PurchaseReturnMapper returnDocMapper;
    /** 退货单行 Mapper。 */
    private final PurchaseReturnItemMapper returnItemMapper;
    /** 采购订单服务(审批态校验 + 原行 VO)。 */
    private final PurchaseOrderService purchaseOrderService;
    /** 采购订单行 Mapper(已退累计读取/回写)。 */
    private final PurchaseOrderItemMapper orderItemMapper;
    /** 采购订单 Mapper(列表展示原单号)。 */
    private final PurchaseOrderMapper orderMapper;
    /** 出库单服务(过账链路)。 */
    private final OutboundService outboundService;
    /** 发票服务(退货自动生成红字凭单)。 */
    private final InvoiceService invoiceService;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 仓库 Mapper(列表展示仓库)。 */
    private final WarehouseMapper warehouseMapper;

    /**
     * 构造服务。
     *
     * @param returnDocMapper       退货单头 Mapper
     * @param returnItemMapper      退货单行 Mapper
     * @param purchaseOrderService  采购订单服务
     * @param orderItemMapper       采购订单行 Mapper
     * @param orderMapper           采购订单 Mapper
     * @param outboundService       出库单服务
     * @param invoiceService        发票服务
     * @param docNoService          单据号服务
     * @param warehouseMapper       仓库 Mapper
     */
    public PurchaseReturnServiceImpl(PurchaseReturnMapper returnDocMapper,
            PurchaseReturnItemMapper returnItemMapper, PurchaseOrderService purchaseOrderService,
            PurchaseOrderItemMapper orderItemMapper, PurchaseOrderMapper orderMapper,
            OutboundService outboundService, InvoiceService invoiceService, DocNoService docNoService,
            WarehouseMapper warehouseMapper) {
        this.returnDocMapper = returnDocMapper;
        this.returnItemMapper = returnItemMapper;
        this.purchaseOrderService = purchaseOrderService;
        this.orderItemMapper = orderItemMapper;
        this.orderMapper = orderMapper;
        this.outboundService = outboundService;
        this.invoiceService = invoiceService;
        this.docNoService = docNoService;
        this.warehouseMapper = warehouseMapper;
    }

    /**
     * 新建采购退货单并过账(create 即过账,与出入库单一致)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 创建结果(含联动出库单号)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturnCreatedVO create(PurchaseReturnCreateDTO dto, String username) {
        // 1. 原采购订单必须存在且已审批通过(含自动 completed/手工 closed:已到货部分仍可退)
        PurchaseOrderDO order = orderMapper.selectById(dto.purchaseOrderId());
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        if (!DocStatus.APPROVED.equals(order.getStatus())
                && !DocStatus.COMPLETED.equals(order.getStatus())
                && !DocStatus.CLOSED.equals(order.getStatus())) {
            throw new BizException("采购订单未审批通过,不允许退货");
        }
        Map<Long, PurchaseOrderItemVO> refItems = new java.util.HashMap<>();
        for (PurchaseOrderItemVO it : purchaseOrderService.get(dto.purchaseOrderId()).items()) {
            refItems.put(it.id(), it);
        }
        Set<Long> refLineIds = new HashSet<>();
        for (PurchaseReturnLineDTO line : dto.items()) {
            refLineIds.add(line.purchaseOrderItemId());
        }
        if (refLineIds.size() != dto.items().size()) {
            throw new BizException("同一原采购订单行不能重复退货");
        }
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, PurchaseOrderItemDO> refLineMap = (refLineIds.isEmpty()
                ? List.<PurchaseOrderItemDO>of()
                : orderItemMapper.selectByIds(refLineIds)).stream()
                .collect(Collectors.toMap(PurchaseOrderItemDO::getId, d -> d));
        // 2. 逐行校验可退量(上限 = 已到货 - 已退累计)
        for (int i = 0; i < dto.items().size(); i++) {
            PurchaseReturnLineDTO line = dto.items().get(i);
            PurchaseOrderItemDO refLine = refLineMap.get(line.purchaseOrderItemId());
            if (refItems.get(line.purchaseOrderItemId()) == null || refLine == null) {
                throw new BizException("采购订单行不存在: " + line.purchaseOrderItemId());
            }
            BigDecimal returned = refLine.getReturnedQty() == null
                    ? BigDecimal.ZERO : refLine.getReturnedQty();
            BigDecimal returnable = refLine.getArrivedQty().subtract(returned);
            if (line.quantity().compareTo(returnable) > 0) {
                if (returnable.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException("第 " + (i + 1) + " 行:可退数量为 0");
                }
                throw new BizException("第 " + (i + 1) + " 行:退货数量超过可退数量("
                        + QtyUtils.toContractString(returnable) + ")");
            }
        }
        // 3. 插入退货单头 + 行(单价/税率锁原行快照,服务端取值,前端传值忽略)
        String docNo = docNoService.generatePurchaseReturnNo();
        PurchaseReturnDO doc = new PurchaseReturnDO();
        doc.setDocNo(docNo);
        doc.setDocDate(dto.docDate() == null ? LocalDate.now() : dto.docDate());
        doc.setPurchaseOrderId(dto.purchaseOrderId());
        doc.setWarehouseId(dto.warehouseId());
        doc.setStatus(ErrorCode.DOC_STATUS_FINISHED);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        returnDocMapper.insert(doc);
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < dto.items().size(); i++) {
            PurchaseReturnLineDTO line = dto.items().get(i);
            PurchaseOrderItemVO ref = refItems.get(line.purchaseOrderItemId());
            BigDecimal unitPrice = ref.unitPrice() == null ? BigDecimal.ZERO : ref.unitPrice();
            BigDecimal taxRate = ref.taxRate() == null ? BigDecimal.ZERO : ref.taxRate();
            BigDecimal amount = MoneyUtils.amountOf(line.quantity(), unitPrice);
            BigDecimal tax = MoneyUtils.taxOf(amount, taxRate);
            PurchaseReturnItemDO item = new PurchaseReturnItemDO();
            item.setDocId(doc.getId());
            item.setLineNo(i + 1);
            item.setPurchaseOrderItemId(line.purchaseOrderItemId());
            item.setItemId(ref.itemId());
            item.setSpecSnapshot(ref.specSnapshot());
            item.setUnit(ref.unit());
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setTaxRate(taxRate);
            item.setAmount(amount);
            item.setTaxAmount(tax);
            item.setTaxInclusiveTotal(MoneyUtils.inclusiveOf(amount, tax));
            item.setCreator(username);
            returnItemMapper.insert(item);
            totalAmount = totalAmount.add(amount);
        }
        doc.setTotalAmount(totalAmount);
        returnDocMapper.updateById(doc);
        // 4. 生成出库单并过账(库存扣减走现有链路,refType=purchase_return 落手工单分支)
        List<OutboundLineDTO> outLines = dto.items().stream().map(line -> {
            PurchaseOrderItemVO ref = refItems.get(line.purchaseOrderItemId());
            BigDecimal unitPrice = ref.unitPrice() == null ? BigDecimal.ZERO : ref.unitPrice();
            BigDecimal taxRate = ref.taxRate() == null ? BigDecimal.ZERO : ref.taxRate();
            return new OutboundLineDTO(ref.itemId(), line.quantity(), null, line.locationId(),
                    line.serialNos(), unitPrice, taxRate, null);
        }).toList();
        OutboundDocCreatedVO outDoc = outboundService.create(new OutboundCreateDTO(
                dto.warehouseId(), dto.remark(), outLines,
                ErrorCode.REF_TYPE_PURCHASE_RETURN, doc.getId(), doc.getDocDate(),
                null, null, null, "采购退货", username), username);
        // 5. 回写原采购订单行已退累计(returned_qty += 本次退货量)
        for (PurchaseReturnLineDTO line : dto.items()) {
            PurchaseOrderItemDO refLine = refLineMap.get(line.purchaseOrderItemId());
            BigDecimal returned = refLine.getReturnedQty() == null
                    ? BigDecimal.ZERO : refLine.getReturnedQty();
            refLine.setReturnedQty(returned.add(line.quantity()));
            orderItemMapper.updateById(refLine);
        }
        // 6. 同事务自动生成负数草稿发票(红字凭单,对齐 U8 红字发票/金蝶红字应付单,确认后核减应付)
        invoiceService.generateForPurchaseReturn(doc, username);
        LOGGER.info("新建采购退货单: docNo={}, purchaseOrderId={}, warehouseId={}, 行数={}, operator={}",
                docNo, dto.purchaseOrderId(), dto.warehouseId(), dto.items().size(), username);
        return new PurchaseReturnCreatedVO(doc.getId(), docNo, doc.getWarehouseId(),
                doc.getStatus(), dto.remark(), doc.getCreator(), outDoc.docNo(), doc.getCreatedAt());
    }

    /**
     * 采购退货单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<PurchaseReturnVO> list(PurchaseReturnQuery query) {
        // 数据权限:未授权用户查空;授权用户只查授权仓(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<PurchaseReturnDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(PurchaseReturnDO::getWarehouseId, allowed);
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(PurchaseReturnDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(PurchaseReturnDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PurchaseReturnDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(PurchaseReturnDO::getDocDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(PurchaseReturnDO::getDocDate, to);
        }
        wrapper.orderByDesc(PurchaseReturnDO::getId);
        Page<PurchaseReturnDO> result = returnDocMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<PurchaseReturnDO> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(),
                    query.getPage(), query.getPageSize());
        }
        return PageResult.of(toVOs(rows), result.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 采购退货单详情。
     *
     * @param id 单据 ID
     * @return 单据(含仓库与原采购订单单号)
     */
    @Override
    public PurchaseReturnVO get(long id) {
        PurchaseReturnDO doc = returnDocMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("采购退货单不存在");
        }
        return toVOs(List.of(doc)).get(0);
    }

    /**
     * 批量组装单据 VO(仓库 + 单据行 + 原采购订单单号)。
     *
     * @param docs 单据列表
     * @return VO 列表
     */
    private List<PurchaseReturnVO> toVOs(List<PurchaseReturnDO> docs) {
        Set<Long> whIds = docs.stream().map(PurchaseReturnDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(PurchaseReturnDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> orderIds = docs.stream().map(PurchaseReturnDO::getPurchaseOrderId)
                .collect(Collectors.toCollection(HashSet::new));
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        Map<Long, PurchaseOrderDO> orderMap = (orderIds.isEmpty() ? List.<PurchaseOrderDO>of()
                : orderMapper.selectByIds(orderIds)).stream()
                .collect(Collectors.toMap(PurchaseOrderDO::getId, o -> o));
        List<PurchaseReturnItemDO> allItems = returnItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItemDO>()
                        .in(PurchaseReturnItemDO::getDocId, docIds)
                        .orderByAsc(PurchaseReturnItemDO::getId));
        Map<Long, List<PurchaseReturnItemDO>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(PurchaseReturnItemDO::getDocId));

        List<PurchaseReturnVO> vos = new ArrayList<>();
        for (PurchaseReturnDO doc : docs) {
            List<PurchaseReturnItemDO> items =
                    itemMap.getOrDefault(doc.getId(), List.of());
            List<PurchaseReturnItemVO> itemVos = items.stream()
                    .map(this::toItemVO).toList();
            PurchaseOrderDO order = orderMap.get(doc.getPurchaseOrderId());
            vos.add(new PurchaseReturnVO(doc.getId(), doc.getDocNo(), doc.getDocDate(),
                    doc.getPurchaseOrderId(), order == null ? null : order.getDocNo(),
                    doc.getWarehouseId(), whMap.get(doc.getWarehouseId()),
                    doc.getTotalAmount(), doc.getRemark(), doc.getStatus(),
                    doc.getCreator(), doc.getCreatedAt(), itemVos));
        }
        return vos;
    }

    /**
     * 实体转单据行 VO。
     *
     * @param item 实体
     * @return VO
     */
    private PurchaseReturnItemVO toItemVO(PurchaseReturnItemDO item) {
        return new PurchaseReturnItemVO(item.getId(), item.getDocId(), item.getLineNo(),
                item.getPurchaseOrderItemId(), item.getItemId(), item.getSpecSnapshot(),
                item.getUnit(), QtyUtils.toContractString(item.getQuantity()),
                item.getUnitPrice(), item.getTaxRate(),
                QtyUtils.toContractString(item.getAmount()),
                QtyUtils.toContractString(item.getTaxAmount()),
                QtyUtils.toContractString(item.getTaxInclusiveTotal()));
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
