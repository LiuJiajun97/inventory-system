package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.util.QtyUtils;
import com.company.inventory.model.dto.inbound.InboundCreateDTO;
import com.company.inventory.model.dto.inbound.InboundLineDTO;
import com.company.inventory.model.dto.purchase.ArrivalLine;
import com.company.inventory.model.dto.stock.StockOpRequest;
import com.company.inventory.model.entity.inbound.InboundDocDO;
import com.company.inventory.model.entity.inbound.InboundDocItemDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.entity.warehouse.WarehouseDO;
import com.company.inventory.mapper.InboundDocItemMapper;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.mapper.WarehouseMapper;
import com.company.inventory.model.query.InboundDocQuery;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.InboundService;
import com.company.inventory.service.PurchaseOrderService;
import com.company.inventory.service.StockCoreService;
import com.company.inventory.model.vo.inbound.InboundDocCreatedVO;
import com.company.inventory.model.vo.inbound.InboundDocItemVO;
import com.company.inventory.model.vo.inbound.InboundDocVO;
import com.company.inventory.model.vo.purchase.PurchaseOrderItemVO;
import com.company.inventory.model.vo.stock.StockLine;
import com.company.inventory.model.vo.stock.StockOpResult;
import com.company.inventory.model.vo.warehouse.WarehouseVO;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 入库单服务实现:整单一个事务,单据头 + 行 + 库存变动 + 序列号同事务。
 *
 * <p>一期扩展:refType=purchase 关联采购订单到货,到货行价税携带订单行单价(服务端取值,
 * 不信前端),入库成功后同事务回写订单 arrivedQty(超收拒绝并提示行号)。</p>
 *
 * @author inventory
 */
@Service
public class InboundServiceImpl implements InboundService {

