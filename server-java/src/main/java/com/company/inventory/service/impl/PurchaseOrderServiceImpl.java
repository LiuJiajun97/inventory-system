package com.company.inventory.service.impl;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.ApprovalGuard;
import com.company.inventory.common.support.DocStateSupport;
import com.company.inventory.common.util.MoneyUtils;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.purchase.ArrivalLine;
import com.company.inventory.model.dto.purchase.PurchaseActionDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderCreateDTO;
import com.company.inventory.model.dto.purchase.PurchaseOrderLineDTO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.PurchaseOrderItemMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.model.query.PurchaseOrderQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.model.vo.purchase.PurchaseOrderItemVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderVO;
import com.company.inventory.model.vo.supplier.SupplierVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 采购订单服务实现:状态机 + 价税重算 + 部分到货回写 + 超收控制 + 自动关闭(方案 §5.1)。
 *
 * @author inventory
 */
@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);

    /** 单据中文名(错误提示用)。 */
    private static final String DOC_NAME = "采购订单";

    /** 默认税率(百分数,13%)。 */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13.00");

    /** 不超收比例(0)。 */
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO;

    /** 表头 Mapper。 */
    private final PurchaseOrderMapper orderMapper;
    /** 行 Mapper。 */
    private final PurchaseOrderItemMapper itemMapper;
    /** 供应商 Mapper。 */
    private final SupplierMapper supplierMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMasterMapper;
    /** 用户 Mapper。 */
    private final UserMapper userMapper;
    /** 审批资格校验。 */
    private final ApprovalGuard approvalGuard;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;

    /**
     * 构造服务。
     *
     * @param orderMapper     表头 Mapper
     * @param itemMapper      行 Mapper
     * @param supplierMapper  供应商 Mapper
     * @param itemMasterMapper 物品 Mapper
     * @param userMapper      用户 Mapper
     * @param approvalGuard   审批资格校验
     * @param docNoService    单据号服务
     * @param stateSupport    状态机支撑
     */
    public PurchaseOrderServiceImpl(PurchaseOrderMapper orderMapper,
            PurchaseOrderItemMapper itemMapper, SupplierMapper supplierMapper,
            ItemMapper itemMasterMapper, UserMapper userMapper, ApprovalGuard approvalGuard,
            DocNoService docNoService, DocStateSupport stateSupport) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.supplierMapper = supplierMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.userMapper = userMapper;
        this.approvalGuard = approvalGuard;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
    }

    /**
     * 新建采购订单(draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO create(PurchaseOrderCreateDTO dto, String username) {
        requireSupplier(dto.supplierId());
        requireUser(dto.buyerId());
        PurchaseOrderDO order = new PurchaseOrderDO();
        order.setDocNo(docNoService.generatePurchaseOrderNo());
        order.setDocDate(dto.docDate());
        order.setSupplierId(dto.supplierId());
        order.setBuyerId(dto.buyerId());
        order.setAllowOverReceiptRate(
                dto.allowOverReceiptRate() == null ? ZERO_RATE : dto.allowOverReceiptRate());
        order.setStatus(DocStatus.DRAFT);
        order.setRemark(dto.remark());
        order.setCreator(username);
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        applyLinesAndTotals(order, dto);
        orderMapper.updateById(order);
        LOGGER.info("新建采购订单: docNo={}, supplierId={}, 行数={}, operator={}",
                order.getDocNo(), dto.supplierId(), dto.items().size(), username);
        return get(order.getId());
    }

    /**
     * 采购订单分页列表。
     */
    @Override
    public PageResult<PurchaseOrderVO> list(PurchaseOrderQuery query) {
        LambdaQueryWrapper<PurchaseOrderDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getSupplierId() != null) {
            wrapper.eq(PurchaseOrderDO::getSupplierId, query.getSupplierId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(PurchaseOrderDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PurchaseOrderDO::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getFrom())) {
            wrapper.ge(PurchaseOrderDO::getDocDate, query.getFrom().trim());
        }
        if (StringUtils.hasText(query.getTo())) {
            wrapper.le(PurchaseOrderDO::getDocDate, query.getTo().trim());
        }
        wrapper.orderByDesc(PurchaseOrderDO::getId);
        Page<PurchaseOrderDO> page = orderMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<PurchaseOrderVO> vos = toVOs(page.getRecords());
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 订单详情。
     */
    @Override
    public PurchaseOrderVO get(long id) {
        PurchaseOrderDO order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        return toVOs(List.of(order)).get(0);
    }

    /**
     * 编辑订单(仅 draft/rejected,rejected 编辑后回 draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO update(long id, PurchaseOrderCreateDTO dto, String username) {
        PurchaseOrderDO order = requireOrder(id);
        stateSupport.assertWritable(order.getStatus(), DOC_NAME);
        if (!DocStatus.DRAFT.equals(order.getStatus()) && !DocStatus.REJECTED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿/已驳回状态可编辑");
        }
        requireSupplier(dto.supplierId());
        requireUser(dto.buyerId());
        order.setDocDate(dto.docDate());
        order.setSupplierId(dto.supplierId());
        order.setBuyerId(dto.buyerId());
        order.setAllowOverReceiptRate(
                dto.allowOverReceiptRate() == null ? ZERO_RATE : dto.allowOverReceiptRate());
        order.setRemark(dto.remark());
        order.setUpdater(username);
        order.setUpdatedAt(LocalDateTime.now());
        if (DocStatus.REJECTED.equals(order.getStatus())) {
            order.setStatus(DocStatus.DRAFT);
            order.setRejectReason(null);
        }
        itemMapper.delete(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getOrderId, id));
        applyLinesAndTotals(order, dto);
        orderMapper.updateById(order);
        LOGGER.info("编辑采购订单: id={}, docNo={}, operator={}", id, order.getDocNo(), username);
        return get(id);
    }

    /**
     * 提交:draft/rejected → pending。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO submit(long id, String username) {
        PurchaseOrderDO order = requireOrder(id);
        stateSupport.assertWritable(order.getStatus(), DOC_NAME);
        int n = stateSupport.transition(orderMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.REJECTED), DocStatus.PENDING, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 审批通过:pending → approved(审批人≠制单人;已 approved 幂等)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO approve(long id, String username) {
        PurchaseOrderDO order = requireOrder(id);
        if (DocStatus.APPROVED.equals(order.getStatus())) {
            return get(id);
        }
        assertApprovable(order, username);
        int n = stateSupport.transitionWith(orderMapper, id, List.of(DocStatus.PENDING),
                DocStatus.APPROVED, username, approveExtra(username));
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        LOGGER.info("采购订单审批通过: id={}, docNo={}, approver={}", id, order.getDocNo(), username);
        return get(id);
    }

    /**
     * 驳回:pending → rejected(原因必填,审批人≠制单人)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO reject(long id, PurchaseActionDTO dto, String username) {
        PurchaseOrderDO order = requireOrder(id);
        if (!DocStatus.PENDING.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可驳回");
        }
        assertApprovable(order, username);
        if (!StringUtils.hasText(dto.rejectReason())) {
            throw new BizException("驳回原因必填");
        }
        Map<String, Object> extra = approveExtra(username);
        extra.put("reject_reason", dto.rejectReason().trim());
        int n = stateSupport.transitionWith(orderMapper, id, List.of(DocStatus.PENDING),
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
    public PurchaseOrderVO voidDoc(long id, String username) {
        PurchaseOrderDO order = requireOrder(id);
        stateSupport.assertWritable(order.getStatus(), DOC_NAME);
        int n = stateSupport.transition(orderMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.PENDING, DocStatus.REJECTED, DocStatus.APPROVED),
                DocStatus.VOIDED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 手工关闭:approved → closed。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO close(long id, String username) {
        PurchaseOrderDO order = requireOrder(id);
        if (!DocStatus.APPROVED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "仅已审批状态可关闭");
        }
        int n = stateSupport.transition(orderMapper, id, List.of(DocStatus.APPROVED),
                DocStatus.CLOSED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 采购到货回写:逐行条件 UPDATE(超收拒绝并提示行号),行达标置 closed,全行 closed 自动 completed。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyArrival(long orderId, List<ArrivalLine> lines) {
        PurchaseOrderDO order = requireOrder(orderId);
        if (!DocStatus.APPROVED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "未审批通过,不允许到货入库");
        }
        for (ArrivalLine line : lines) {
            PurchaseOrderItemDO oi = itemMapper.selectById(line.orderLineId());
            if (oi == null || oi.getOrderId() == null || oi.getOrderId() != orderId) {
                throw new BizException("采购订单行不存在或不属于该订单: " + line.orderLineId());
            }
            int n = itemMapper.applyArrival(oi.getId(), orderId, line.qty());
            if (n == 0) {
                throw new BizException("超收拒绝:行 " + oi.getLineNo()
                        + "(物品 " + oi.getItemId() + "),已到货 "
                        + QtyUtils.toContractString(oi.getArrivedQty())
                        + ",本次 " + QtyUtils.toContractString(line.qty())
                        + ",超出允许上限");
            }
        }
        // 全行 closed → 订单自动 completed
        Long open = itemMapper.selectCount(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .eq(PurchaseOrderItemDO::getOrderId, orderId).ne(PurchaseOrderItemDO::getClosed, true));
        if (open != null && open == 0) {
            stateSupport.transition(orderMapper, orderId, List.of(DocStatus.APPROVED),
                    DocStatus.COMPLETED, order.getCreator() == null ? "system" : order.getCreator());
        }
        LOGGER.info("采购到货回写: orderId={}, 行数={}", orderId, lines.size());
    }

    /**
     * 校验订单已审批并返回行(入库单关联采购订单时调用)。
     *
     * @param orderId 订单 ID
     * @return 行 ID → 行 VO
     */
    @Override
    public Map<Long, PurchaseOrderItemVO> requireApprovedItems(long orderId) {
        PurchaseOrderDO order = requireOrder(orderId);
        if (!DocStatus.APPROVED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "未审批通过,不允许到货入库");
        }
        Map<Long, PurchaseOrderItemVO> map = new HashMap<>();
        for (PurchaseOrderItemVO it : get(orderId).items()) {
            map.put(it.id(), it);
        }
        return map;
    }

    /**
     * 行与表头合计落库:行级价税分离 + 物料快照 + 表头三合计(服务端重算,不信前端传值)。
     *
     * @param order 订单头(必须已 insert,有 ID)
     * @param dto   入参
     */
    private void applyLinesAndTotals(PurchaseOrderDO order, PurchaseOrderCreateDTO dto) {
        Set<Long> itemIds = new HashSet<>();
        for (PurchaseOrderLineDTO line : dto.items()) {
            itemIds.add(line.itemId());
        }
        List<ItemDO> items = itemIds.isEmpty() ? List.of() : itemMasterMapper.selectByIds(itemIds);
        Map<Long, ItemDO> itemMap = new HashMap<>();
        for (ItemDO it : items) {
            itemMap.put(it.getId(), it);
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalInclusive = BigDecimal.ZERO;
        for (int i = 0; i < dto.items().size(); i++) {
            PurchaseOrderLineDTO line = dto.items().get(i);
            ItemDO item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException("物品不存在: id=" + line.itemId());
            }
            BigDecimal rate = line.taxRate() == null ? DEFAULT_TAX_RATE : line.taxRate();
            BigDecimal amount = MoneyUtils.amountOf(line.orderedQty(), line.unitPrice());
            BigDecimal tax = MoneyUtils.taxOf(amount, rate);
            BigDecimal inclusive = MoneyUtils.inclusiveOf(amount, tax);
            totalAmount = totalAmount.add(amount);
            totalTax = totalTax.add(tax);
            totalInclusive = totalInclusive.add(inclusive);

            PurchaseOrderItemDO oi = new PurchaseOrderItemDO();
            oi.setOrderId(order.getId());
            oi.setLineNo(i + 1);
            oi.setItemId(line.itemId());
            oi.setSpecSnapshot(item.getSpec());
            oi.setUnit(item.getUnit());
            oi.setOrderedQty(line.orderedQty());
            oi.setArrivedQty(BigDecimal.ZERO);
            oi.setExpectedDeliveryDate(line.expectedDeliveryDate());
            oi.setUnitPrice(line.unitPrice());
            oi.setTaxRate(rate);
            oi.setAmount(amount);
            oi.setTaxAmount(tax);
            oi.setTaxInclusiveTotal(inclusive);
            oi.setClosed(false);
            oi.setLineRemark(line.lineRemark());
            itemMapper.insert(oi);
        }
        order.setTotalAmount(totalAmount);
        order.setTotalTaxAmount(totalTax);
        order.setTotalTaxInclusive(totalInclusive);
    }

    /**
     * 校验供应商存在且启用。
     *
     * @param supplierId 供应商 ID
     */
    private void requireSupplier(long supplierId) {
        SupplierDO supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BizException("供应商不存在: id=" + supplierId);
        }
        if (supplier.getStatus() != null && supplier.getStatus() != 1) {
            throw new BizException("供应商已停用: " + supplier.getSupplierCode());
        }
    }

    /**
     * 校验用户存在(采购员)。
     *
     * @param userId 用户 ID
     */
    private void requireUser(long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("采购员不存在: id=" + userId);
        }
    }

    /**
     * 查订单,不存在抛 404。
     *
     * @param id 订单 ID
     * @return 订单
     */
    private PurchaseOrderDO requireOrder(long id) {
        PurchaseOrderDO order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        return order;
    }

    /**
     * 校验可审批:pending + 审批资格(管理员可自批,库员禁自批)。
     *
     * @param order    订单
     * @param username 当前用户
     */
    private void assertApprovable(PurchaseOrderDO order, String username) {
        if (!DocStatus.PENDING.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "仅待审批状态可审批");
        }
        approvalGuard.assertApprovable(username, order.getCreator());
    }

    /**
     * 审批附加字段(审批人/审批时间)。
     *
     * @param username 当前用户
     * @return 列→值映射
     */
    private Map<String, Object> approveExtra(String username) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("\"approver\"", username);
        extra.put("approved_at", LocalDateTime.now());
        return extra;
    }

    /**
     * 批量组装 VO(供应商 + 物品 + 行)。
     *
     * @param orders 订单列表
     * @return VO 列表
     */
    private List<PurchaseOrderVO> toVOs(List<PurchaseOrderDO> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (PurchaseOrderDO o : orders) {
            supplierIds.add(o.getSupplierId());
            orderIds.add(o.getId());
        }
        Map<Long, SupplierVO> supplierMap = (supplierIds.isEmpty() ? List.<SupplierDO>of()
                : supplierMapper.selectByIds(supplierIds)).stream()
                .collect(java.util.stream.Collectors.toMap(SupplierDO::getId, this::toSupplierVO));
        List<PurchaseOrderItemDO> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItemDO>()
                        .in(PurchaseOrderItemDO::getOrderId, orderIds)
                        .orderByAsc(PurchaseOrderItemDO::getLineNo));
        Set<Long> itemIds = new HashSet<>();
        for (PurchaseOrderItemDO it : allItems) {
            itemIds.add(it.getItemId());
        }
        Map<Long, ItemDO> itemMap = (itemIds.isEmpty() ? List.<ItemDO>of()
                : itemMasterMapper.selectByIds(itemIds)).stream()
                .collect(java.util.stream.Collectors.toMap(ItemDO::getId, it -> it));
        Map<Long, List<PurchaseOrderItemDO>> itemMapByOrder = allItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(PurchaseOrderItemDO::getOrderId));

        List<PurchaseOrderVO> vos = new ArrayList<>();
        for (PurchaseOrderDO order : orders) {
            List<PurchaseOrderItemDO> lines =
                    itemMapByOrder.getOrDefault(order.getId(), List.of());
            List<PurchaseOrderItemVO> lineVos = new ArrayList<>();
            for (PurchaseOrderItemDO line : lines) {
                ItemDO item = itemMap.get(line.getItemId());
                lineVos.add(new PurchaseOrderItemVO(line.getId(), line.getLineNo(), line.getItemId(),
                        item == null ? null : item.getItemCode(),
                        item == null ? null : item.getItemName(),
                        line.getSpecSnapshot(), line.getUnit(),
                        QtyUtils.toContractString(line.getOrderedQty()),
                        QtyUtils.toContractString(line.getArrivedQty()),
                        line.getExpectedDeliveryDate(), line.getUnitPrice(), line.getTaxRate(),
                        QtyUtils.toContractString(line.getAmount()),
                        QtyUtils.toContractString(line.getTaxAmount()),
                        QtyUtils.toContractString(line.getTaxInclusiveTotal()),
                        line.getClosed(), line.getLineRemark()));
            }
            vos.add(new PurchaseOrderVO(order.getId(), order.getDocNo(), order.getDocDate(),
                    order.getSupplierId(), supplierMap.get(order.getSupplierId()), order.getBuyerId(),
                    order.getAllowOverReceiptRate(),
                    QtyUtils.toContractString(order.getTotalAmount()),
                    QtyUtils.toContractString(order.getTotalTaxAmount()),
                    QtyUtils.toContractString(order.getTotalTaxInclusive()),
                    order.getStatus(), order.getCreator(), order.getCreatedAt(),
                    order.getUpdater(), order.getUpdatedAt(), order.getApprover(),
                    order.getApprovedAt(), order.getRejectReason(), order.getRemark(), lineVos));
        }
        return vos;
    }

    /**
     * 实体转供应商 VO。
     *
     * @param s 实体
     * @return VO
     */
    private SupplierVO toSupplierVO(SupplierDO s) {
        return new SupplierVO(s.getId(), s.getSupplierCode(), s.getSupplierName(), s.getTaxNo(),
                s.getDefaultTaxRate(), s.getContact(), s.getPhone(), s.getAddress(),
                s.getSettleMethod(), s.getPayTermDays(), s.getStatus(), s.getRemark(),
                s.getCreator(), s.getCreatedAt(), s.getUpdater(), s.getUpdatedAt());
    }
}
