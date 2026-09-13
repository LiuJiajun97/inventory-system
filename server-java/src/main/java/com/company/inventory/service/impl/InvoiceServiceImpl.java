package com.company.inventory.service.impl;

import com.company.inventory.common.constant.ErrorCode;
import com.company.inventory.common.constant.SettlementConstants;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.InboundDocItemMapper;
import com.company.inventory.mapper.InboundDocMapper;
import com.company.inventory.mapper.InvoiceItemMapper;
import com.company.inventory.mapper.InvoiceMapper;
import com.company.inventory.mapper.ItemMapper;
import com.company.inventory.mapper.OutboundDocItemMapper;
import com.company.inventory.mapper.OutboundDocMapper;
import com.company.inventory.mapper.PurchaseOrderMapper;
import com.company.inventory.mapper.PurchaseReturnItemMapper;
import com.company.inventory.mapper.PurchaseReturnMapper;
import com.company.inventory.mapper.SalesOrderMapper;
import com.company.inventory.mapper.SalesReturnItemMapper;
import com.company.inventory.mapper.SalesReturnMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.dto.settlement.InvoiceCreateDTO;
import com.company.inventory.model.dto.settlement.InvoiceItemDTO;
import com.company.inventory.model.dto.settlement.InvoiceUpdateDTO;
import com.company.inventory.model.entity.inbound.InboundDocDO;
import com.company.inventory.model.entity.inbound.InboundDocItemDO;
import com.company.inventory.model.entity.item.ItemDO;
import com.company.inventory.model.entity.outbound.OutboundDocDO;
import com.company.inventory.model.entity.outbound.OutboundDocItemDO;
import com.company.inventory.model.entity.purchase.PurchaseOrderDO;
import com.company.inventory.model.entity.returns.PurchaseReturnDO;
import com.company.inventory.model.entity.returns.PurchaseReturnItemDO;
import com.company.inventory.model.entity.returns.SalesReturnDO;
import com.company.inventory.model.entity.returns.SalesReturnItemDO;
import com.company.inventory.model.entity.sales.SalesOrderDO;
import com.company.inventory.model.entity.settlement.InvoiceDO;
import com.company.inventory.model.entity.settlement.InvoiceItemDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.query.InvoiceQuery;
import com.company.inventory.model.vo.settlement.InvoiceItemVO;
import com.company.inventory.model.vo.settlement.InvoiceVO;
import com.company.inventory.model.vo.settlement.InvoiceableLineVO;
import com.company.inventory.model.vo.settlement.InvoicedSrcVO;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.InvoiceService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发票服务实现(V18 结算域):手工登记正票 + 退货生成红字凭单(负票)。
 *
 * <p>行级匹配:发票行挂源单据行,源行含税额服务端取值(不信前端);
 * |开票额-源行含税额|>0.01 落 mismatch(挂起),改平后 draft;防超开硬校验:
 * 同一源行已有未作废发票行则视为占用,开票额超源行含税额,均 400。</p>
 *
 * @author inventory
 */
@Service
public class InvoiceServiceImpl implements InvoiceService {

    /** 发票头 Mapper。 */
    private final InvoiceMapper invoiceMapper;
    /** 发票行 Mapper。 */
    private final InvoiceItemMapper invoiceItemMapper;
    /** 单据号服务。 */
    private final DocNoService docNoService;
    /** 入库单 Mapper(源行校验)。 */
    private final InboundDocMapper inboundDocMapper;
    /** 入库单行 Mapper(源行快照)。 */
    private final InboundDocItemMapper inboundDocItemMapper;
    /** 出库单 Mapper(源行校验)。 */
    private final OutboundDocMapper outboundDocMapper;
    /** 出库单行 Mapper(源行快照)。 */
    private final OutboundDocItemMapper outboundDocItemMapper;
    /** 采购订单 Mapper(对方一致校验)。 */
    private final PurchaseOrderMapper purchaseOrderMapper;
    /** 销售订单 Mapper(对方一致校验)。 */
    private final SalesOrderMapper salesOrderMapper;
    /** 供应商 Mapper。 */
    private final SupplierMapper supplierMapper;
    /** 客户 Mapper。 */
    private final CustomerMapper customerMapper;
    /** 物品 Mapper(行展示编码/名称)。 */
    private final ItemMapper itemMapper;
    /** 采购退货单 Mapper(负票来源)。 */
    private final PurchaseReturnMapper purchaseReturnMapper;
    /** 采购退货单行 Mapper。 */
    private final PurchaseReturnItemMapper purchaseReturnItemMapper;
    /** 销售退货单 Mapper(负票来源)。 */
    private final SalesReturnMapper salesReturnMapper;
    /** 销售退货单行 Mapper。 */
    private final SalesReturnItemMapper salesReturnItemMapper;