    /** 日志。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(InboundServiceImpl.class);

    /** 单据 Mapper。 */
    private final InboundDocMapper inboundDocMapper;
    /** 单据行 Mapper。 */
    private final InboundDocItemMapper inboundDocItemMapper;
    /** 仓库 Mapper。 */
    private final WarehouseMapper warehouseMapper;
    /** 库存核心服务。 */
    private final StockCoreService stockCoreService;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 采购订单服务(到货回写)。 */
    private final PurchaseOrderService purchaseOrderService;
    /** 采购订单 Mapper(列表展示关联单号)。 */
    private final PurchaseOrderMapper purchaseOrderMapper;
    /** 供应商 Mapper(列表展示关联供应商)。 */
    private final SupplierMapper supplierMapper;
    /** JSON 序列化(serialNos 列存 JSON 数组字符串)。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造服务。
     *
     * @param inboundDocMapper       单据 Mapper
     * @param inboundDocItemMapper   单据行 Mapper
     * @param warehouseMapper        仓库 Mapper
     * @param stockCoreService       库存核心服务
     * @param docNoService           单据号服务
     * @param purchaseOrderService   采购订单服务
     * @param purchaseOrderMapper    采购订单 Mapper
     * @param supplierMapper         供应商 Mapper
     * @param objectMapper           JSON 序列化器
     */
    public InboundServiceImpl(InboundDocMapper inboundDocMapper,
                              InboundDocItemMapper inboundDocItemMapper,
                              WarehouseMapper warehouseMapper,
                              StockCoreService stockCoreService,
                              DocNoService docNoService,
                              PurchaseOrderService purchaseOrderService,
                              PurchaseOrderMapper purchaseOrderMapper,
                              SupplierMapper supplierMapper,
                              ObjectMapper objectMapper) {
        this.inboundDocMapper = inboundDocMapper;
        this.inboundDocItemMapper = inboundDocItemMapper;
        this.warehouseMapper = warehouseMapper;
        this.stockCoreService = stockCoreService;
        this.docNoService = docNoService;
        this.purchaseOrderService = purchaseOrderService;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.supplierMapper = supplierMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 新建入库单(手工入库或采购到货)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 单据头
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundDocCreatedVO create(InboundCreateDTO dto, String username) {
        String docNo = docNoService.generateInboundDocNo();

        boolean purchaseArrival = ErrorCode.REF_TYPE_PURCHASE.equals(dto.refType())
                && dto.refDocId() != null;
        // 采购到货:先校验订单已审批并取订单行(服务端单价/税率来源),失败即整单回滚
        Map<Long, PurchaseOrderItemVO> refItems =
                purchaseArrival ? purchaseOrderService.requireApprovedItems(dto.refDocId()) : Map.of();
        for (InboundLineDTO line : dto.items()) {
            if (purchaseArrival) {
                if (line.refLineId() == null) {
                    throw new BizException("采购到货行必须选择采购订单行");
                }
                PurchaseOrderItemVO ref = refItems.get(line.refLineId());
                if (ref == null || !ref.itemId().equals(line.itemId())) {
                    throw new BizException("采购订单行不存在或物品不匹配: " + line.refLineId());
                }
            }
        }

        InboundDocDO doc = new InboundDocDO();
        doc.setDocNo(docNo);
        doc.setWarehouseId(dto.warehouseId());
        doc.setStatus(ErrorCode.DOC_STATUS_FINISHED);
        doc.setRemark(dto.remark());
        doc.setCreator(username);
        doc.setRefType(dto.refType());
        doc.setRefDocId(dto.refDocId());
        doc.setDocDate(dto.docDate());
        inboundDocMapper.insert(doc);

        StockOpRequest request = new StockOpRequest();
        request.setWarehouseId(dto.warehouseId());
        request.setLines(toStockLines(dto));
        request.setDocNo(docNo);
        request.setOperator(username);
        StockOpResult stockResult = stockCoreService.inbound(request);

        List<StockOpResult.StockOpRow> resultRows = stockResult.getRows();
        List<ArrivalLine> arrivalLines = new ArrayList<>();
        for (int i = 0; i < dto.items().size(); i++) {
            InboundLineDTO line = dto.items().get(i);
            StockOpResult.StockOpRow resultRow = resultRows.get(i);
            InboundDocItemDO docItem = new InboundDocItemDO();
            docItem.setDocId(doc.getId());
            docItem.setItemId(line.itemId());
            docItem.setQuantity(line.qty());
            docItem.setBatchId(resultRow.batchId());
            docItem.setLocationId(resultRow.locationId());
            docItem.setSerialNos(toJsonArray(line.serialNos()));
            docItem.setBatchNo(line.batchNo());
            docItem.setProductionDate(line.productionDate());
            docItem.setExpiryDate(line.expiryDate());
            docItem.setRefLineId(line.refLineId());
            if (purchaseArrival) {
                // 价税携带订单行单价(服务端取值,不信前端传值)
                PurchaseOrderItemVO ref = refItems.get(line.refLineId());
                docItem.setUnitPrice(ref.unitPrice());
                docItem.setTaxRate(ref.taxRate());
                arrivalLines.add(new ArrivalLine(line.refLineId(), line.qty()));
            } else {
                docItem.setUnitPrice(line.unitPrice());
                docItem.setTaxRate(line.taxRate());
            }
            inboundDocItemMapper.insert(docItem);
        }
        // 采购与库存同事务:入库成功后回写订单到货量(超收拒绝整单回滚)
        if (purchaseArrival) {
            purchaseOrderService.applyArrival(dto.refDocId(), arrivalLines);
        }
        LOGGER.info("新建入库单: docNo={}, warehouseId={}, refType={}, 行数={}, operator={}",
                docNo, dto.warehouseId(), dto.refType(), dto.items().size(), username);
        return toCreatedVO(doc);
    }

    /**
     * 入库单分页列表。
     *
     * @param query 查询条件(warehouseId/page/pageSize)
     * @return 分页结果
     */
    @Override
    public PageResult<InboundDocVO> list(InboundDocQuery query) {
        LambdaQueryWrapper<InboundDocDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getWarehouseId() != null) {
            wrapper.eq(InboundDocDO::getWarehouseId, query.getWarehouseId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(InboundDocDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(InboundDocDO::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getFrom())) {
            wrapper.ge(InboundDocDO::getDocDate, query.getFrom().trim());
        }
        if (StringUtils.hasText(query.getTo())) {
            wrapper.le(InboundDocDO::getDocDate, query.getTo().trim());
        }
        wrapper.orderByDesc(InboundDocDO::getId);
        Page<InboundDocDO> result = inboundDocMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        List<InboundDocDO> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(),
                    query.getPage(), query.getPageSize());
        }
        return PageResult.of(toVOs(rows), result.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 入库单详情。
     *
     * @param id 单据 ID
     * @return 单据(含仓库与单据行)
     */
    @Override
    public InboundDocVO get(long id) {
        InboundDocDO doc = inboundDocMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("入库单不存在");
        }
        List<InboundDocVO> vos = toVOs(List.of(doc));
        return vos.get(0);
    }

    /**
     * 批量组装单据 VO(仓库 + 单据行 + 关联采购订单/供应商)。
     *
     * @param docs 单据列表
     * @return VO 列表
     */
    private List<InboundDocVO> toVOs(List<InboundDocDO> docs) {
        Set<Long> whIds = docs.stream().map(InboundDocDO::getWarehouseId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> docIds = docs.stream().map(InboundDocDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> refOrderIds = docs.stream().map(InboundDocDO::getRefDocId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));

        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, WarehouseVO> whMap = (whIds.isEmpty() ? List.<WarehouseDO>of()
                : warehouseMapper.selectByIds(whIds)).stream()
                .collect(Collectors.toMap(WarehouseDO::getId, this::toWarehouseVO));
        List<InboundDocItemDO> allItems = inboundDocItemMapper.selectList(
                new LambdaQueryWrapper<InboundDocItemDO>()
                        .in(InboundDocItemDO::getDocId, docIds)
                        .orderByAsc(InboundDocItemDO::getId));
        Map<Long, List<InboundDocItemDO>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(InboundDocItemDO::getDocId));
        Map<Long, PurchaseOrderDO> refOrderMap = (refOrderIds.isEmpty()
                ? List.<PurchaseOrderDO>of() : purchaseOrderMapper.selectByIds(refOrderIds))
                        .stream().collect(Collectors.toMap(PurchaseOrderDO::getId, o -> o));
        Set<Long> supplierIds = refOrderMap.values().stream()
                .map(PurchaseOrderDO::getSupplierId).collect(Collectors.toCollection(HashSet::new));
        Map<Long, SupplierDO> supplierMap = (supplierIds.isEmpty() ? List.<SupplierDO>of()
                : supplierMapper.selectByIds(supplierIds)).stream()
                .collect(Collectors.toMap(SupplierDO::getId, s -> s));

        List<InboundDocVO> vos = new ArrayList<>();
        for (InboundDocDO doc : docs) {
            List<InboundDocItemDO> items = itemMap.getOrDefault(doc.getId(), List.of());
            List<InboundDocItemVO> itemVos = items.stream().map(this::toItemVO).toList();
            PurchaseOrderDO refOrder = doc.getRefDocId() == null ? null
                    : refOrderMap.get(doc.getRefDocId());
            String supplierName = refOrder == null ? null
                    : supplierMap.get(refOrder.getSupplierId()) == null ? null
                    : supplierMap.get(refOrder.getSupplierId()).getSupplierName();
            vos.add(new InboundDocVO(doc.getId(), doc.getDocNo(), doc.getWarehouseId(),
                    doc.getStatus(), doc.getRemark(), doc.getCreator(), doc.getCreatedAt(),
                    whMap.get(doc.getWarehouseId()), itemVos,
                    doc.getRefType(), doc.getRefDocId(),
                    refOrder == null ? null : refOrder.getDocNo(), supplierName,
                    doc.getDocDate()));
        }
        return vos;
    }

    /**
     * DTO 行转库存操作行。
     *
     * @param dto 入参
     * @return 操作行列表
     */
    private List<StockLine> toStockLines(InboundCreateDTO dto) {
        return dto.items().stream().map(line -> {
            StockLine sl = new StockLine();
            sl.setItemId(line.itemId());
            sl.setQty(line.qty());
            sl.setBatchNo(line.batchNo());
            sl.setProductionDate(line.productionDate());
            sl.setExpiryDate(line.expiryDate());
            sl.setSupplier(line.supplier());
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
    private InboundDocItemVO toItemVO(InboundDocItemDO item) {
        return new InboundDocItemVO(item.getId(), item.getDocId(), item.getItemId(),
                QtyUtils.toContractString(item.getQuantity()), item.getBatchId(),
                item.getLocationId(), item.getSerialNos(),
                item.getUnitPrice(), item.getTaxRate(), item.getBatchNo(),
                item.getProductionDate(), item.getExpiryDate(), item.getRefLineId());
    }

    /**
     * 实体转单据头 VO。
     *
     * @param doc 实体
     * @return VO
     */
    private InboundDocCreatedVO toCreatedVO(InboundDocDO doc) {
        return new InboundDocCreatedVO(doc.getId(), doc.getDocNo(), doc.getWarehouseId(),
                doc.getStatus(), doc.getRemark(), doc.getCreator(), doc.getCreatedAt());
    }

    /**
     * 序列号列表转 JSON 数组字符串(与 Fastify 版 JSON.stringify 一致,空则 null)。
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
