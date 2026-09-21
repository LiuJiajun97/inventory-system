package com.company.inventory.service.impl;

import com.company.inventory.common.constant.QuotationStatus;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.support.DocStateSupport;
import com.company.inventory.common.util.MoneyUtils;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.SalesQuotationItemMapper;
import com.company.inventory.mapper.SalesQuotationMapper;
import com.company.inventory.mapper.UserMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.sales.SalesQuotationCreateDTO;
import com.company.inventory.model.dto.sales.SalesQuotationLineDTO;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.sales.SalesQuotationDO;
import com.company.inventory.model.entity.sales.SalesQuotationItemDO;
import com.company.inventory.model.entity.user.UserDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.SalesQuotationQuery;
import com.company.inventory.model.vo.customer.CustomerVO;
import com.company.inventory.model.vo.sales.SalesQuotationItemVO;
import com.company.inventory.model.vo.sales.SalesQuotationVO;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.SalesQuotationService;

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
 * 销售报价单服务实现(V25,无审批:服务端价税重算 + 状态机 + 转换触发标记源单)。
 *
 * @author inventory
 */
@Service
public class SalesQuotationServiceImpl implements SalesQuotationService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesQuotationServiceImpl.class);

    /** 单据中文名(错误提示用)。 */
    private static final String DOC_NAME = "销售报价单";

    /** 默认税率(百分数,13%)。 */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("13.00");

    /** 过期允许的状态集合(展示标记用,转换时拦截)。 */
    private static final Set<String> EXPIRED_DISPLAY_STATUSES =
            Set.of(QuotationStatus.DRAFT, QuotationStatus.SENT);

    /** 表头 Mapper。 */
    private final SalesQuotationMapper quotationMapper;
    /** 行 Mapper。 */
    private final SalesQuotationItemMapper itemMapper;
    /** 客户 Mapper。 */
    private final CustomerMapper customerMapper;
    /** 物品 Mapper。 */
    private final ItemMapper itemMasterMapper;
    /** 用户 Mapper。 */
    private final UserMapper userMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 状态机支撑。 */
    private final DocStateSupport stateSupport;

    /**
     * 构造服务。
     *
     * @param quotationMapper  表头 Mapper
     * @param itemMapper       行 Mapper
     * @param customerMapper   客户 Mapper
     * @param itemMasterMapper 物品 Mapper
     * @param userMapper       用户 Mapper
     * @param warehouseMapper  仓库 Mapper
     * @param docNoService     单据号服务
     * @param stateSupport     状态机支撑
     */
    public SalesQuotationServiceImpl(SalesQuotationMapper quotationMapper,
            SalesQuotationItemMapper itemMapper, CustomerMapper customerMapper,
            ItemMapper itemMasterMapper, UserMapper userMapper, WarehouseMapper warehouseMapper,
            DocNoService docNoService, DocStateSupport stateSupport) {
        this.quotationMapper = quotationMapper;
        this.itemMapper = itemMapper;
        this.customerMapper = customerMapper;
        this.itemMasterMapper = itemMasterMapper;
        this.userMapper = userMapper;
        this.warehouseMapper = warehouseMapper;
        this.docNoService = docNoService;
        this.stateSupport = stateSupport;
    }

    /**
     * 新建报价单(draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationVO create(SalesQuotationCreateDTO dto, String username) {
        requireCustomer(dto.customerId());
        if (dto.salespersonId() != null) {
            requireUser(dto.salespersonId());
        }
        requireWarehouse(dto.warehouseId());
        SalesQuotationDO quotation = new SalesQuotationDO();
        quotation.setDocNo(docNoService.generateSalesQuotationNo());
        quotation.setDocDate(dto.docDate());
        quotation.setCustomerId(dto.customerId());
        quotation.setSalespersonId(dto.salespersonId());
        quotation.setWarehouseId(dto.warehouseId());
        quotation.setQuoteValidUntil(dto.quoteValidUntil());
        quotation.setStatus(QuotationStatus.DRAFT);
        quotation.setRemark(dto.remark());
        quotation.setCreator(username);
        quotation.setCreatedAt(LocalDateTime.now());
        quotationMapper.insert(quotation);
        applyLinesAndTotals(quotation, dto);
        quotationMapper.updateById(quotation);
        LOGGER.info("新建销售报价单: docNo={}, customerId={}, 行数={}, operator={}",
                quotation.getDocNo(), dto.customerId(), dto.items().size(), username);
        return get(quotation.getId());
    }

    /**
     * 编辑报价单(仅 draft)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationVO update(long id, SalesQuotationCreateDTO dto, String username) {
        SalesQuotationDO quotation = requireQuotation(id);
        if (!QuotationStatus.DRAFT.equals(quotation.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿状态可编辑");
        }
        requireCustomer(dto.customerId());
        if (dto.salespersonId() != null) {
            requireUser(dto.salespersonId());
        }
        requireWarehouse(dto.warehouseId());
        quotation.setDocDate(dto.docDate());
        quotation.setCustomerId(dto.customerId());
        quotation.setSalespersonId(dto.salespersonId());
        quotation.setWarehouseId(dto.warehouseId());
        quotation.setQuoteValidUntil(dto.quoteValidUntil());
        quotation.setRemark(dto.remark());
        quotation.setUpdater(username);
        quotation.setUpdatedAt(LocalDateTime.now());
        itemMapper.delete(new LambdaQueryWrapper<SalesQuotationItemDO>()
                .eq(SalesQuotationItemDO::getQuotationId, id));
        applyLinesAndTotals(quotation, dto);
        quotationMapper.updateById(quotation);
        LOGGER.info("编辑销售报价单: id={}, docNo={}, operator={}", id, quotation.getDocNo(), username);
        return get(id);
    }

    /**
     * 标记已发送(draft → sent)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationVO markSent(long id, String username) {
        SalesQuotationDO quotation = requireQuotation(id);
        if (!QuotationStatus.DRAFT.equals(quotation.getStatus())) {
            throw new BizException(DOC_NAME + "仅草稿状态可标记已发送");
        }
        int n = stateSupport.transition(quotationMapper, id,
                List.of(QuotationStatus.DRAFT), QuotationStatus.SENT, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 作废报价单(draft/sent → voided)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotationVO voidDoc(long id, String username) {
        SalesQuotationDO quotation = requireQuotation(id);
        stateSupport.assertWritable(quotation.getStatus(), DOC_NAME);
        int n = stateSupport.transition(quotationMapper, id,
                List.of(QuotationStatus.DRAFT, QuotationStatus.SENT),
                QuotationStatus.VOIDED, username);
        if (n == 0) {
            throw new BizException(DOC_NAME + "状态已变更,请刷新后重试");
        }
        return get(id);
    }

    /**
     * 报价单详情。
     */
    @Override
    public SalesQuotationVO get(long id) {
        SalesQuotationDO quotation = requireQuotation(id);
        return toVO(quotation);
    }

    /**
     * 报价单分页列表。
     */
    @Override
    public PageResult<SalesQuotationVO> list(SalesQuotationQuery query) {
        // 数据权限:未授权用户查空;授权用户只看发货仓在授权仓内的报价单(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<SalesQuotationDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(SalesQuotationDO::getWarehouseId, allowed);
        }
        if (query.getCustomerId() != null) {
            wrapper.eq(SalesQuotationDO::getCustomerId, query.getCustomerId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(SalesQuotationDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SalesQuotationDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(SalesQuotationDO::getDocDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(SalesQuotationDO::getDocDate, to);
        }
        wrapper.orderByDesc(SalesQuotationDO::getId);
        Page<SalesQuotationDO> page = quotationMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<SalesQuotationVO> vos = page.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return PageResult.of(vos, page.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 行与表头合计落库(服务端重算,不信前端传值)。
     *
     * @param quotation 报价单头(必须已 insert,有 ID)
     * @param dto       入参
     */
    private void applyLinesAndTotals(SalesQuotationDO quotation, SalesQuotationCreateDTO dto) {
        Set<Long> itemIds = new HashSet<>();
        for (SalesQuotationLineDTO line : dto.items()) {
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
            SalesQuotationLineDTO line = dto.items().get(i);
            ItemDO item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException("物品不存在: id=" + line.itemId());
            }
            if (line.unitPrice() == null && line.taxPrice() == null) {
                throw new BizException("第 " + (i + 1) + " 行:不含税单价与含税单价必填其一");
            }
            BigDecimal rate = line.taxRate() == null ? DEFAULT_TAX_RATE : line.taxRate();
            MoneyUtils.LineMoney lm = line.unitPrice() != null
                    ? MoneyUtils.fromExclusiveUnit(line.quantity(), line.unitPrice(), rate)
                    : MoneyUtils.fromInclusiveUnit(line.quantity(), line.taxPrice(), rate);
            BigDecimal amount = lm.amount();
            BigDecimal tax = lm.tax();
            BigDecimal inclusive = lm.inclusive();
            totalAmount = totalAmount.add(amount);
            totalTax = totalTax.add(tax);
            totalInclusive = totalInclusive.add(inclusive);

            SalesQuotationItemDO qi = new SalesQuotationItemDO();
            qi.setQuotationId(quotation.getId());
            qi.setLineNo(i + 1);
            qi.setItemId(line.itemId());
            qi.setQuantity(line.quantity());
            qi.setUnitPrice(lm.unitPrice());
            qi.setTaxPrice(lm.taxPrice());
            qi.setTaxRate(rate);
            qi.setAmount(amount);
            qi.setTaxAmount(tax);
            qi.setTotalAmount(inclusive);
            qi.setRemark(line.remark());
            itemMapper.insert(qi);
        }
        quotation.setTotalAmount(totalAmount);
        quotation.setTotalTaxAmount(totalTax);
        quotation.setTotalTaxInclusive(totalInclusive);
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
     * 校验仓库存在。
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
     * 查报价单,不存在抛 404,非 admin 无权访问该发货仓单据抛 403。
     *
     * @param id 报价单 ID
     * @return 报价单
     */
    private SalesQuotationDO requireQuotation(long id) {
        SalesQuotationDO quotation = quotationMapper.selectById(id);
        if (quotation == null) {
            throw BizException.notFound("销售报价单不存在");
        }
        assertWarehouseAccess(quotation.getWarehouseId());
        return quotation;
    }

    /**
     * 校验当前用户对发货仓的数据权限:非 admin 且发货仓不在授权仓内 → 403(admin 豁免)。
     *
     * @param warehouseId 发货仓 ID
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
     * 计算是否过期展示标记:quote_valid_until < 今天 且 状态为 draft/sent → true;
     * 未过期/无有效期/状态非 draft/sent → null(避免无意义的 false 标记)。
     *
     * @param quotation 报价单
     * @return true 已过期展示;null 未过期或无需展示
     */
    private Boolean isExpiredDisplay(SalesQuotationDO quotation) {
        if (quotation.getQuoteValidUntil() == null) {
            return null;
        }
        if (!EXPIRED_DISPLAY_STATUSES.contains(quotation.getStatus())) {
            return null;
        }
        return quotation.getQuoteValidUntil().isBefore(LocalDate.now()) ? Boolean.TRUE : null;
    }

    /**
     * DO 转 VO(详情用,含客户 + 行)。
     *
     * @param quotation 报价单头
     * @return VO
     */
    private SalesQuotationVO toVO(SalesQuotationDO quotation) {
        CustomerVO customerVO = null;
        if (quotation.getCustomerId() != null) {
            CustomerDO customer = customerMapper.selectById(quotation.getCustomerId());
            if (customer != null) {
                customerVO = toCustomerVO(customer);
            }
        }
        List<SalesQuotationItemDO> lines = itemMapper.selectList(
                new LambdaQueryWrapper<SalesQuotationItemDO>()
                        .eq(SalesQuotationItemDO::getQuotationId, quotation.getId())
                        .orderByAsc(SalesQuotationItemDO::getLineNo));
        Set<Long> itemIds = new HashSet<>();
        for (SalesQuotationItemDO line : lines) {
            itemIds.add(line.getItemId());
        }
        Map<Long, ItemDO> itemMap = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (ItemDO it : itemMasterMapper.selectByIds(itemIds)) {
                itemMap.put(it.getId(), it);
            }
        }
        List<SalesQuotationItemVO> lineVos = new ArrayList<>();
        for (SalesQuotationItemDO line : lines) {
            ItemDO item = itemMap.get(line.getItemId());
            lineVos.add(new SalesQuotationItemVO(line.getId(), line.getLineNo(),
                    line.getItemId(),
                    item == null ? null : item.getItemCode(),
                    item == null ? null : item.getItemName(),
                    item == null ? null : item.getUnit(),
                    QtyUtils.toContractString(line.getQuantity()),
                    line.getUnitPrice(), line.getTaxPrice(), line.getTaxRate(),
                    QtyUtils.toContractString(line.getAmount()),
                    QtyUtils.toContractString(line.getTaxAmount()),
                    QtyUtils.toContractString(line.getTotalAmount()),
                    line.getRemark()));
        }
        return new SalesQuotationVO(quotation.getId(), quotation.getDocNo(),
                quotation.getDocDate(),
                quotation.getCustomerId(), customerVO,
                quotation.getSalespersonId(), quotation.getWarehouseId(),
                QtyUtils.toContractString(quotation.getTotalAmount()),
                QtyUtils.toContractString(quotation.getTotalTaxAmount()),
                QtyUtils.toContractString(quotation.getTotalTaxInclusive()),
                quotation.getQuoteValidUntil(),
                quotation.getStatus(),
                isExpiredDisplay(quotation),
                quotation.getCreator(), quotation.getCreatedAt(),
                quotation.getUpdater(), quotation.getUpdatedAt(),
                quotation.getRemark(),
                lineVos);
    }

    /**
     * 客户实体转 VO。
     *
     * @param c 客户
     * @return 客户 VO
     */
    private CustomerVO toCustomerVO(CustomerDO c) {
        return new CustomerVO(c.getId(), c.getCustomerCode(), c.getCustomerName(), c.getTaxNo(),
                c.getDefaultTaxRate(), c.getContact(), c.getPhone(), c.getAddress(),
                c.getSettleMethod(), c.getPayTermDays(), c.getBankName(), c.getBankAccount(),
                c.getCreditLimit(), c.getDeliveryAddress(), c.getEmail(), c.getStatus(),
                c.getRemark(), c.getCreator(), c.getCreatedAt(), c.getUpdater(), c.getUpdatedAt());
    }
}