    /**
     * 构造服务。
     *
     * @param invoiceMapper            发票头 Mapper
     * @param invoiceItemMapper        发票行 Mapper
     * @param docNoService             单据号服务
     * @param inboundDocMapper         入库单 Mapper
     * @param inboundDocItemMapper     入库单行 Mapper
     * @param outboundDocMapper        出库单 Mapper
     * @param outboundDocItemMapper    出库单行 Mapper
     * @param purchaseOrderMapper      采购订单 Mapper
     * @param salesOrderMapper         销售订单 Mapper
     * @param supplierMapper           供应商 Mapper
     * @param customerMapper           客户 Mapper
     * @param itemMapper               物品 Mapper
     * @param purchaseReturnMapper     采购退货单 Mapper
     * @param purchaseReturnItemMapper 采购退货单行 Mapper
     * @param salesReturnMapper        销售退货单 Mapper
     * @param salesReturnItemMapper    销售退货单行 Mapper
     */
    public InvoiceServiceImpl(InvoiceMapper invoiceMapper, InvoiceItemMapper invoiceItemMapper,
            DocNoService docNoService, InboundDocMapper inboundDocMapper,
            InboundDocItemMapper inboundDocItemMapper, OutboundDocMapper outboundDocMapper,
            OutboundDocItemMapper outboundDocItemMapper, PurchaseOrderMapper purchaseOrderMapper,
            SalesOrderMapper salesOrderMapper, SupplierMapper supplierMapper,
            CustomerMapper customerMapper, ItemMapper itemMapper,
            PurchaseReturnMapper purchaseReturnMapper,
            PurchaseReturnItemMapper purchaseReturnItemMapper, SalesReturnMapper salesReturnMapper,
            SalesReturnItemMapper salesReturnItemMapper) {
        this.invoiceMapper = invoiceMapper;
        this.invoiceItemMapper = invoiceItemMapper;
        this.docNoService = docNoService;
        this.inboundDocMapper = inboundDocMapper;
        this.inboundDocItemMapper = inboundDocItemMapper;
        this.outboundDocMapper = outboundDocMapper;
        this.outboundDocItemMapper = outboundDocItemMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.supplierMapper = supplierMapper;
        this.customerMapper = customerMapper;
        this.itemMapper = itemMapper;
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.purchaseReturnItemMapper = purchaseReturnItemMapper;
        this.salesReturnMapper = salesReturnMapper;
        this.salesReturnItemMapper = salesReturnItemMapper;
    }

    /** 源单据行解析结果(内部):源行快照 + 开票额 + 差异。 */
    private record ResolvedLine(String srcDocType, Long srcDocId, Long srcDocItemId, Long itemId,
            String specSnapshot, String unit, BigDecimal quantity, BigDecimal srcAmount,
            BigDecimal invoicedAmount, BigDecimal variance, String batchNo) {
    }

