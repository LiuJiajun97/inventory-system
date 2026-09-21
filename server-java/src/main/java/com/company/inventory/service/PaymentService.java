package com.company.inventory.service;

import com.company.inventory.common.page.PageResult;
import com.company.inventory.model.dto.settlement.PaymentCreateDTO;
import com.company.inventory.model.query.PaymentQuery;
import com.company.inventory.model.vo.settlement.PaymentVO;
import com.company.inventory.model.vo.settlement.UnsettledInvoiceVO;

import java.util.List;

/**
 * 付款/收款单服务(V18 结算域):一表一 type 区分(payment 付款/receipt 收款),
 * create 即生效,核销行挂 confirmed 正票,支持一票多笔部分核销。
 *
 * @author inventory
 */
public interface PaymentService {

    /**
     * 新建付款/收款单(方向校验 + 防超核硬校验,总额=核销行合计)。
     *
     * @param dto      入参(头 + 核销行)
     * @param username 当前登录用户名
     * @return 单据(含核销行)
     */
    PaymentVO create(PaymentCreateDTO dto, String username);

    /**
     * 作废付款/收款单(释放核销额度)。
     *
     * @param id       单据 ID
     * @param username 当前登录用户名
     * @return 单据
     */
    PaymentVO voidPayment(long id, String username);

    /**
     * 付款/收款单分页列表(数据权限口径与采购订单列表一致)。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PaymentVO> list(PaymentQuery query);

    /**
     * 付款/收款单详情(带核销行)。
     *
     * @param id 单据 ID
     * @return 单据
     */
    PaymentVO get(long id);

    /**
     * 查某对方 confirmed 正票未核销额(新建核销行选择区用)。
     *
     * @param payType 单据类型(payment | receipt)
     * @param partyId 对方 ID
     * @return 未核销票列表
     */
    List<UnsettledInvoiceVO> unsettledInvoices(String payType, long partyId);
}
