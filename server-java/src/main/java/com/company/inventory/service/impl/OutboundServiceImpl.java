package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.support.DataScope;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.util.MoneyUtils;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.outbound.OutboundCreateDTO;
import com.company.inventory.model.dto.outbound.OutboundLineDTO;
import com.company.inventory.model.dto.sales.ShipLine;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.entity.outbound.OutboundDocDO;
import com.company.inventory.model.entity.outbound.OutboundDocItemDO;
import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.OutboundDocItemMapper;
import com.company.inventory.mapper.OutboundDocMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.OutboundDocQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.OutboundService;
import com.company.inventory.service.SalesOrderService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.model.vo.outbound.OutboundDocCreatedVO;
import com.company.inventory.model.vo.outbound.OutboundDocItemVO;
import com.company.inventory.model.vo.outbound.OutboundDocVO;
import com.company.inventory.model.vo.sales.SalesOrderItemVO;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.model.vo.stock.StockOpResult;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出库单服务实现:整单一个事务,单据头 + 行 + 库存扣减 + 序列号同事务。
 *
 * <p>一期扩展:refType=sales 关联销售订单发货,库存核心走销售扣减(优先扣预占行并同步释放),
 * 出库成功后同事务回写订单 shippedQty(超发拒绝并提示行号)。</p>
 *
 * @author inventory
 */
