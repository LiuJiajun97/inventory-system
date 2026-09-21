package com.company.inventory.service.impl;

import com.company.inventory.common.constant.DocStatus;
import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.util.MoneyUtils;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.mapper.SalesOrderItemMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.SalesReturnItemMapper;
import com.company.inventory.mapper.SalesReturnMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.dto.inbound.InboundCreateDTO;
import com.company.inventory.model.dto.inbound.InboundLineDTO;
import com.company.inventory.model.dto.returns.SalesReturnCreateDTO;
import com.company.inventory.model.dto.returns.SalesReturnLineDTO;
import com.company.inventory.model.entity.returns.SalesReturnDO;
import com.company.inventory.model.entity.returns.SalesReturnItemDO;
import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.entity.sales.SalesOrderItemDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.model.query.SalesReturnQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.InvoiceService;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.SalesReturnService;
import com.company.inventory.model.vo.inbound.InboundDocCreatedVO;
import com.company.inventory.model.vo.returns.SalesReturnCreatedVO;
import com.company.inventory.model.vo.returns.SalesReturnItemVO;
import com.company.inventory.model.vo.returns.SalesReturnVO;
import com.company.inventory.model.vo.sales.SalesOrderItemVO;
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
 * 销售退货单服务实现:整单一个事务,退货单头 + 行 + 入库过账 + 原单行已退回写。
 *
 * <p>方案 A:退货单不另建加库存逻辑,过账时生成入库单(refType=sales_return,
 * refDocId=退货单 ID)走现有过账链路;价格锁原销售订单行快照(服务端取值,不信前端);
 * 行退货量上限 = 原行已发货 - 已退累计;不改 shipped_qty(净发货 = shipped - returned)。</p>
 *
 * @author inventory
 */
