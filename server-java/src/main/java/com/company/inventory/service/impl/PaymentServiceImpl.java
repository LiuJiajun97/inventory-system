package com.company.inventory.service.impl;

import com.company.inventory.common.constant.SettlementConstants;
import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.common.support.DateRangeSupport;
import com.company.inventory.mapper.CustomerMapper;
import com.company.inventory.mapper.InvoiceMapper;
import com.company.inventory.mapper.PaymentDocMapper;
import com.company.inventory.mapper.PaymentLineMapper;
import com.company.inventory.mapper.SupplierMapper;
import com.company.inventory.model.entity.customer.CustomerDO;
import com.company.inventory.model.dto.settlement.PaymentCreateDTO;
import com.company.inventory.model.dto.settlement.PaymentLineDTO;
import com.company.inventory.model.entity.settlement.InvoiceDO;
import com.company.inventory.model.entity.settlement.PaymentDocDO;
import com.company.inventory.model.entity.settlement.PaymentLineDO;
import com.company.inventory.model.entity.supplier.SupplierDO;
import com.company.inventory.model.query.PaymentQuery;
import com.company.inventory.model.vo.settlement.PaymentLineVO;
import com.company.inventory.model.vo.settlement.PaymentVO;
import com.company.inventory.model.vo.settlement.SettledInvoiceVO;
import com.company.inventory.model.vo.settlement.UnsettledInvoiceVO;
import com.company.inventory.service.DocNoService;
import com.company.inventory.service.PaymentService;

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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 付款/收款单服务实现(V18 结算域):一表一 type 区分,create 即生效。
 *
 * <p>方向校验:付款只能核采购票、收款只能核销售票;只能核 confirmed 正票(负票 400);
 * 防超核硬校验:累计已核销 + 本次 ≤ 票额。</p>
 *
 * @author inventory
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    /** 付款/收款单头 Mapper。 */
    private final PaymentDocMapper paymentDocMapper;
    /** 核销行 Mapper。 */
    private final PaymentLineMapper paymentLineMapper;
    /** 发票 Mapper(方向/状态/票额校验)。 */
    private final InvoiceMapper invoiceMapper;
    /** 供应商 Mapper。 */
    private final SupplierMapper supplierMapper;
    /** 客户 Mapper。 */
    private final CustomerMapper customerMapper;
    /** 单据号服务。 */
    private final DocNoService docNoService;

    /**
     * 构造服务。
     *
     * @param paymentDocMapper 付款/收款单头 Mapper
     * @param paymentLineMapper 核销行 Mapper
     * @param invoiceMapper    发票 Mapper
     * @param supplierMapper   供应商 Mapper
     * @param customerMapper   客户 Mapper
     * @param docNoService     单据号服务
     */
    public PaymentServiceImpl(PaymentDocMapper paymentDocMapper,
            PaymentLineMapper paymentLineMapper, InvoiceMapper invoiceMapper,
            SupplierMapper supplierMapper, CustomerMapper customerMapper,
            DocNoService docNoService) {
        this.paymentDocMapper = paymentDocMapper;
        this.paymentLineMapper = paymentLineMapper;
        this.invoiceMapper = invoiceMapper;
        this.supplierMapper = supplierMapper;
        this.customerMapper = customerMapper;
        this.docNoService = docNoService;
    }

    /**
     * 新建付款/收款单。
     *
     * @param dto      入参
     * @param username 当前登录用户名
     * @return 单据(含核销行)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO create(PaymentCreateDTO dto, String username) {
        String payType = normalizePayType(dto.payType());
        String invoiceType = expectedInvoiceType(payType);
        Long partyId = requireParty(payType, dto.partyId());
        List<PaymentLineDTO> lines = dto.lines();
        if (lines == null || lines.isEmpty()) {
            throw new BizException("付款/收款单至少需要一条核销行");
        }
        Set<Long> invoiceIds = new HashSet<>();
        for (PaymentLineDTO line : lines) {
            if (!invoiceIds.add(line.invoiceId())) {
                throw new BizException("同一发票在本单只能核销一行,请合并核销额");
            }
        }
        // 空集合防护 + 防超核硬校验
        Map<Long, BigDecimal> settledMap = new HashMap<>();
        if (!invoiceIds.isEmpty()) {
            for (SettledInvoiceVO row : paymentDocMapper
                    .selectSettledByInvoices(new java.util.ArrayList<>(invoiceIds))) {
                settledMap.put(row.invoiceId(), row.settledAmount());
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            PaymentLineDTO line = lines.get(i);
            if (line.amount() == null || line.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("第 " + (i + 1) + " 条:核销额必须为正数");
            }
            InvoiceDO invoice = invoiceMapper.selectById(line.invoiceId());
            if (invoice == null) {
                throw new BizException("第 " + (i + 1) + " 条:发票不存在");
            }
            if (!invoiceType.equals(invoice.getInvoiceType())) {
                throw new BizException("第 " + (i + 1) + " 条:"
                        + (SettlementConstants.PAY_TYPE_PAYMENT.equals(payType)
                                ? "付款只能核销采购发票" : "收款只能核销销售发票"));
            }
            if (!SettlementConstants.INVOICE_STATUS_CONFIRMED.equals(invoice.getStatus())) {
                throw new BizException("第 " + (i + 1) + " 条:只能核销已确认发票");
            }
            if (!SettlementConstants.SIGN_POSITIVE.equals(invoice.getSign())) {
                throw new BizException("第 " + (i + 1) + " 条:负票(红字凭单)不能核销,它直接冲减余额");
            }
            BigDecimal settled = settledMap.getOrDefault(line.invoiceId(), BigDecimal.ZERO);
            if (settled.add(line.amount()).compareTo(invoice.getTotalAmount()) > 0) {
                throw new BizException("第 " + (i + 1) + " 条:核销额 " + line.amount()
                        + " 超过发票剩余可核额(票额 " + invoice.getTotalAmount()
                        + ",已核销 " + settled + ")");
            }
        }
        String docNo = SettlementConstants.PAY_TYPE_PAYMENT.equals(payType)
                ? docNoService.generatePaymentDocNo() : docNoService.generateReceiptDocNo();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentLineDTO line : lines) {
            total = total.add(line.amount());
        }
        PaymentDocDO head = new PaymentDocDO();
        head.setDocNo(docNo);
        head.setPayType(payType);
        head.setPartyId(partyId);
        head.setPayDate(dto.payDate());
        head.setTotalAmount(total);
        head.setStatus(SettlementConstants.PAYMENT_STATUS_CONFIRMED);
        head.setRemark(dto.remark());
        head.setCreator(username);
        paymentDocMapper.insert(head);
        for (PaymentLineDTO line : lines) {
            PaymentLineDO row = new PaymentLineDO();
            row.setPaymentId(head.getId());
            row.setInvoiceId(line.invoiceId());
            row.setAmount(line.amount());
            paymentLineMapper.insert(row);
        }
        return get(head.getId());
    }

    /**
     * 作废付款/收款单(释放核销额度)。
     *
     * @param id       单据 ID
     * @param username 当前登录用户名
     * @return 单据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO voidPayment(long id, String username) {
        PaymentDocDO head = getHead(id);
        if (SettlementConstants.PAYMENT_STATUS_VOIDED.equals(head.getStatus())) {
            throw new BizException("单据已作废,请勿重复操作");
        }
        head.setStatus(SettlementConstants.PAYMENT_STATUS_VOIDED);
        paymentDocMapper.updateById(head);
        return get(id);
    }

    /**
     * 付款/收款单分页列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<PaymentVO> list(PaymentQuery query) {
        LambdaQueryWrapper<PaymentDocDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getPayType())) {
            wrapper.eq(PaymentDocDO::getPayType, query.getPayType().trim());
        }
        if (query.getPartyId() != null) {
            wrapper.eq(PaymentDocDO::getPartyId, query.getPartyId());
        }
        if (StringUtils.hasText(query.getDocNo())) {
            wrapper.like(PaymentDocDO::getDocNo, query.getDocNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PaymentDocDO::getStatus, query.getStatus().trim());
        }
        LocalDate from = DateRangeSupport.parseDate(query.getFrom(), "日期起");
        if (from != null) {
            wrapper.ge(PaymentDocDO::getPayDate, from);
        }
        LocalDate to = DateRangeSupport.parseDate(query.getTo(), "日期止");
        if (to != null) {
            wrapper.le(PaymentDocDO::getPayDate, to);
        }
        wrapper.orderByDesc(PaymentDocDO::getId);
        Page<PaymentDocDO> page = paymentDocMapper.selectPage(
                Page.of(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(toVOs(page.getRecords()), page.getTotal(),
                query.getPage(), query.getPageSize());
    }

    /**
     * 付款/收款单详情(带核销行)。
     *
     * @param id 单据 ID
     * @return 单据
     */
    @Override
    public PaymentVO get(long id) {
        return toVOs(List.of(getHead(id))).get(0);
    }

    /**
     * 某对方 confirmed 正票未核销额。
     *
     * @param payType 单据类型
     * @param partyId 对方 ID
     * @return 未核销票列表
     */
    @Override
    public List<UnsettledInvoiceVO> unsettledInvoices(String payType, long partyId) {
        String invoiceType = expectedInvoiceType(normalizePayType(payType));
        requireParty(normalizePayType(payType), partyId);
        return paymentDocMapper.selectUnsettledInvoices(invoiceType, partyId);
    }

    // ==================== 私有辅助 ====================

    /**
     * 归一化单据类型(仅允许 payment | receipt)。
     *
     * @param raw 原始值
     * @return 归一化类型
     */
    private String normalizePayType(String raw) {
        String payType = raw == null ? "" : raw.trim();
        if (SettlementConstants.PAY_TYPE_PAYMENT.equals(payType)
                || SettlementConstants.PAY_TYPE_RECEIPT.equals(payType)) {
            return payType;
        }
        throw new BizException("单据类型非法(仅 payment | receipt)");
    }

    /**
     * 付款/收款类型对应发票类型。
     *
     * @param payType 单据类型
     * @return 发票类型
     */
    private String expectedInvoiceType(String payType) {
        return SettlementConstants.PAY_TYPE_PAYMENT.equals(payType)
                ? SettlementConstants.INVOICE_TYPE_PURCHASE
                : SettlementConstants.INVOICE_TYPE_SALES;
    }

    /**
     * 校验对方存在(payment→供应商,receipt→客户)。
     *
     * @param payType 单据类型
     * @param partyId 对方 ID
     * @return 对方 ID
     */
    private Long requireParty(String payType, Long partyId) {
        if (partyId == null) {
            throw new BizException("请选择对方(供应商/客户)");
        }
        if (SettlementConstants.PAY_TYPE_PAYMENT.equals(payType)) {
            if (supplierMapper.selectById(partyId) == null) {
                throw new BizException("供应商不存在");
            }
        } else if (customerMapper.selectById(partyId) == null) {
            throw new BizException("客户不存在");
        }
        return partyId;
    }

    /**
     * 取单据头(不存在 404)。
     *
     * @param id 单据 ID
     * @return 单据头
     */
    private PaymentDocDO getHead(long id) {
        PaymentDocDO head = paymentDocMapper.selectById(id);
        if (head == null) {
            throw BizException.notFound("付款/收款单不存在");
        }
        return head;
    }

    /**
     * 批量组装 VO(对方名称 + 核销行发票号)。
     *
     * @param heads 单据头列表
     * @return VO 列表
     */
    private List<PaymentVO> toVOs(List<PaymentDocDO> heads) {
        if (heads.isEmpty()) {
            return List.of();
        }
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> customerIds = new HashSet<>();
        for (PaymentDocDO head : heads) {
            if (SettlementConstants.PAY_TYPE_PAYMENT.equals(head.getPayType())) {
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

        Set<Long> headIds = heads.stream().map(PaymentDocDO::getId).collect(Collectors.toSet());
        List<PaymentLineDO> allLines = paymentLineMapper.selectList(
                new LambdaQueryWrapper<PaymentLineDO>()
                        .in(PaymentLineDO::getPaymentId, headIds)
                        .orderByAsc(PaymentLineDO::getId));
        Map<Long, List<PaymentLineDO>> lineMap = allLines.stream()
                .collect(Collectors.groupingBy(PaymentLineDO::getPaymentId));
        Set<Long> invoiceIds = allLines.stream().map(PaymentLineDO::getInvoiceId)
                .collect(Collectors.toSet());
        Map<Long, String> invoiceNos = (invoiceIds.isEmpty()
                ? List.<InvoiceDO>of() : invoiceMapper.selectByIds(invoiceIds)).stream()
                .collect(Collectors.toMap(InvoiceDO::getId, InvoiceDO::getDocNo, (a, b) -> a));

        List<PaymentVO> vos = new ArrayList<>();
        for (PaymentDocDO head : heads) {
            String partyName = SettlementConstants.PAY_TYPE_PAYMENT.equals(head.getPayType())
                    ? supplierNames.get(head.getPartyId()) : customerNames.get(head.getPartyId());
            List<PaymentLineVO> lineVos = lineMap.getOrDefault(head.getId(), List.of()).stream()
                    .map(line -> new PaymentLineVO(line.getId(), line.getPaymentId(),
                            line.getInvoiceId(), invoiceNos.get(line.getInvoiceId()),
                            line.getAmount()))
                    .toList();
            vos.add(new PaymentVO(head.getId(), head.getDocNo(), head.getPayType(),
                    head.getPartyId(), partyName, head.getPayDate(), head.getTotalAmount(),
                    head.getStatus(), head.getRemark(), head.getCreator(),
                    head.getCreatedAt(), lineVos));
        }
        return vos;
    }
}