    /**
     * 手工新建发票(正票)。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 发票(含行)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceVO create(InvoiceCreateDTO dto, String username) {
        String type = normalizeType(dto.invoiceType());
        Long partyId = requireParty(type, dto.partyId());
        List<ResolvedLine> resolved = resolvePositiveLines(type, partyId, dto.items());
        checkOverInvoice(resolved);
        InvoiceDO head = new InvoiceDO();
        head.setDocNo(docNoService.generateInvoiceNo());
        head.setInvoiceType(type);
        head.setPartyId(partyId);
        head.setInvoiceDate(dto.invoiceDate());
        head.setTotalAmount(resolveTotal(resolved));
        head.setStatus(anyMismatch(resolved)
                ? SettlementConstants.INVOICE_STATUS_MISMATCH
                : SettlementConstants.INVOICE_STATUS_DRAFT);
        head.setSign(SettlementConstants.SIGN_POSITIVE);
        head.setSourceType(SettlementConstants.SOURCE_MANUAL);
        head.setRemark(dto.remark());
        head.setCreator(username);
        invoiceMapper.insert(head);
        insertItems(head, resolved, SettlementConstants.SIGN_POSITIVE);
        return get(head.getId());
    }

    /**
     * 修改发票(仅 draft/mismatch 可改)。
     *
     * @param id       发票 ID
     * @param dto      入参(全量行,覆盖式)
     * @param username 当前登录用户名
     * @return 发票(含行)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceVO update(long id, InvoiceUpdateDTO dto, String username) {
        InvoiceDO head = getHead(id);
        if (SettlementConstants.INVOICE_STATUS_CONFIRMED.equals(head.getStatus())
                || SettlementConstants.INVOICE_STATUS_VOIDED.equals(head.getStatus())) {
            throw new BizException("已确认/已作废发票不可修改");
        }
        String type = head.getInvoiceType();
        Long partyId = dto.partyId() == null ? head.getPartyId() : requireParty(type, dto.partyId());
        LocalDate date = dto.invoiceDate() == null ? head.getInvoiceDate() : dto.invoiceDate();
        // 先读旧行(负票行解析用)再物理删旧行:防超开聚合随即不再含本票旧行
        List<InvoiceItemDO> oldItems = selectItems(id);
        invoiceItemMapper.delete(new LambdaQueryWrapper<InvoiceItemDO>()
                .eq(InvoiceItemDO::getInvoiceId, id));
        List<ResolvedLine> resolved;
        if (SettlementConstants.SIGN_NEGATIVE.equals(head.getSign())) {
            // 负票(退货生成):行只能改金额/删除,不能新增
            resolved = resolveNegativeUpdateLines(oldItems, dto.items());
        } else {
            resolved = resolvePositiveLines(type, partyId, dto.items());
            checkOverInvoice(resolved);
        }
        head.setPartyId(partyId);
        head.setInvoiceDate(date);
        head.setRemark(dto.remark());
        head.setTotalAmount(resolveTotal(resolved));
        head.setStatus(anyMismatch(resolved)
                ? SettlementConstants.INVOICE_STATUS_MISMATCH
                : SettlementConstants.INVOICE_STATUS_DRAFT);
        invoiceMapper.updateById(head);
        insertItems(head, resolved, head.getSign());
        return get(id);
    }

    /**
     * 确认发票(重算差异,仍有差异行 400)。
     *
     * @param id       发票 ID
     * @param username 当前登录用户名
     * @return 发票
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceVO confirm(long id, String username) {
        InvoiceDO head = getHead(id);
        if (SettlementConstants.INVOICE_STATUS_VOIDED.equals(head.getStatus())) {
            throw new BizException("已作废发票不可确认");
        }
        if (SettlementConstants.INVOICE_STATUS_CONFIRMED.equals(head.getStatus())) {
            throw new BizException("发票已确认,请勿重复操作");
        }
        for (InvoiceItemDO item : selectItems(id)) {
            BigDecimal variance = item.getVariance() == null ? BigDecimal.ZERO : item.getVariance();
            if (variance.abs().compareTo(SettlementConstants.VARIANCE_TOLERANCE) > 0) {
                throw new BizException("存在差异行,请修改或作废(行 " + item.getLineNo()
                        + " 开票额 " + trimAmount(item.getInvoicedAmount())
                        + ",源行含税额 " + trimAmount(item.getSrcAmount()) + ")");
            }
        }
        head.setStatus(SettlementConstants.INVOICE_STATUS_CONFIRMED);
        invoiceMapper.updateById(head);
        return get(id);
    }

    /**
     * 作废发票(留痕:头表保留状态/金额/审计,物理删行释放源行占用,可再开票)。
     *
     * @param id       发票 ID
     * @param username 当前登录用户名
     * @return 发票
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvoiceVO voidInvoice(long id, String username) {
        InvoiceDO head = getHead(id);
        if (SettlementConstants.INVOICE_STATUS_VOIDED.equals(head.getStatus())) {
            throw new BizException("发票已作废,请勿重复操作");
        }
        // 物理删行:释放源行唯一约束占用(作废后同一源行可重新开票);头表留痕
        invoiceItemMapper.delete(new LambdaQueryWrapper<InvoiceItemDO>()
                .eq(InvoiceItemDO::getInvoiceId, id));
        head.setStatus(SettlementConstants.INVOICE_STATUS_VOIDED);
        invoiceMapper.updateById(head);
        return get(id);
    }

    /**
     * 发票分页列表(数据权限口径与采购订单列表一致:无仓库维度过滤)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<InvoiceVO> list(InvoiceQuery query) {
        LambdaQueryWrapper<InvoiceDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getInvoiceType())) {
            wrapper.eq(InvoiceDO::getInvoiceType, query.getInvoiceType().trim());
        }
        if (query.getPartyId() != null) {
            wrapper.eq(InvoiceDO::getPartyId, query.getPartyId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(InvoiceDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(InvoiceDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(InvoiceDO::getInvoiceDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(InvoiceDO::getInvoiceDate, to);
        }
        wrapper.orderByDesc(InvoiceDO::getId);
        Page<InvoiceDO> page = invoiceMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 发票详情(带行与源单据号)。
     *
     * @param id 发票 ID
     * @return 发票
     */
    @Override
    public InvoiceVO get(long id) {
        return toVOs(List.of(getHead(id))).get(0);
    }

    /**
     * 可挂票源单据行(未开票/部分开票)。
     *
     * @param invoiceType 发票类型
     * @param partyId     对方 ID
     * @return 可挂票行列表
     */
    @Override
    public List<InvoiceableLineVO> invoiceableLines(String invoiceType, long partyId) {
        String type = normalizeType(invoiceType);
        requireParty(type, partyId);
        return invoiceMapper.selectInvoiceableLines(type, partyId);
    }