@Service
public class SalesReturnServiceImpl implements SalesReturnService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesReturnServiceImpl.class);

    /** 退货单头 Mapper。 */
    private final SalesReturnMapper returnDocMapper;
    /** 退货单行 Mapper。 */
    private final SalesReturnItemMapper returnItemMapper;
    /** 销售订单服务(审批态校验 + 原行 VO)。 */
    private final SalesOrderService salesOrderService;
    /** 销售订单行 Mapper(已退累计读取/回写)。 */
    private final SalesOrderItemMapper orderItemMapper;
    /** 销售订单 Mapper(列表展示原单号)。 */
    private final SalesOrderMapper orderMapper;
    /** 入库单服务(过账链路)。 */
    private final InboundService inboundService;
    /** 发票服务(退货自动生成红字凭单)。 */
    private final InvoiceService invoiceService;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 仓库 Mapper(列表展示仓库)。 */
    private final WarehouseMapper warehouseMapper;

    /**
     * 构造服务。
     *
     * @param returnDocMapper   退货单头 Mapper
     * @param returnItemMapper  退货单行 Mapper
     * @param salesOrderService 销售订单服务
     * @param orderItemMapper   销售订单行 Mapper
     * @param orderMapper       销售订单 Mapper
     * @param inboundService    入库单服务
     * @param invoiceService    发票服务
     * @param docNoService      单据号服务
     * @param warehouseMapper   仓库 Mapper
     */
    public SalesReturnServiceImpl(SalesReturnMapper returnDocMapper,
            SalesReturnItemMapper returnItemMapper, SalesOrderService salesOrderService,
            SalesOrderItemMapper orderItemMapper, SalesOrderMapper orderMapper,
            InboundService inboundService, InvoiceService invoiceService,
            DocNoService docNoService,
            WarehouseMapper warehouseMapper) {
        this.returnDocMapper = returnDocMapper;
        this.returnItemMapper = returnItemMapper;
        this.salesOrderService = salesOrderService;
        this.orderItemMapper = orderItemMapper;
        this.orderMapper = orderMapper;
        this.inboundService = inboundService;
        this.invoiceService = invoiceService;
        this.docNoService = docNoService;
        this.warehouseMapper = warehouseMapper;
    }

    /**
     * 新建销售退货单并过账(create 即过账,与出入库单一致)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 创建结果(含联动入库单号)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesReturnCreatedVO create(SalesReturnCreateDTO dto, String username) {
        // 1. 原销售订单必须存在且已审批通过(含自动 completed/手工 closed:已发货部分仍可退)
        SalesOrderDO order = orderMapper.selectById(dto.salesOrderId());
        if (order == null) {
            throw BizException.notFound("销售订单不存在");
        }
        if (!DocStatus.APPROVED.equals(order.getStatus())
                && !DocStatus.COMPLETED.equals(order.getStatus())
                && !DocStatus.CLOSED.equals(order.getStatus())) {
            throw new BizException("销售订单未审批通过,不允许退货");
        }
        Map<Long, SalesOrderItemVO> refItems = new java.util.HashMap<>();
        for (SalesOrderItemVO it : salesOrderService.get(dto.salesOrderId()).items()) {
            refItems.put(it.id(), it);
        }
        Set<Long> refLineIds = new HashSet<>();
        for (SalesReturnLineDTO line : dto.items()) {
            refLineIds.add(line.salesOrderItemId());
        }
        if (refLineIds.size() != dto.items().size()) {
            throw new BizException("同一原销售订单行不能重复退货");
        }
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, SalesOrderItemDO> refLineMap = (refLineIds.isEmpty()
                ? List.<SalesOrderItemDO>of()
                : orderItemMapper.selectByIds(refLineIds)).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, d -> d));
        // 2. 逐行校验可退量(上限 = 已发货 - 已退累计)
        for (int i = 0; i < dto.items().size(); i++) {
            SalesReturnLineDTO line = dto.items().get(i);
            SalesOrderItemDO refLine = refLineMap.get(line.salesOrderItemId());
            if (refItems.get(line.salesOrderItemId()) == null || refLine == null) {
                throw new BizException("销售订单行不存在: " + line.salesOrderItemId());
            }
            BigDecimal returned = refLine.getReturnedQty() == null
                    ? BigDecimal.ZERO : refLine.getReturnedQty();
            BigDecimal returnable = refLine.getShippedQty().subtract(returned);
            if (line.quantity().compareTo(returnable) > 0) {
                if (returnable.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException("第 " + (i + 1) + " 行:可退数量为 0");
                }
                throw new BizException("第 " + (i + 1) + " 行:退货数量超过可退数量("
                        + QtyUtils.toContractString(returnable) + ")");
            }
        }
        // 3. 插入退货单头 + 行(单价/税率锁原行快照,服务端取值,前端传值忽略)
        String docNo = docNoService.generateSalesReturnNo();
        SalesReturnDO doc = new SalesReturnDO();
        doc.setDocNo(docNo);
        doc.setDocDate(dto.docDate() == null ? LocalDate.now() : dto.docDate());
        doc.setSalesOrderId(dto.salesOrderId());
        doc.setWarehouseId(dto.warehouseId());
        doc.setStatus(ErrorCode.DOC_STATUS_FINISHED);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        returnDocMapper.insert(doc);
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < dto.items().size(); i++) {
            SalesReturnLineDTO line = dto.items().get(i);
            SalesOrderItemVO ref = refItems.get(line.salesOrderItemId());
            BigDecimal unitPrice = ref.unitPrice() == null ? BigDecimal.ZERO : ref.unitPrice();
            BigDecimal taxRate = ref.taxRate() == null ? BigDecimal.ZERO : ref.taxRate();
            BigDecimal taxPrice = ref.taxPrice() == null ? BigDecimal.ZERO : ref.taxPrice();
            BigDecimal amount = MoneyUtils.amountOf(line.quantity(), unitPrice);
            BigDecimal tax = MoneyUtils.taxOf(amount, taxRate);
            SalesReturnItemDO item = new SalesReturnItemDO();
            item.setDocId(doc.getId());
            item.setLineNo(i + 1);
            item.setSalesOrderItemId(line.salesOrderItemId());
            item.setItemId(ref.itemId());
            item.setSpecSnapshot(ref.specSnapshot());
            item.setUnit(ref.unit());
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setTaxPrice(taxPrice);
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
        // 4. 生成入库单并过账(库存增加走现有链路,refType=sales_return 落手工单分支)
        List<InboundLineDTO> inLines = dto.items().stream().map(line -> {
            SalesOrderItemVO ref = refItems.get(line.salesOrderItemId());
            BigDecimal unitPrice = ref.unitPrice() == null ? BigDecimal.ZERO : ref.unitPrice();
            BigDecimal taxRate = ref.taxRate() == null ? BigDecimal.ZERO : ref.taxRate();
            BigDecimal taxPrice = ref.taxPrice() == null ? BigDecimal.ZERO : ref.taxPrice();
            return new InboundLineDTO(ref.itemId(), line.quantity(), line.batchNo(),
                    line.productionDate(), line.expiryDate(), null, line.locationId(),
                    line.serialNos(), unitPrice, taxRate, null, null);
        }).toList();
        InboundDocCreatedVO inDoc = inboundService.create(new InboundCreateDTO(
                dto.warehouseId(), dto.remark(), inLines,
                ErrorCode.REF_TYPE_SALES_RETURN, doc.getId(), doc.getDocDate(),
                null, null, null, "销售退货", username), username);
        // 5. 回写原销售订单行已退累计(returned_qty += 本次退货量;shipped_qty 不动)
        for (SalesReturnLineDTO line : dto.items()) {
            SalesOrderItemDO refLine = refLineMap.get(line.salesOrderItemId());
            BigDecimal returned = refLine.getReturnedQty() == null
                    ? BigDecimal.ZERO : refLine.getReturnedQty();
            refLine.setReturnedQty(returned.add(line.quantity()));
            orderItemMapper.updateById(refLine);
        }
        // 6. 同事务自动生成负数草稿发票(贷项凭单,确认后核减应收)
        invoiceService.generateForSalesReturn(doc, username);
        LOGGER.info("新建销售退货单: docNo={}, salesOrderId={}, warehouseId={}, 行数={}, operator={}",
                docNo, dto.salesOrderId(), dto.warehouseId(), dto.items().size(), username);
        return new SalesReturnCreatedVO(doc.getId(), docNo, doc.getWarehouseId(),
                doc.getStatus(), dto.remark(), doc.getCreator(), inDoc.docNo(), doc.getCreatedAt());
    }

    /**
     * 销售退货单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<SalesReturnVO> list(SalesReturnQuery query) {
        // 数据权限:未授权用户查空;授权用户只查授权仓(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<SalesReturnDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(SalesReturnDO::getWarehouseId, allowed);
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(SalesReturnDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(SalesReturnDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SalesReturnDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(SalesReturnDO::getDocDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(SalesReturnDO::getDocDate, to);
        }
        wrapper.orderByDesc(SalesReturnDO::getId);
        Page<SalesReturnDO> result = returnDocMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<SalesReturnDO> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(),
                    query.getPage(), query.getPageSize());
        }
        return PageResult.of(toVOs(rows), result.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 销售退货单详情。
     *
     * @param id 单据 ID
     * @return 单据(含仓库与原销售订单单号)
     */
    @Override
    public SalesReturnVO get(long id) {
        SalesReturnDO doc = returnDocMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("销售退货单不存在");
        }
        return toVOs(List.of(doc)).get(0);
    }

    /**
     * 批量组装单据 VO(仓库 + 单据行 + 原销售订单单号)。
     *
     * @param docs 单据列表
     * @return VO 列表
     */
    private List<SalesReturnVO> toVOs(List<SalesReturnDO> docs) {
        Set<Long> whIds = docs.stream().map(SalesReturnDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(SalesReturnDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> orderIds = docs.stream().map(SalesReturnDO::getSalesOrderId)
                .collect(Collectors.toCollection(HashSet::new));
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        Map<Long, SalesOrderDO> orderMap = (orderIds.isEmpty() ? List.<SalesOrderDO>of()
                : orderMapper.selectByIds(orderIds)).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        List<SalesReturnItemDO> allItems = returnItemMapper.selectList(
                new LambdaQueryWrapper<SalesReturnItemDO>()
                        .in(SalesReturnItemDO::getDocId, docIds)
                        .orderByAsc(SalesReturnItemDO::getId));
        Map<Long, List<SalesReturnItemDO>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(SalesReturnItemDO::getDocId));

        List<SalesReturnVO> vos = new ArrayList<>();
        for (SalesReturnDO doc : docs) {
            List<SalesReturnItemDO> items = itemMap.getOrDefault(doc.getId(), List.of());
            List<SalesReturnItemVO> itemVos = items.stream().map(this::toItemVO).toList();
            SalesOrderDO order = orderMap.get(doc.getSalesOrderId());
            vos.add(new SalesReturnVO(doc.getId(), doc.getDocNo(), doc.getDocDate(),
                    doc.getSalesOrderId(), order == null ? null : order.getDocNo(),
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
    private SalesReturnItemVO toItemVO(SalesReturnItemDO item) {
        return new SalesReturnItemVO(item.getId(), item.getDocId(), item.getLineNo(),
                item.getSalesOrderItemId(), item.getItemId(), item.getSpecSnapshot(),
                item.getUnit(), QtyUtils.toContractString(item.getQuantity()),
                item.getUnitPrice(), item.getTaxPrice(), item.getTaxRate(),
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