@Service
public class OutboundServiceImpl implements OutboundService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboundServiceImpl.class);

    /** 单据 Mapper。 */
    private final OutboundDocMapper outboundDocMapper;
    /** 单据行 Mapper。 */
    private final OutboundDocItemMapper outboundDocItemMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 库存核心服务。 */
    private final StockCoreService stockCoreService;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 销售订单服务(发货回写)。 */
    private final SalesOrderService salesOrderService;
    /** 销售订单 Mapper(列表展示关联单号)。 */
    private final SalesOrderMapper salesOrderMapper;
    /** 客户 Mapper(列表展示关联客户)。 */
    private final CustomerMapper customerMapper;
    /** 序列号 Mapper(V9 追溯链补写)。 */
    private final SerialMapper serialMapper;
    /** JSON 序列化(serialNos 列存 JSON 数组字符串)。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造服务。
     *
     * @param outboundDocMapper     单据 Mapper
     * @param outboundDocItemMapper 单据行 Mapper
     * @param warehouseMapper       仓库 Mapper
     * @param stockCoreService      库存核心服务
     * @param docNoService          单据号服务
     * @param salesOrderService     销售订单服务
     * @param salesOrderMapper      销售订单 Mapper
     * @param customerMapper        客户 Mapper
     * @param serialMapper          序列号 Mapper
     * @param objectMapper          JSON 序列化器
     */
    public OutboundServiceImpl(OutboundDocMapper outboundDocMapper,
                               OutboundDocItemMapper outboundDocItemMapper,
                               WarehouseMapper warehouseMapper,
                               StockCoreService stockCoreService,
                               DocNoService docNoService,
                               SalesOrderService salesOrderService,
                               SalesOrderMapper salesOrderMapper,
                               CustomerMapper customerMapper,
                               SerialMapper serialMapper,
                               ObjectMapper objectMapper) {
        this.outboundDocMapper = outboundDocMapper;
        this.outboundDocItemMapper = outboundDocItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.stockCoreService = stockCoreService;
        this.docNoService = docNoService;
        this.salesOrderService = salesOrderService;
        this.salesOrderMapper = salesOrderMapper;
        this.customerMapper = customerMapper;
        this.serialMapper = serialMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 新建出库单(手工出库或销售发货)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 单据头
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundDocCreatedVO create(OutboundCreateDTO dto, String username) {
        // V9 追溯链:方法开头记录基准时间(留 5 秒容差,防止 JVM 与数据库时钟漂移导致漏补写),
        // 过账后补写本单序列号的 ref_out_doc_no
        LocalDateTime ts = LocalDateTime.now().minusSeconds(5);
        String docNo = docNoService.generateOutboundDocNo();

        boolean salesShip = ErrorCode.REF_TYPE_SALES.equals(dto.refType())
                && dto.refDocId() != null;
        Map<Long, SalesOrderItemVO> refItems = Map.of();
        if (salesShip) {
            long orderWarehouse = salesOrderService.requireWarehouseId(dto.refDocId());
            if (orderWarehouse != dto.warehouseId()) {
                throw new BizException("出库仓库必须与销售订单发货仓库一致");
            }
            refItems = salesOrderService.requireApprovedItems(dto.refDocId());
            for (OutboundLineDTO line : dto.items()) {
                if (line.refLineId() == null) {
                    throw new BizException("销售发货行必须选择销售订单行");
                }
                SalesOrderItemVO ref = refItems.get(line.refLineId());
                if (ref == null || !ref.itemId().equals(line.itemId())) {
                    throw new BizException("销售订单行不存在或物品不匹配: " + line.refLineId());
                }
            }
        }

        OutboundDocDO doc = new OutboundDocDO();
        doc.setDocNo(docNo);
        doc.setWarehouseId(dto.warehouseId());
        doc.setStatus(ErrorCode.DOC_STATUS_FINISHED);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        doc.setRefType(dto.refType());
        doc.setRefDocId(dto.refDocId());
        doc.setDocDate(dto.docDate());
        doc.setCarrier(dto.carrier());
        doc.setVehicleNo(dto.vehicleNo());
        doc.setFreight(dto.freight());
        doc.setDocType(dto.docType());
        doc.setHandler(dto.handler());
        outboundDocMapper.insert(doc);

        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(dto.warehouseId());
        request.setLines(toStockLines(dto));
        request.setDocNo(docNo);
        request.setOperator(username);
        request.setSalesShip(salesShip);
        StockOpResult stockResult = stockCoreService.outbound(request);

        List<StockOpResult.StockOpRow> resultRows = stockResult.getRows();
        List<ShipLine> shipLines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < dto.items().size(); i++) {
            OutboundLineDTO line = dto.items().get(i);
            StockOpResult.StockOpRow resultRow = resultRows.get(i);
            OutboundDocItemDO docItem = new OutboundDocItemDO();
            docItem.setDocId(doc.getId());
            docItem.setItemId(line.itemId());
            docItem.setQuantity(line.qty());
            docItem.setBatchId(resultRow.batchId());
            docItem.setLocationId(resultRow.locationId());
            docItem.setSerialNos(toJsonArray(line.serialNos()));
            docItem.setRefLineId(line.refLineId());
            if (salesShip) {
                // 出库参考价携带订单行单价(服务端取值,不信前端传值),税率同源于订单行
                SalesOrderItemVO ref = refItems.get(line.refLineId());
                docItem.setUnitPrice(ref.unitPrice());
                docItem.setTaxRate(ref.taxRate());
                shipLines.add(new ShipLine(line.refLineId(), line.qty()));
            } else {
                docItem.setUnitPrice(line.unitPrice());
                docItem.setTaxRate(line.taxRate());
            }
            // V10 金额快照:行号从 1 连号,金额=数量×(不含税单价??0),税额=金额×(税率??0)/100
            BigDecimal amount = MoneyUtils.amountOf(line.qty(),
                    docItem.getUnitPrice() == null ? BigDecimal.ZERO : docItem.getUnitPrice());
            BigDecimal tax = MoneyUtils.taxOf(amount,
                    docItem.getTaxRate() == null ? BigDecimal.ZERO : docItem.getTaxRate());
            docItem.setLineNo(i + 1);
            docItem.setAmount(amount);
            docItem.setTaxAmount(tax);
            docItem.setTaxInclusiveTotal(MoneyUtils.inclusiveOf(amount, tax));
            totalAmount = totalAmount.add(amount);
            outboundDocItemMapper.insert(docItem);
        }
        // V10 头总金额=行金额合计(服务端重算)
        doc.setTotalAmount(totalAmount);
        outboundDocMapper.updateById(doc);
        if (salesShip) {
            salesOrderService.applyShipment(dto.refDocId(), shipLines);
        }
        backfillSerialRef(dto.items(), docNo, dto.warehouseId(), ts);
        LOGGER.info("新建出库单: docNo={}, warehouseId={}, refType={}, 行数={}, operator={}",
                docNo, dto.warehouseId(), dto.refType(), dto.items().size(), username);
        return toCreatedVO(doc);
    }

    /**
     * 出库单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<OutboundDocVO> list(OutboundDocQuery query) {
        // 数据权限:未授权用户查空;授权用户只查授权仓(admin 豁免不过滤)
        List<Long> allowed = DataScope.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getPageSize());
        }
        LambdaQueryWrapper<OutboundDocDO> wrapper = new LambdaQueryWrapper<>();
        if (allowed != null) {
            wrapper.in(OutboundDocDO::getWarehouseId, allowed);
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(OutboundDocDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(OutboundDocDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(OutboundDocDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(OutboundDocDO::getDocDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(OutboundDocDO::getDocDate, to);
        }
        wrapper.orderByDesc(OutboundDocDO::getId);
        Page<OutboundDocDO> result = outboundDocMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<OutboundDocDO> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(),
                    query.getPage(), query.getPageSize());
        }
        return PageResult.of(toVOs(rows), result.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 出库单详情。
     *
     * @param id 单据 ID
     * @return 单据(含仓库与单据行)
     */
    @Override
    public OutboundDocVO get(long id) {
        OutboundDocDO doc = outboundDocMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("出库单不存在");
        }
        List<OutboundDocVO> vos = toVOs(List.of(doc));
        return vos.get(0);
    }

    /**
     * 批量组装单据 VO(仓库 + 单据行 + 关联销售订单/客户)。
     *
     * @param docs 单据列表
     * @return VO 列表
     */
    private List<OutboundDocVO> toVOs(List<OutboundDocDO> docs) {
        Set<Long> whIds = docs.stream().map(OutboundDocDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(OutboundDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> refOrderIds = docs.stream().map(OutboundDocDO::getRefDocId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));

        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        List<OutboundDocItemDO> allItems = outboundDocItemMapper.selectList(
                new LambdaQueryWrapper<OutboundDocItemDO>()
                        .in(OutboundDocItemDO::getDocId, docIds)
                        .orderByAsc(OutboundDocItemDO::getId));
        Map<Long, List<OutboundDocItemDO>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(OutboundDocItemDO::getDocId));
        Map<Long, SalesOrderDO> refOrderMap = (refOrderIds.isEmpty()
                ? List.<SalesOrderDO>of() : salesOrderMapper.selectByIds(refOrderIds))
                        .stream().collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        Set<Long> customerIds = refOrderMap.values().stream()
                .map(SalesOrderDO::getCustomerId).collect(Collectors.toCollection(HashSet::new));
        Map<Long, CustomerDO> customerMap = (customerIds.isEmpty() ? List.<CustomerDO>of()
                : customerMapper.selectByIds(customerIds)).stream()
                .collect(Collectors.toMap(CustomerDO::getId, c -> c));

        List<OutboundDocVO> vos = new ArrayList<>();
        for (OutboundDocDO doc : docs) {
            List<OutboundDocItemDO> items = itemMap.getOrDefault(doc.getId(), List.of());
            List<OutboundDocItemVO> itemVos = items.stream().map(this::toItemVO).toList();
            SalesOrderDO refOrder = doc.getRefDocId() == null ? null
                    : refOrderMap.get(doc.getRefDocId());
            String customerName = refOrder == null ? null
                    : customerMap.get(refOrder.getCustomerId()) == null ? null
                    : customerMap.get(refOrder.getCustomerId()).getCustomerName();
            vos.add(new OutboundDocVO(doc.getId(), doc.getDocNo(), doc.getWarehouseId(),
                    doc.getStatus(), doc.getRemark(), doc.getCreator(), doc.getCreatedAt(),
                    whMap.get(doc.getWarehouseId()), itemVos,
                    doc.getRefType(), doc.getRefDocId(),
                    refOrder == null ? null : refOrder.getDocNo(), customerName,
                    doc.getDocDate(), doc.getCarrier(), doc.getVehicleNo(),
                    doc.getFreight() == null ? null
                            : QtyUtils.toContractString(doc.getFreight()),
                    doc.getTotalAmount() == null ? null
                            : QtyUtils.toContractString(doc.getTotalAmount()),
                    doc.getDocType(), doc.getHandler()));
        }
        return vos;
    }

    /**
     * DTO 行转库存操作行。
     *
     * @param dto 入参
     * @return 操作行列表
     */
    private List<StockLine> toStockLines(OutboundCreateDTO dto) {
        return dto.items().stream().map(line -> {
            StockLine sl = new StockLine();
            sl.setItemId(line.itemId());
            sl.setQty(line.qty());
            sl.setBatchNo(line.batchNo());
            sl.setLocationId(line.locationId());
            sl.setSerialNos(line.serialNos());
            return sl;
        }).toList();
    }

    /**
     * 实体转单据行 VO。
     *
     * @param item 实体
     * @return VO
     */
    private OutboundDocItemVO toItemVO(OutboundDocItemDO item) {
        return new OutboundDocItemVO(item.getId(), item.getDocId(), item.getItemId(),
                QtyUtils.toContractString(item.getQuantity()), item.getBatchId(),
                item.getLocationId(), item.getSerialNos(), item.getUnitPrice(),
                item.getRefLineId(), item.getTaxRate(), item.getLineNo(), item.getAmount(),
                item.getTaxAmount(), item.getTaxInclusiveTotal());
    }

    /**
     * 实体转单据头 VO。
     *
     * @param doc 实体
     * @return VO
     */
    private OutboundDocCreatedVO toCreatedVO(OutboundDocDO doc) {
        return new OutboundDocCreatedVO(doc.getId(), doc.getDocNo(), doc.getWarehouseId(),
                doc.getStatus(), doc.getRemark(), doc.getCreator(), doc.getCreatedAt());
    }

    /**
     * V9 追溯链补写:出库过账后把本单行序列号挂上本单 doc_no(同一事务,与库存数量无关)。
     *
     * @param items       出库行列表
     * @param docNo       本单单据号
     * @param warehouseId 本仓 ID
     * @param ts          过账前基准时间(只补写本单出库产生的序列号)
     */
    private void backfillSerialRef(List<OutboundLineDTO> items, String docNo, Long warehouseId,
            LocalDateTime ts) {
        Set<String> serialNos = new HashSet<>();
        for (OutboundLineDTO line : items) {
            if (line.serialNos() != null) {
                serialNos.addAll(line.serialNos());
            }
        }
        if (serialNos.isEmpty()) {
            return;
        }
        serialMapper.update(null, new LambdaUpdateWrapper<SerialDO>()
                .eq(SerialDO::getWarehouseId, warehouseId)
                .in(SerialDO::getSerialNo, serialNos)
                .ge(SerialDO::getOutboundTime, ts)
                .set(SerialDO::getRefOutDocNo, docNo));
    }

    /**
     * 序列号列表转 JSON 数组字符串(空则 null)。
     *
     * @param serialNos 序列号列表(可空)
     * @return JSON 数组字符串或 null
     */
    private String toJsonArray(List<String> serialNos) {
        if (serialNos == null || serialNos.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(serialNos);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("序列号序列化失败: " + serialNos, e);
        }
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
