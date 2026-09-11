package com.company.inventory.service.impl;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.ApprovalGuard;
import com.company.inventory.common.support.DocStateSupport;
import com.company.inventory.common.util.MoneyUtils;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.sales.SalesActionDTO;
import com.company.inventory.model.dto.sales.SalesOrderCreateDTO;
import com.company.inventory.model.dto.sales.SalesOrderLineDTO;
import com.company.inventory.model.dto.sales.ShipLine;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.entity.sales.SalesOrderItemDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SalesOrderItemMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.SalesOrderQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.model.vo.sales.SalesOrderItemVO;
import com.company.inventory.model.vo.sales.SalesOrderVO;
import com.company.inventory.model.vo.customer.CustomerVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
 * 销售订单服务实现:状态机 + 价税重算 + 审批 FEFO 预占(方案 §5.2)+ 发货回写 + 关闭/作废释放预占。
 *
 * @author inventory
 */
@Service
public class SalesOrderServiceImpl implements SalesOrderService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesOrderServiceImpl.class);

    /** 单据中文名(错误提示用)。 */
    private static final String DOC_NAME = "销售订单";

    /** 默认税率(百分数,13%)。 */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13.00");

    /** 表头 Mapper。 */
    private final SalesOrderMapper orderMapper;
    /** 行 Mapper。 */
    private final SalesOrderItemMapper itemMapper;
    /** 客户 Mapper。 */
    private final CustomerMapper customerMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMasterMapper;
    /** 用户 Mapper。 */
    private final UserMapper userMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 审批资格校验。 */
    private final ApprovalGuard approvalGuard;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;
    /** 库存核心服务(预占/释放)。 */
    private final StockCoreService stockCoreService;
    /** 事务管理器(预占与状态变更分事务:预占失败订单回 draft 需独立提交)。 */
    private final PlatformTransactionManager transactionManager;

    /**
     * 构造服务。
     *
     * @param orderMapper          表头 Mapper
     * @param itemMapper           行 Mapper
     * @param customerMapper       客户 Mapper
     * @param itemMasterMapper     物品 Mapper
     * @param userMapper           用户 Mapper
     * @param warehouseMapper      仓库 Mapper
     * @param approvalGuard        审批资格校验
     * @param docNoService         单据号服务
     * @param stateSupport         状态机支撑
     * @param stockCoreService     库存核心服务
     * @param transactionManager   事务管理器
     */
    public SalesOrderServiceImpl(SalesOrderMapper orderMapper, SalesOrderItemMapper itemMapper,
            CustomerMapper customerMapper, ItemMapper itemMasterMapper, UserMapper userMapper,
            WarehouseMapper warehouseMapper, ApprovalGuard approvalGuard, DocNoService docNoService,
            DocStateSupport stateSupport, StockCoreService stockCoreService,
            PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.customerMapper = customerMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.userMapper = userMapper;
        this.warehouseMapper = warehouseMapper;
        this.approvalGuard = approvalGuard;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
        this.stockCoreService = stockCoreService;
        this.transactionManager = transactionManager;
    }

    /**
     * 新建销售订单(draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO create(SalesOrderCreateDTO dto, String username) {
        requireCustomer(dto.customerId());
        requireUser(dto.salespersonId());
        requireWarehouse(dto.warehouseId());
        SalesOrderDO order = new SalesOrderDO();
        order.setDocNo(docNoService.generateSalesOrderNo());
        order.setDocDate(dto.docDate());
        order.setCustomerId(dto.customerId());
        order.setSalespersonId(dto.salespersonId());
        order.setWarehouseId(dto.warehouseId());
        order.setStatus(DocStatus.DRAFT);
        order.setRemark(dto.remark());
        order.setCreator(username);
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);
        applyLinesAndTotals(order, dto);
        orderMapper.updateById(order);
        LOGGER.info("新建销售订单: docNo={}, customerId={}, 仓库={}, 行数={}, operator={}",
                order.getDocNo(), dto.customerId(), dto.warehouseId(), dto.items().size(), username);
        return get(order.getId());
    }

    /**
     * 销售订单分页列表。
     */
    @Override
    public PageResult<SalesOrderVO> list(SalesOrderQuery query) {
        LambdaQueryWrapper<SalesOrderDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getCustomerId() != null) {
            wrapper.eq(SalesOrderDO::getCustomerId, query.getCustomerId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(SalesOrderDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SalesOrderDO::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getFrom())) {
            wrapper.ge(SalesOrderDO::getDocDate, query.getFrom().trim());
        }
        if (StringUtils.hasText(query.getTo())) {
            wrapper.le(SalesOrderDO::getDocDate, query.getTo().trim());
        }
        wrapper.orderByDesc(SalesOrderDO::getId);
        Page<SalesOrderDO> page = orderMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 订单详情。
     */
    @Override
    public SalesOrderVO get(long id) {
        SalesOrderDO order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("销售订单不存在");
        }
        return toVOs(List.of(order)).get(0);
    }

    /**
     * 编辑订单(仅 draft/rejected,rejected 编辑后回 draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO update(long id, SalesOrderCreateDTO dto, String username) {
        SalesOrderDO order = requireOrder(id);
        stateSupport.assertWritable(order.getStatus(), DOC_NAME);
        if (!DocStatus.DRAFT.equals(order.getStatus()) && !DocStatus.REJECTED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿/已驳回状态可编辑");
        }
        requireCustomer(dto.customerId());
        requireUser(dto.salespersonId());
        requireWarehouse(dto.warehouseId());
        order.setDocDate(dto.docDate());
        order.setCustomerId(dto.customerId());
        order.setSalespersonId(dto.salespersonId());
        order.setWarehouseId(dto.warehouseId());
        order.setRemark(dto.remark());
        order.setUpdater(username);
        order.setUpdatedAt(LocalDateTime.now());
        if (DocStatus.REJECTED.equals(order.getStatus())) {
            order.setStatus(DocStatus.DRAFT);
            order.setRejectReason(null);
        }
        itemMapper.delete(new LambdaQueryWrapper<SalesOrderItemDO>()
                .eq(SalesOrderItemDO::getOrderId, id));
        applyLinesAndTotals(order, dto);
        orderMapper.updateById(order);
        LOGGER.info("编辑销售订单: id={}, docNo={}, operator={}", id, order.getDocNo(), username);
        return get(id);
    }

    /**
     * 提交:draft/rejected → pending。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO submit(long id, String username) {
        SalesOrderDO order = requireOrder(id);
        stateSupport.assertWritable(order.getStatus(), DOC_NAME);
        int n = stateSupport.transition(orderMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.REJECTED), DocStatus.PENDING, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 审批通过:先独立事务逐行 FEFO 预占(任一失败整体回滚且订单回 draft),
     * 再条件迁移 pending → approved(审批人≠制单人;已 approved 幂等)。
     */
    @Override
    public SalesOrderVO approve(long id, String username) {
        SalesOrderDO order = requireOrder(id);
        if (DocStatus.APPROVED.equals(order.getStatus())) {
            return get(id);
        }
        assertApprovable(order, username);
        List<SalesOrderItemDO> lines = selectLines(id);
        if (lines.isEmpty()) {
            throw new BizException(DOC_NAME + "无订单行,不可审批");
        }
        Map<Long, ItemDO> itemMap = loadItems(lines);

        // 预占独立事务:失败整体回滚,订单单独回 draft 并提交
        TransactionTemplate ttx = new TransactionTemplate(transactionManager);
        BizException preAllocError = null;
        try {
            ttx.executeWithoutResult(status -> {
                for (SalesOrderItemDO line : lines) {
                    ItemDO item = itemMap.get(line.getItemId());
                    String desc = "行" + line.getLineNo() + "("
                            + (item == null ? line.getItemId() : item.getItemCode()) + ")";
                    stockCoreService.preAlloc(order.getWarehouseId(), line.getItemId(),
                            line.getOrderedQty(), desc);
                }
            });
        } catch (BizException e) {
            preAllocError = e;
        }
        if (preAllocError != null) {
            stateSupport.transition(orderMapper, id, List.of(DocStatus.PENDING),
                    DocStatus.DRAFT, username);
            throw preAllocError;
        }

        int n = stateSupport.transitionWith(orderMapper, id, List.of(DocStatus.PENDING),
                DocStatus.APPROVED, username, approveExtra(username));
        if (n == 0) {
            // 极端并发:状态已变,释放刚做的预占后报错
            releaseRemaining(order, lines);
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        LOGGER.info("销售订单审批通过(已预占): id={}, docNo={}, approver={}", id, order.getDocNo(), username);
        return get(id);
    }

    /**
     * 驳回:pending → rejected(原因必填)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO reject(long id, SalesActionDTO dto, String username) {
        SalesOrderDO order = requireOrder(id);
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
     * 作废:任意未执行状态 → voided;已审批先释放未发货预占。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO voidDoc(long id, String username) {
        SalesOrderDO order = requireOrder(id);
        stateSupport.assertWritable(order.getStatus(), DOC_NAME);
        if (DocStatus.APPROVED.equals(order.getStatus())) {
            releaseRemaining(order, selectLines(id));
        }
        int n = stateSupport.transition(orderMapper, id,
                List.of(DocStatus.DRAFT, DocStatus.PENDING, DocStatus.REJECTED, DocStatus.APPROVED),
                DocStatus.VOIDED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 手工关闭:approved → closed,释放未发货部分预占。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO close(long id, String username) {
        SalesOrderDO order = requireOrder(id);
        if (!DocStatus.APPROVED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "仅已审批状态可关闭");
        }
        releaseRemaining(order, selectLines(id));
        int n = stateSupport.transition(orderMapper, id, List.of(DocStatus.APPROVED),
                DocStatus.CLOSED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 销售发货回写:逐行条件 UPDATE(超发拒绝并提示行号),行达标置 closed,全行 closed 自动 completed。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyShipment(long orderId, List<ShipLine> lines) {
        SalesOrderDO order = requireOrder(orderId);
        if (!DocStatus.APPROVED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "未审批通过,不允许发货出库");
        }
        for (ShipLine line : lines) {
            SalesOrderItemDO oi = itemMapper.selectById(line.orderLineId());
            if (oi == null || oi.getOrderId() == null || oi.getOrderId() != orderId) {
                throw new BizException("销售订单行不存在或不属于该订单: " + line.orderLineId());
            }
            int n = itemMapper.applyShipment(oi.getId(), orderId, line.qty());
            if (n == 0) {
                throw new BizException("超发拒绝:行 " + oi.getLineNo()
                        + "(物品 " + oi.getItemId() + "),已发货 "
                        + QtyUtils.toContractString(oi.getShippedQty())
                        + ",本次 " + QtyUtils.toContractString(line.qty())
                        + ",超出订单量");
            }
        }
        Long open = itemMapper.selectCount(new LambdaQueryWrapper<SalesOrderItemDO>()
                .eq(SalesOrderItemDO::getOrderId, orderId).ne(SalesOrderItemDO::getClosed, true));
        if (open != null && open == 0) {
            stateSupport.transition(orderMapper, orderId, List.of(DocStatus.APPROVED),
                    DocStatus.COMPLETED,
                    order.getCreator() == null ? "system" : order.getCreator());
        }
        LOGGER.info("销售发货回写: orderId={}, 行数={}", orderId, lines.size());
    }

    /**
     * 校验订单已审批并返回行。
     */
    @Override
    public Map<Long, SalesOrderItemVO> requireApprovedItems(long orderId) {
        SalesOrderDO order = requireOrder(orderId);
        if (!DocStatus.APPROVED.equals(order.getStatus())) {
            throw new BizException(DOC_NAME + "未审批通过,不允许发货出库");
        }
        Map<Long, SalesOrderItemVO> map = new HashMap<>();
        for (SalesOrderItemVO it : get(orderId).items()) {
            map.put(it.id(), it);
        }
        return map;
    }

    /**
     * 订单发货仓库 ID。
     */
    @Override
    public long requireWarehouseId(long orderId) {
        return requireOrder(orderId).getWarehouseId();
    }

    /**
     * 释放订单未发货部分预占(逐行 ordered - shipped,>0 才释放)。
     *
     * @param order 订单
     * @param lines 订单行
     */
    private void releaseRemaining(SalesOrderDO order, List<SalesOrderItemDO> lines) {
        for (SalesOrderItemDO line : lines) {
            BigDecimal remaining = line.getOrderedQty().subtract(line.getShippedQty());
            if (remaining.signum() > 0) {
                stockCoreService.releasePreAlloc(order.getWarehouseId(), line.getItemId(),
                        remaining, "行" + line.getLineNo());
            }
        }
    }

    /**
     * 行与表头合计落库(服务端重算,不信前端传值)。
     *
     * @param order 订单头(必须已 insert,有 ID)
     * @param dto   入参
     */
    private void applyLinesAndTotals(SalesOrderDO order, SalesOrderCreateDTO dto) {
        Set<Long> itemIds = new HashSet<>();
        for (SalesOrderLineDTO line : dto.items()) {
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
            SalesOrderLineDTO line = dto.items().get(i);
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

            SalesOrderItemDO oi = new SalesOrderItemDO();
            oi.setOrderId(order.getId());
            oi.setLineNo(i + 1);
            oi.setItemId(line.itemId());
            oi.setSpecSnapshot(item.getSpec());
            oi.setUnit(item.getUnit());
            oi.setOrderedQty(line.orderedQty());
            oi.setShippedQty(BigDecimal.ZERO);
            oi.setCustomerDeliveryDate(line.customerDeliveryDate());
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
     * 查询订单行。
     *
     * @param orderId 订单 ID
     * @return 行列表
     */
    private List<SalesOrderItemDO> selectLines(long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .eq(SalesOrderItemDO::getOrderId, orderId)
                .orderByAsc(SalesOrderItemDO::getLineNo));
    }

    /**
     * 批量加载行涉及物品。
     *
     * @param lines 订单行
     * @return 物品 ID → 物品
     */
    private Map<Long, ItemDO> loadItems(List<SalesOrderItemDO> lines) {
        Set<Long> itemIds = lines.stream().map(SalesOrderItemDO::getItemId)
                .collect(Collectors.toCollection(HashSet::new));
        List<ItemDO> items = itemIds.isEmpty() ? List.of() : itemMasterMapper.selectByIds(itemIds);
        Map<Long, ItemDO> map = new HashMap<>();
        for (ItemDO it : items) {
            map.put(it.getId(), it);
        }
        return map;
    }

    /**
     * 校验客户存在且启用。
     *
     * @param customerId 客户 ID
     */
    private void requireCustomer(long customerId) {
        CustomerDO customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BizException("客户不存在: id=" + customerId);
        }
        if (customer.getStatus() != null && customer.getStatus() != 1) {
            throw new BizException("客户已停用: " + customer.getCustomerCode());
        }
    }

    /**
     * 校验用户存在(销售员)。
     *
     * @param userId 用户 ID
     */
    private void requireUser(long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("销售员不存在: id=" + userId);
        }
    }

    /**
     * 校验仓库存在(发货仓)。
     *
     * @param warehouseId 仓库 ID
     */
    private void requireWarehouse(long warehouseId) {
        WarehouseDO warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BizException("发货仓库不存在: id=" + warehouseId);
        }
    }

    /**
     * 查订单,不存在抛 404。
     *
     * @param id 订单 ID
     * @return 订单
     */
    private SalesOrderDO requireOrder(long id) {
        SalesOrderDO order = orderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("销售订单不存在");
        }
        return order;
    }

    /**
     * 校验可审批:pending + 审批资格(管理员可自批,库员禁自批)。
     *
     * @param order    订单
     * @param username 当前用户
     */
    private void assertApprovable(SalesOrderDO order, String username) {
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
        extra.put("approver", username);
        extra.put("approved_at", LocalDateTime.now());
        return extra;
    }

    /**
     * 批量组装 VO(客户 + 物品 + 行)。
     *
     * @param orders 订单列表
     * @return VO 列表
     */
    private List<SalesOrderVO> toVOs(List<SalesOrderDO> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> customerIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (SalesOrderDO o : orders) {
            customerIds.add(o.getCustomerId());
            orderIds.add(o.getId());
        }
        Map<Long, CustomerVO> customerMap = (customerIds.isEmpty() ? List.<CustomerDO>of()
                : customerMapper.selectByIds(customerIds)).stream()
                .collect(Collectors.toMap(CustomerDO::getId, this::toCustomerVO));
        List<SalesOrderItemDO> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<SalesOrderItemDO>()
                        .in(SalesOrderItemDO::getOrderId, orderIds)
                        .orderByAsc(SalesOrderItemDO::getLineNo));
        Set<Long> itemIds = new HashSet<>();
        for (SalesOrderItemDO it : allItems) {
            itemIds.add(it.getItemId());
        }
        Map<Long, ItemDO> itemMap = (itemIds.isEmpty() ? List.<ItemDO>of()
                : itemMasterMapper.selectByIds(itemIds)).stream()
                .collect(Collectors.toMap(ItemDO::getId, it -> it));
        Map<Long, List<SalesOrderItemDO>> linesByOrder = allItems.stream()
                .collect(Collectors.groupingBy(SalesOrderItemDO::getOrderId));

        List<SalesOrderVO> vos = new ArrayList<>();
        for (SalesOrderDO order : orders) {
            List<SalesOrderItemDO> lines = linesByOrder.getOrDefault(order.getId(), List.of());
            List<SalesOrderItemVO> lineVos = new ArrayList<>();
            for (SalesOrderItemDO line : lines) {
                ItemDO item = itemMap.get(line.getItemId());
                lineVos.add(new SalesOrderItemVO(line.getId(), line.getLineNo(), line.getItemId(),
                        item == null ? null : item.getItemCode(),
                        item == null ? null : item.getItemName(),
                        line.getSpecSnapshot(), line.getUnit(),
                        QtyUtils.toContractString(line.getOrderedQty()),
                        QtyUtils.toContractString(line.getShippedQty()),
                        line.getCustomerDeliveryDate(), line.getUnitPrice(), line.getTaxRate(),
                        QtyUtils.toContractString(line.getAmount()),
                        QtyUtils.toContractString(line.getTaxAmount()),
                        QtyUtils.toContractString(line.getTaxInclusiveTotal()),
                        line.getClosed(), line.getLineRemark()));
            }
            vos.add(new SalesOrderVO(order.getId(), order.getDocNo(), order.getDocDate(),
                    order.getCustomerId(), customerMap.get(order.getCustomerId()),
                    order.getSalespersonId(), order.getWarehouseId(),
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
     * 实体转客户 VO。
     *
     * @param c 实体
     * @return VO
     */
    private CustomerVO toCustomerVO(CustomerDO c) {
        return new CustomerVO(c.getId(), c.getCustomerCode(), c.getCustomerName(), c.getTaxNo(),
                c.getDefaultTaxRate(), c.getContact(), c.getPhone(), c.getAddress(),
                c.getSettleMethod(), c.getPayTermDays(), c.getStatus(), c.getRemark(),
                c.getCreator(), c.getCreatedAt(), c.getUpdater(), c.getUpdatedAt());
    }
}
