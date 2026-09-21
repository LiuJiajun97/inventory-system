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
import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.entity.returns.PurchaseReturnDO;
import com.company.inventory.model.entity.returns.SalesReturnDO;
import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.entity.stock.BatchDO;
import com.company.inventory.model.entity.stock.SerialDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.BatchMapper;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.OutboundDocItemMapper;
import com.company.inventory.mapper.OutboundDocMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.PurchaseReturnMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.SalesReturnMapper;
import com.company.inventory.mapper.SerialMapper;
import com.company.inventory.mapper.SupplierMapper;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    /** 销售退货单 Mapper(列表展示 refType=sales_return 关联单号)。 */
    private final SalesReturnMapper salesReturnMapper;
    /** 采购退货单 Mapper(列表展示 refType=purchase_return 关联单号)。 */
    private final PurchaseReturnMapper purchaseReturnMapper;
    /** 采购订单 Mapper(采购退货对端供应商名溯源)。 */
    private final PurchaseOrderMapper purchaseOrderMapper;
    /** 供应商 Mapper(列表展示采购退货对端供应商名)。 */
    private final SupplierMapper supplierMapper;
    /** 序列号 Mapper(V9 追溯链补写)。 */
    private final SerialMapper serialMapper;
    /** 批次 Mapper(列表/详情行展示批次号,出库行表只存 batch_id 快照)。 */
    private final BatchMapper batchMapper;
    /** JSON 序列化(serialNos 列存 JSON 数组字符串)。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造服务。
     *
     * @param outboundDocMapper      单据 Mapper
     * @param outboundDocItemMapper  单据行 Mapper
     * @param warehouseMapper        仓库 Mapper
     * @param stockCoreService       库存核心服务
     * @param docNoService           单据号服务
     * @param salesOrderService      销售订单服务
     * @param salesOrderMapper       销售订单 Mapper
     * @param customerMapper         客户 Mapper
     * @param salesReturnMapper      销售退货单 Mapper
     * @param purchaseReturnMapper   采购退货单 Mapper
     * @param purchaseOrderMapper    采购订单 Mapper
     * @param supplierMapper         供应商 Mapper
     * @param serialMapper           序列号 Mapper
     * @param batchMapper            批次 Mapper(行展示批次号)
     * @param objectMapper           JSON 序列化器
     */
    public OutboundServiceImpl(OutboundDocMapper outboundDocMapper,
                               OutboundDocItemMapper outboundDocItemMapper,
                               WarehouseMapper warehouseMapper,
                               StockCoreService stockCoreService,
                               DocNoService docNoService,
                               SalesOrderService salesOrderService,
                               SalesOrderMapper salesOrderMapper,
                               CustomerMapper customerMapper,
                               SalesReturnMapper salesReturnMapper,
                               PurchaseReturnMapper purchaseReturnMapper,
                               PurchaseOrderMapper purchaseOrderMapper,
                               SupplierMapper supplierMapper,
                               SerialMapper serialMapper,
                               BatchMapper batchMapper,
                               ObjectMapper objectMapper) {
        this.outboundDocMapper = outboundDocMapper;
        this.outboundDocItemMapper = outboundDocItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.stockCoreService = stockCoreService;
        this.docNoService = docNoService;
        this.salesOrderService = salesOrderService;
        this.salesOrderMapper = salesOrderMapper;
        this.customerMapper = customerMapper;
        this.salesReturnMapper = salesReturnMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.supplierMapper = supplierMapper;
        this.serialMapper = serialMapper;
        this.batchMapper = batchMapper;
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
                // V20 含税单价:有源继承源订单行快照
                docItem.setTaxPrice(ref.taxPrice());
                shipLines.add(new ShipLine(line.refLineId(), line.qty()));
            } else {
                // V20 无源:不含税单价/含税单价二选一(与订单同口径),缺的互算
                BigDecimal up = line.unitPrice();
                BigDecimal tp = line.taxPrice();
                BigDecimal rt = line.taxRate() == null ? BigDecimal.ZERO : line.taxRate();
                if (up == null && tp != null) {
                    // 只填含税单价:按行数量反算不含税单价
                    up = MoneyUtils.fromInclusiveUnit(line.qty(), tp, rt).unitPrice();
                }
                docItem.setUnitPrice(up);
                docItem.setTaxRate(rt);
                // 含税单价缺失时按 不含税×(1+税率/100) 补(存量回填同口径)
                if (tp == null && up != null) {
                    tp = up.multiply(BigDecimal.ONE.add(rt.divide(
                            new BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP)))
                            .setScale(4, java.math.RoundingMode.HALF_UP);
                }
                docItem.setTaxPrice(tp);
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
     * 批量组装单据 VO(仓库 + 单据行 + 按 refType 分表回填关联单号/对端名)。
     *
     * <p>各关联表自增 id 相互独立,refDocId 必须按 refType 查对应表,否则撞号时
     * 会回填成别的单据的单号与对端名。</p>
     *
     * @param docs 单据列表
     * @return VO 列表
     */
    private List<OutboundDocVO> toVOs(List<OutboundDocDO> docs) {
        Set<Long> whIds = docs.stream().map(OutboundDocDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(OutboundDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        // 关联单 id 按 refType 分组(不同表自增 id 独立,查错表会回填串号)
        Map<String, Set<Long>> refIdsByType = new HashMap<>();
        for (OutboundDocDO doc : docs) {
            if (doc.getRefType() != null && doc.getRefDocId() != null) {
                refIdsByType.computeIfAbsent(doc.getRefType(), t -> new HashSet<>())
                        .add(doc.getRefDocId());
            }
        }

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
        // 批次批查(出库行表只存 batch_id,批次号经批次表回填展示;空集合防护)
        Set<Long> batchIds = allItems.stream().map(OutboundDocItemDO::getBatchId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<Long, String> batchNoMap = (batchIds.isEmpty() ? List.<BatchDO>of()
                : batchMapper.selectByIds(batchIds)).stream()
                .collect(Collectors.toMap(BatchDO::getId, BatchDO::getBatchNo));

        // 退货单先查(取其原订单 id 与直挂订单合并成一次批查)
        Set<Long> srIds = refIdsByType.getOrDefault(ErrorCode.REF_TYPE_SALES_RETURN, Set.of());
        Map<Long, SalesReturnDO> salesReturnMap = toIdMap(srIds.isEmpty()
                ? List.of() : salesReturnMapper.selectByIds(srIds), SalesReturnDO::getId);
        Set<Long> prIds = refIdsByType.getOrDefault(ErrorCode.REF_TYPE_PURCHASE_RETURN, Set.of());
        Map<Long, PurchaseReturnDO> purchaseReturnMap = toIdMap(prIds.isEmpty()
                ? List.of() : purchaseReturnMapper.selectByIds(prIds), PurchaseReturnDO::getId);
        // 销售订单批查:直挂 refType=sales + 销售退货原销售订单(对端客户名溯源)
        Set<Long> salesOrderIds = new HashSet<>(
                refIdsByType.getOrDefault(ErrorCode.REF_TYPE_SALES, Set.of()));
        for (SalesReturnDO sr : salesReturnMap.values()) {
            if (sr.getSalesOrderId() != null) {
                salesOrderIds.add(sr.getSalesOrderId());
            }
        }
        Map<Long, SalesOrderDO> salesOrderMap = toIdMap(salesOrderIds.isEmpty()
                ? List.of() : salesOrderMapper.selectByIds(salesOrderIds), SalesOrderDO::getId);
        // 采购订单批查:采购退货原采购订单(对端供应商名溯源)
        Set<Long> purchaseOrderIds = new HashSet<>();
        for (PurchaseReturnDO pr : purchaseReturnMap.values()) {
            if (pr.getPurchaseOrderId() != null) {
                purchaseOrderIds.add(pr.getPurchaseOrderId());
            }
        }
        Map<Long, PurchaseOrderDO> purchaseOrderMap = toIdMap(purchaseOrderIds.isEmpty()
                ? List.of() : purchaseOrderMapper.selectByIds(purchaseOrderIds),
                PurchaseOrderDO::getId);
        // 客户/供应商名批查
        Set<Long> customerIds = salesOrderMap.values().stream()
                .map(SalesOrderDO::getCustomerId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, CustomerDO> customerMap = toIdMap(customerIds.isEmpty() ? List.of()
                : customerMapper.selectByIds(customerIds), CustomerDO::getId);
        Set<Long> supplierIds = purchaseOrderMap.values().stream()
                .map(PurchaseOrderDO::getSupplierId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, SupplierDO> supplierMap = toIdMap(supplierIds.isEmpty() ? List.of()
                : supplierMapper.selectByIds(supplierIds), SupplierDO::getId);

        List<OutboundDocVO> vos = new ArrayList<>();
        for (OutboundDocDO doc : docs) {
            List<OutboundDocItemDO> items = itemMap.getOrDefault(doc.getId(), List.of());
            List<OutboundDocItemVO> itemVos = items.stream()
                    .map(item -> toItemVO(item, batchNoMap)).toList();
            String refDocNo;
            String refPartner;
            Long refDocId = doc.getRefDocId();
            String refType = doc.getRefType();
            if (ErrorCode.REF_TYPE_SALES.equals(refType) && refDocId != null) {
                SalesOrderDO so = salesOrderMap.get(refDocId);
                refDocNo = so == null ? null : so.getDocNo();
                refPartner = so == null ? null : customerNameOf(customerMap, so.getCustomerId());
            } else if (ErrorCode.REF_TYPE_SALES_RETURN.equals(refType) && refDocId != null) {
                SalesReturnDO sr = salesReturnMap.get(refDocId);
                refDocNo = sr == null ? null : sr.getDocNo();
                SalesOrderDO so = sr == null || sr.getSalesOrderId() == null ? null
                        : salesOrderMap.get(sr.getSalesOrderId());
                refPartner = so == null ? null : customerNameOf(customerMap, so.getCustomerId());
            } else if (ErrorCode.REF_TYPE_PURCHASE_RETURN.equals(refType) && refDocId != null) {
                PurchaseReturnDO pr = purchaseReturnMap.get(refDocId);
                refDocNo = pr == null ? null : pr.getDocNo();
                PurchaseOrderDO po = pr == null || pr.getPurchaseOrderId() == null ? null
                        : purchaseOrderMap.get(pr.getPurchaseOrderId());
                refPartner = po == null ? null : supplierNameOf(supplierMap, po.getSupplierId());
            } else {
                refDocNo = null;
                refPartner = null;
            }
            vos.add(new OutboundDocVO(doc.getId(), doc.getDocNo(), doc.getWarehouseId(),
                    doc.getStatus(), doc.getRemark(), doc.getCreator(), doc.getCreatedAt(),
                    whMap.get(doc.getWarehouseId()), itemVos,
                    doc.getRefType(), doc.getRefDocId(), refDocNo, refPartner,
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
     * @param item       实体
     * @param batchNoMap 批次 id→批次号映射(行表只存 batch_id,批次号经此回填)
     * @return VO
     */
    private OutboundDocItemVO toItemVO(OutboundDocItemDO item, Map<Long, String> batchNoMap) {
        return new OutboundDocItemVO(item.getId(), item.getDocId(), item.getItemId(),
                QtyUtils.toContractString(item.getQuantity()), item.getBatchId(),
                batchNoMap.get(item.getBatchId()),
                item.getLocationId(), item.getSerialNos(), item.getUnitPrice(),
                item.getRefLineId(), item.getTaxRate(), item.getLineNo(), item.getAmount(),
                item.getTaxAmount(), item.getTaxInclusiveTotal(), item.getTaxPrice());
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

    /**
     * 实体列表转 id 索引 Map。
     *
     * @param <T>      实体类型
     * @param rows     实体列表
     * @param idGetter 取 id 函数
     * @return id 索引 Map
     */
    private <T> Map<Long, T> toIdMap(List<T> rows, Function<T, Long> idGetter) {
        return rows.stream().collect(Collectors.toMap(idGetter, r -> r));
    }

    /**
     * 供应商名安全取值。
     *
     * @param supplierMap 供应商索引
     * @param supplierId  供应商 ID(可空)
     * @return 供应商名或 null
     */
    private String supplierNameOf(Map<Long, SupplierDO> supplierMap, Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        SupplierDO s = supplierMap.get(supplierId);
        return s == null ? null : s.getSupplierName();
    }

    /**
     * 客户名安全取值。
     *
     * @param customerMap 客户索引
     * @param customerId  客户 ID(可空)
     * @return 客户名或 null
     */
    private String customerNameOf(Map<Long, CustomerDO> customerMap, Long customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerDO c = customerMap.get(customerId);
        return c == null ? null : c.getCustomerName();
    }
}