    /**
     * 采购退货过账后同事务生成负数草稿发票(红字凭单)。
     *
     * @param doc      采购退货单(已过账)
     * @param username 当前登录用户名
     * @return 生成的发票 ID
     */
    @Override
    public long generateForPurchaseReturn(PurchaseReturnDO doc, String username) {
        PurchaseOrderDO order = purchaseOrderMapper.selectById(doc.getPurchaseOrderId());
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        List<InvoiceItemDO> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 0;
        for (PurchaseReturnItemDO line : purchaseReturnItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnItemDO>()
                        .eq(PurchaseReturnItemDO::getDocId, doc.getId())
                        .orderByAsc(PurchaseReturnItemDO::getId))) {
            BigDecimal amount = line.getTaxInclusiveTotal() == null
                    ? BigDecimal.ZERO : line.getTaxInclusiveTotal();
            items.add(negativeItem(++lineNo, SettlementConstants.SRC_PURCHASE_RETURN,
                    doc.getId(), line.getId(), line.getItemId(), line.getSpecSnapshot(),
                    line.getUnit(), line.getQuantity(), amount.negate()));
            total = total.add(amount.negate());
        }
        return insertNegativeInvoice(SettlementConstants.INVOICE_TYPE_PURCHASE,
                order.getSupplierId(), doc.getDocDate(), doc.getId(), total, items, username);
    }

    /**
     * 销售退货过账后同事务生成负数草稿发票(贷项凭单)。
     *
     * @param doc      销售退货单(已过账)
     * @param username 当前登录用户名
     * @return 生成的发票 ID
     */
    @Override
    public long generateForSalesReturn(SalesReturnDO doc, String username) {
        SalesOrderDO order = salesOrderMapper.selectById(doc.getSalesOrderId());
        if (order == null) {
            throw BizException.notFound("销售订单不存在");
        }
        List<InvoiceItemDO> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 0;
        for (SalesReturnItemDO line : salesReturnItemMapper.selectList(
                new LambdaQueryWrapper<SalesReturnItemDO>()
                        .eq(SalesReturnItemDO::getDocId, doc.getId())
                        .orderByAsc(SalesReturnItemDO::getId))) {
            BigDecimal amount = line.getTaxInclusiveTotal() == null
                    ? BigDecimal.ZERO : line.getTaxInclusiveTotal();
            items.add(negativeItem(++lineNo, SettlementConstants.SRC_SALES_RETURN,
                    doc.getId(), line.getId(), line.getItemId(), line.getSpecSnapshot(),
                    line.getUnit(), line.getQuantity(), amount.negate()));
            total = total.add(amount.negate());
        }
        return insertNegativeInvoice(SettlementConstants.INVOICE_TYPE_SALES,
                order.getCustomerId(), doc.getDocDate(), doc.getId(), total, items, username);
    }

    // ==================== 私有辅助 ====================

    /**
     * 归一化发票类型(仅允许 purchase | sales)。
     *
     * @param raw 原始值
     * @return 归一化类型
     */
    private String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim();
        if (SettlementConstants.INVOICE_TYPE_PURCHASE.equals(type)
                || SettlementConstants.INVOICE_TYPE_SALES.equals(type)) {
            return type;
        }
        throw new BizException("发票类型非法(仅 purchase | sales)");
    }

    /**
     * 校验对方存在(purchase→供应商,sales→客户)。
     *
     * @param type    发票类型
     * @param partyId 对方 ID
     * @return 对方 ID
     */
    private Long requireParty(String type, Long partyId) {
        if (partyId == null) {
            throw new BizException("请选择对方(供应商/客户)");
        }
        if (SettlementConstants.INVOICE_TYPE_PURCHASE.equals(type)) {
            if (supplierMapper.selectById(partyId) == null) {
                throw new BizException("供应商不存在");
            }
        } else if (customerMapper.selectById(partyId) == null) {
            throw new BizException("客户不存在");
        }
        return partyId;
    }

    /**
     * 解析正票行:服务端逐行取源单据行含税额(不信前端),校验源行合法(仅采购关联入库行/
     * 销售关联出库行,且单据对方与本票一致)。
     *
     * @param type    发票类型
     * @param partyId 对方 ID
     * @param items   入参行
     * @return 解析结果列表
     */
    private List<ResolvedLine> resolvePositiveLines(String type, Long partyId,
            List<InvoiceItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException("发票至少需要一行");
        }
        String expectSrc = SettlementConstants.INVOICE_TYPE_PURCHASE.equals(type)
                ? SettlementConstants.SRC_INBOUND : SettlementConstants.SRC_OUTBOUND;
        Set<String> keys = new HashSet<>();
        List<ResolvedLine> resolved = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            InvoiceItemDTO dto = items.get(i);
            String srcType = dto.srcDocType() == null ? "" : dto.srcDocType().trim();
            if (!expectSrc.equals(srcType)) {
                throw new BizException("第 " + (i + 1) + " 行:手工发票只能挂"
                        + (SettlementConstants.INVOICE_TYPE_PURCHASE.equals(type)
                                ? "采购关联入库行" : "销售关联出库行"));
            }
            if (dto.invoicedAmount() == null
                    || dto.invoicedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("第 " + (i + 1) + " 行:开票额必须为正数");
            }
            if (!keys.add(expectSrc + ":" + dto.srcDocId() + ":" + dto.srcDocItemId())) {
                throw new BizException("第 " + (i + 1) + " 行:同一源单据行不能重复挂票");
            }
            resolved.add(resolvePositiveLine(i, type, partyId, expectSrc, dto));
        }
        return resolved;
    }

    /**
     * 解析单条正票源行(取源单据行含税额/数量/物品快照,校验单据对方一致)。
     *
     * @param index   行序号(从 0 起,错误提示用)
     * @param type    发票类型
     * @param partyId 对方 ID
     * @param srcType 源单据类型
     * @param dto     入参行
     * @return 解析结果
     */
    private ResolvedLine resolvePositiveLine(int index, String type, Long partyId,
            String srcType, InvoiceItemDTO dto) {
        String prefix = "第 " + (index + 1) + " 行:";
        BigDecimal srcAmount;
        BigDecimal quantity;
        String batchNo;
        Long itemId;
        if (SettlementConstants.SRC_INBOUND.equals(srcType)) {
            InboundDocDO doc = inboundDocMapper.selectById(dto.srcDocId());
            if (doc == null || !ErrorCode.REF_TYPE_PURCHASE.equals(doc.getRefType())
                    || doc.getRefDocId() == null) {
                throw new BizException(prefix + "源单据不是采购关联入库单(独立入库/期初/退货等不可挂票)");
            }
            PurchaseOrderDO order = purchaseOrderMapper.selectById(doc.getRefDocId());
            if (order == null || !Objects.equals(order.getSupplierId(), partyId)) {
                throw new BizException(prefix + "入库单不属于本供应商");
            }
            InboundDocItemDO line = inboundDocItemMapper.selectById(dto.srcDocItemId());
            if (line == null || !Objects.equals(line.getDocId(), doc.getId())) {
                throw new BizException(prefix + "源入库行不存在");
            }
            srcAmount = line.getTaxInclusiveTotal() == null ? BigDecimal.ZERO
                    : line.getTaxInclusiveTotal();
            quantity = line.getQuantity();
            batchNo = line.getBatchNo();
            itemId = line.getItemId();
        } else {
            OutboundDocDO doc = outboundDocMapper.selectById(dto.srcDocId());
            if (doc == null || !ErrorCode.REF_TYPE_SALES.equals(doc.getRefType())
                    || doc.getRefDocId() == null) {
                throw new BizException(prefix + "源单据不是销售关联出库单(独立出库/调拨/退货等不可挂票)");
            }
            SalesOrderDO order = salesOrderMapper.selectById(doc.getRefDocId());
            if (order == null || !Objects.equals(order.getCustomerId(), partyId)) {
                throw new BizException(prefix + "出库单不属于本客户");
            }
            OutboundDocItemDO line = outboundDocItemMapper.selectById(dto.srcDocItemId());
            if (line == null || !Objects.equals(line.getDocId(), doc.getId())) {
                throw new BizException(prefix + "源出库行不存在");
            }
            srcAmount = line.getTaxInclusiveTotal() == null ? BigDecimal.ZERO
                    : line.getTaxInclusiveTotal();
            quantity = line.getQuantity();
            batchNo = null;
            itemId = line.getItemId();
        }
        ItemDO item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(prefix + "源行物品不存在");
        }
        BigDecimal variance = dto.invoicedAmount().subtract(srcAmount);
        return new ResolvedLine(srcType, dto.srcDocId(), dto.srcDocItemId(), itemId,
                item.getSpec(), item.getUnit(), quantity, srcAmount,
                dto.invoicedAmount(), variance, batchNo);
    }

    /**
     * 防超开硬校验:同一源行已有未作废发票行视为占用(唯一约束同向防重复);
     * 开票额超源行含税额(未作废累计 + 本次 ≤ 含税额)400。
     *
     * @param resolved 解析结果列表
     */
    private void checkOverInvoice(List<ResolvedLine> resolved) {
        Map<String, BigDecimal> invoicedMap = new HashMap<>();
        for (InvoicedSrcVO row : invoiceMapper.selectInvoicedSumBySrc()) {
            invoicedMap.put(row.srcDocType() + ":" + row.srcDocId() + ":" + row.srcDocItemId(),
                    row.invoiced());
        }
        for (ResolvedLine line : resolved) {
            BigDecimal existing = invoicedMap.getOrDefault(
                    line.srcDocType() + ":" + line.srcDocId() + ":" + line.srcDocItemId(),
                    BigDecimal.ZERO);
            if (existing.signum() != 0) {
                throw new BizException("源行 " + line.srcDocItemId()
                        + " 已有未作废发票(累计开票额 " + trimAmount(existing) + "),请修改该发票或先作废");
            }
            if (line.invoicedAmount().compareTo(line.srcAmount()) > 0) {
                throw new BizException("源行 " + line.srcDocItemId()
                        + " 开票额 " + trimAmount(line.invoicedAmount())
                        + " 超过源行含税额 " + trimAmount(line.srcAmount()));
            }
        }
    }

    /**
     * 解析负票(退货生成)更新行:行只能改金额/删除,不能新增;src 必须与原行一致。
     *
     * @param oldItems 原发票行(删行前读取)
     * @param items    入参行(全量,未带回的视为删除)
     * @return 解析结果列表
     */
    private List<ResolvedLine> resolveNegativeUpdateLines(List<InvoiceItemDO> oldItems,
            List<InvoiceItemDTO> items) {
        Map<Long, InvoiceItemDO> oldMap = new HashMap<>();
        for (InvoiceItemDO old : oldItems) {
            oldMap.put(old.getSrcDocItemId(), old);
        }
        Set<Long> used = new HashSet<>();
        List<ResolvedLine> resolved = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            InvoiceItemDTO dto = items.get(i);
            InvoiceItemDO old = oldMap.get(dto.srcDocItemId());
            if (old == null) {
                throw new BizException("第 " + (i + 1) + " 行:负票(退货生成)不能新增行,请作废后重新登记");
            }
            if (!Objects.equals(old.getSrcDocType(), dto.srcDocType())
                    || !Objects.equals(old.getSrcDocId(), dto.srcDocId())) {
                throw new BizException("第 " + (i + 1) + " 行:负票行的源单据不能变更");
            }
            if (!used.add(dto.srcDocItemId())) {
                throw new BizException("第 " + (i + 1) + " 行:同一源单据行不能重复挂票");
            }
            if (dto.invoicedAmount() == null
                    || dto.invoicedAmount().compareTo(BigDecimal.ZERO) >= 0) {
                throw new BizException("第 " + (i + 1) + " 行:负票开票额必须为负数");
            }
            BigDecimal srcAmount = old.getSrcAmount() == null ? BigDecimal.ZERO : old.getSrcAmount();
            resolved.add(new ResolvedLine(old.getSrcDocType(), old.getSrcDocId(),
                    old.getSrcDocItemId(), old.getItemId(), old.getSpecSnapshot(), old.getUnit(),
                    old.getQuantity(), srcAmount, dto.invoicedAmount(),
                    dto.invoicedAmount().subtract(srcAmount), old.getBatchNo()));
        }
        return resolved;
    }

    /**
     * 是否存在差异行(|差异| > 0.01)。
     *
     * @param resolved 解析结果列表
     * @return true 表示存在差异行
     */
    private boolean anyMismatch(List<ResolvedLine> resolved) {
        return resolved.stream().anyMatch(line ->
                line.variance().abs().compareTo(SettlementConstants.VARIANCE_TOLERANCE) > 0);
    }

    /**
     * 汇总行开票额为发票总额。
     *
     * @param resolved 解析结果列表
     * @return 总额
     */
    private BigDecimal resolveTotal(List<ResolvedLine> resolved) {
        BigDecimal total = BigDecimal.ZERO;
        for (ResolvedLine line : resolved) {
            total = total.add(line.invoicedAmount());
        }
        return total;
    }

    /**
     * 插入负票(退货生成红字凭单):draft 状态,金额取退货行原含税额取负。
     *
     * @param type     发票类型
     * @param partyId  对方 ID
     * @param date     发票日期
     * @param returnId 退货单 ID
     * @param total    总额(负)
     * @param items    发票行
     * @param username 当前登录用户名
     * @return 发票 ID
     */
    private long insertNegativeInvoice(String type, Long partyId, LocalDate date, long returnId,
            BigDecimal total, List<InvoiceItemDO> items, String username) {
        InvoiceDO head = new InvoiceDO();
        head.setDocNo(docNoService.generateInvoiceNo());
        head.setInvoiceType(type);
        head.setPartyId(partyId);
        head.setInvoiceDate(date);
        head.setTotalAmount(total);
        head.setStatus(SettlementConstants.INVOICE_STATUS_DRAFT);
        head.setSign(SettlementConstants.SIGN_NEGATIVE);
        head.setSourceType(SettlementConstants.SOURCE_RETURN_GEN);
        head.setRefReturnId(returnId);
        head.setRemark("退货自动生成红字凭单");
        head.setCreator(username);
        invoiceMapper.insert(head);
        for (InvoiceItemDO item : items) {
            item.setInvoiceId(head.getId());
            item.setSign(SettlementConstants.SIGN_NEGATIVE);
            invoiceItemMapper.insert(item);
        }
        return head.getId();
    }

    /**
     * 构建负票行实体(退货生成,差异恒 0,金额取退货行原含税额取负)。
     *
     * @param lineNo     行号
     * @param srcType    源单据类型
     * @param srcDocId   退货单 ID
     * @param srcItemId  退货单行 ID
     * @param itemId     物品 ID
     * @param spec       规格快照
     * @param unit       单位
     * @param quantity   数量
     * @param amount     开票额(负)
     * @return 发票行实体
     */
    @SuppressWarnings("checkstyle:ParameterNumber") // 行快照字段与发票行列一一对应,拆参会丢失可读性
    private InvoiceItemDO negativeItem(int lineNo, String srcType, long srcDocId,
            long srcItemId, long itemId, String spec, String unit, BigDecimal quantity,
            BigDecimal amount) {
        InvoiceItemDO item = new InvoiceItemDO();
        item.setLineNo(lineNo);
        item.setSrcDocType(srcType);
        item.setSrcDocId(srcDocId);
        item.setSrcDocItemId(srcItemId);
        item.setItemId(itemId);
        item.setSpecSnapshot(spec);
        item.setUnit(unit);
        item.setQuantity(quantity);
        item.setInvoicedAmount(amount);
        item.setSrcAmount(amount);
        item.setVariance(BigDecimal.ZERO);
        return item;
    }

    /**
     * 插入解析后的正票行。
     *
     * @param head 发票头(已 insert)
     * @param rows 解析结果列表
     * @param sign 正负号
     */
    private void insertItems(InvoiceDO head, List<ResolvedLine> rows, String sign) {
        int lineNo = 0;
        for (ResolvedLine row : rows) {
            InvoiceItemDO item = new InvoiceItemDO();
            item.setInvoiceId(head.getId());
            item.setLineNo(++lineNo);
            item.setSrcDocType(row.srcDocType());
            item.setSrcDocId(row.srcDocId());
            item.setSrcDocItemId(row.srcDocItemId());
            item.setItemId(row.itemId());
            item.setSpecSnapshot(row.specSnapshot());
            item.setUnit(row.unit());
            item.setQuantity(row.quantity());
            item.setInvoicedAmount(row.invoicedAmount());
            item.setSrcAmount(row.srcAmount());
            item.setVariance(row.variance());
            item.setSign(sign);
            item.setBatchNo(row.batchNo());
            invoiceItemMapper.insert(item);
        }
    }

    /**
     * 取发票头(不存在 404)。
     *
     * @param id 发票 ID
     * @return 发票头
     */
    private InvoiceDO getHead(long id) {
        InvoiceDO head = invoiceMapper.selectById(id);
        if (head == null) {
            throw BizException.notFound("发票不存在");
        }
        return head;
    }

    /**
     * 取发票行(按 ID 排序)。
     *
     * @param invoiceId 发票 ID
     * @return 行列表
     */
    private List<InvoiceItemDO> selectItems(long invoiceId) {
        return invoiceItemMapper.selectList(new LambdaQueryWrapper<InvoiceItemDO>()
                .eq(InvoiceItemDO::getInvoiceId, invoiceId)
                .orderByAsc(InvoiceItemDO::getId));
    }

    /**
     * 批量组装发票 VO(对方名称/源单据号/退货单号/物品编码名称)。
     *
     * @param heads 发票头列表
     * @return VO 列表
     */
    private List<InvoiceVO> toVOs(List<InvoiceDO> heads) {
        if (heads.isEmpty()) {
            return List.of();
        }
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> customerIds = new HashSet<>();
        for (InvoiceDO head : heads) {
            if (SettlementConstants.INVOICE_TYPE_PURCHASE.equals(head.getInvoiceType())) {
                supplierIds.add(head.getPartyId());
            } else {
                customerIds.add(head.getPartyId());
            }
        }
        // 空集合防护:selectByIds 空集会生成非法 SQL "IN ( )"
        Map<Long, String> supplierNames = (supplierIds.isEmpty()
                ? List.<SupplierDO>of() : supplierMapper.selectByIds(supplierIds)).stream()
                .collect(Collectors.toMap(SupplierDO::getId, SupplierDO::getSupplierName,
                        (a, b) -> a));
        Map<Long, String> customerNames = (customerIds.isEmpty()
                ? List.<CustomerDO>of() : customerMapper.selectByIds(customerIds)).stream()
                .collect(Collectors.toMap(CustomerDO::getId, CustomerDO::getCustomerName,
                        (a, b) -> a));

        Set<Long> invoiceIds = heads.stream().map(InvoiceDO::getId).collect(Collectors.toSet());
        List<InvoiceItemDO> allItems = invoiceItemMapper.selectList(
                new LambdaQueryWrapper<InvoiceItemDO>()
                        .in(InvoiceItemDO::getInvoiceId, invoiceIds)
                        .orderByAsc(InvoiceItemDO::getId));
        Map<Long, List<InvoiceItemDO>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(InvoiceItemDO::getInvoiceId));

        // 源单据号:按源单据类型分组批量取
        Map<String, Set<Long>> srcIdsByType = new HashMap<>();
        Set<Long> itemIdSet = new HashSet<>();
        for (InvoiceItemDO item : allItems) {
            srcIdsByType.computeIfAbsent(item.getSrcDocType(), k -> new HashSet<>())
                    .add(item.getSrcDocId());
            itemIdSet.add(item.getItemId());
        }
        Map<String, Map<Long, String>> srcNoMap = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : srcIdsByType.entrySet()) {
            srcNoMap.put(entry.getKey(), loadSrcDocNos(entry.getKey(), entry.getValue()));
        }
        Map<Long, ItemDO> itemById = (itemIdSet.isEmpty()
                ? List.<ItemDO>of() : itemMapper.selectByIds(itemIdSet)).stream()
                .collect(Collectors.toMap(ItemDO::getId, d -> d));

        List<InvoiceVO> vos = new ArrayList<>();
        for (InvoiceDO head : heads) {
            String partyName = SettlementConstants.INVOICE_TYPE_PURCHASE.equals(head.getInvoiceType())
                    ? supplierNames.get(head.getPartyId()) : customerNames.get(head.getPartyId());
            String returnNo = head.getRefReturnId() == null
                    ? null : loadReturnNo(head.getRefReturnId(), head.getInvoiceType());
            List<InvoiceItemVO> itemVos = itemMap.getOrDefault(head.getId(), List.of()).stream()
                    .map(item -> toItemVO(item, srcNoMap, itemById))
                    .toList();
            vos.add(new InvoiceVO(head.getId(), head.getDocNo(), head.getInvoiceType(),
                    head.getPartyId(), partyName, head.getInvoiceDate(), head.getTotalAmount(),
                    head.getStatus(), head.getSign(), head.getSourceType(),
                    head.getRefReturnId(), returnNo, head.getRemark(), head.getCreator(),
                    head.getCreatedAt(), head.getUpdater(), head.getUpdatedAt(), itemVos));
        }
        return vos;
    }

    /**
     * 批量取源单据号(按源单据类型分派:入库/出库/采购退货/销售退货)。
     *
     * @param srcType 源单据类型
     * @param ids     源单据 ID 集合(非空)
     * @return ID → 单号映射
     */
    private Map<Long, String> loadSrcDocNos(String srcType, Set<Long> ids) {
        if (SettlementConstants.SRC_INBOUND.equals(srcType)) {
            return inboundDocMapper.selectByIds(ids).stream()
                    .collect(Collectors.toMap(InboundDocDO::getId, InboundDocDO::getDocNo,
                            (a, b) -> a));
        }
        if (SettlementConstants.SRC_OUTBOUND.equals(srcType)) {
            return outboundDocMapper.selectByIds(ids).stream()
                    .collect(Collectors.toMap(OutboundDocDO::getId, OutboundDocDO::getDocNo,
                            (a, b) -> a));
        }
        if (SettlementConstants.SRC_PURCHASE_RETURN.equals(srcType)) {
            return purchaseReturnMapper.selectByIds(ids).stream()
                    .collect(Collectors.toMap(PurchaseReturnDO::getId,
                            PurchaseReturnDO::getDocNo, (a, b) -> a));
        }
        return salesReturnMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(SalesReturnDO::getId, SalesReturnDO::getDocNo,
                        (a, b) -> a));
    }

    /**
     * 取退货单号(采购票→采购退货单,销售票→销售退货单)。
     *
     * @param returnId    退货单 ID
     * @param invoiceType 发票类型
     * @return 退货单号(不存在返回 null)
     */
    private String loadReturnNo(long returnId, String invoiceType) {
        if (SettlementConstants.INVOICE_TYPE_PURCHASE.equals(invoiceType)) {
            PurchaseReturnDO doc = purchaseReturnMapper.selectById(returnId);
            return doc == null ? null : doc.getDocNo();
        }
        SalesReturnDO doc = salesReturnMapper.selectById(returnId);
        return doc == null ? null : doc.getDocNo();
    }

    /**
     * 发票行转 VO(补源单据号与物品编码名称)。
     *
     * @param item     发票行
     * @param srcNoMap 源单据号映射(按源单据类型)
     * @param itemById 物品映射
     * @return 行 VO
     */
    private InvoiceItemVO toItemVO(InvoiceItemDO item, Map<String, Map<Long, String>> srcNoMap,
            Map<Long, ItemDO> itemById) {
        Map<Long, String> byType = srcNoMap.get(item.getSrcDocType());
        String srcNo = byType == null ? null : byType.get(item.getSrcDocId());
        ItemDO itemDO = itemById.get(item.getItemId());
        return new InvoiceItemVO(item.getId(), item.getInvoiceId(), item.getLineNo(),
                item.getSrcDocType(), srcNo, item.getSrcDocId(), item.getSrcDocItemId(),
                item.getItemId(), itemDO == null ? null : itemDO.getItemCode(),
                itemDO == null ? null : itemDO.getItemName(), item.getSpecSnapshot(),
                item.getUnit(), item.getQuantity(), item.getInvoicedAmount(),
                item.getSrcAmount(), item.getVariance(), item.getSign(), item.getBatchNo());
    }

    /**
     * 金额展示文本:去尾部零(避免 90.4 展示 90.4000)。
     *
     * @param amount 金额
     * @return 展示文本
     */
    private String trimAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return amount.stripTrailingZeros().toPlainString();
    }
}
